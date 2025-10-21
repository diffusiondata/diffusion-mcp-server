/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.prompts.ContextGuides.TOPICS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_SELECTORS;
import static com.diffusion.mcp.tools.JsonSchemas.boolProperty;
import static com.diffusion.mcp.tools.JsonSchemas.intProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.argumentIsTrue;
import static com.diffusion.mcp.tools.ToolUtils.intArgument;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.Topics.FetchRequest;
import com.pushtechnology.diffusion.client.features.Topics.FetchResult.TopicResult;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.TopicSelector;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.diffusion.datatype.recordv2.RecordV2;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Fetch topics tool with pagination, depth control, and optional
 * values.
 *
 * @author DiffusionData Limited
 */
final class FetchTopicsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(FetchTopicsTool.class);

    static final String TOOL_NAME = "fetch_topics";

    private static final String TOOL_DESCRIPTION =
        "Fetches topics from the connected Diffusion server with options for pagination, optional values, and structure exploration. " +
            "Needs SELECT_TOPIC permission for the path prefix of the selector used. Only topics for which the caller has READ_TOPIC permission" +
            " will be returned. " +
            "See the " + TOPICS +
            " context for full information about topics and the " +
            TOPIC_SELECTORS + " context for how to specify topic selectors.";

    /*
     * Parameters.
     */
    private static final String TOPIC_SELECTOR = "topicSelector";
    private static final String NUMBER = "number";
    private static final String VALUES = "values";
    private static final String AFTER = "after";
    private static final String DEPTH = "depth";
    private static final String SIZES = "sizes";
    private static final String UNPUBLISHED_DELAYED_TOPICS = "unpublishedDelayedTopics";

    /**
     * The tool's input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_SELECTOR,
                stringProperty(
                    "The topic selector pattern to match topics " +
                    "(e.g., '?path/', '?sensors//'). " +
                    "Defaults to '?.*//' (all topics) if not specified."))
            .property(
                NUMBER,
                intProperty(
                    "Maximum number of topics to fetch in this request. " +
                    "Defaults to 100 with values or 5000 without values.",
                    1,
                    10000,
                    null))
            .property(
                VALUES,
                boolProperty(
                    "Whether to include topic values in the response. " +
                    "Defaults to false. Ignored if depth supplied."))
            .property(
                AFTER,
                stringProperty(
                    "Topic path to start after (for pagination). " +
                    "Used to continue from where the last fetch left off."))
            .property(
                DEPTH,
                intProperty(
                    "Use to discover topic tree structure. " +
                    "Specifying 1 will return a single topic from each branch that has at least 1 part in its path. " +
                    "If not specified, all topics satisfying the selector are returned."))
            .property(
                SIZES,
                boolProperty(
                    "Whether to include topic size information in the response. " +
                    "For time series topics, returns the size of the last event, number of events, and total size. " +
                    "For other topics, returns the size of the topic value. " +
                    "Defaults to false."))
            .property(
                UNPUBLISHED_DELAYED_TOPICS,
                boolProperty(
                    "Whether to include unpublished reference topics created by topic views with delay clauses. " +
                    "These topics are in an unpublished state until their delay time expires. " +
                    "Defaults to false."))
            .additionalProperties(false)
            .build();

    /**
     * The default selector fetches all topics.
     */
    private static final String DEFAULT_SELECTOR = "?.*//";

    /**
     * The default number of topics returned when values are requested.
     */
    private static final int DEFAULT_NUMBER_WITH_VALUES = 100;

    /**
     * The default number of topics returned when values are not requested.
     */
    private static final int DEFAULT_NUMBER_WITHOUT_VALUES = 5000;

    /**
     * The maximum size of a JSON value before truncation.
     */
    private static final int VALUE_TRUNCATE_LENGTH = 1000;

    private static final String VALUE_PREFIX = "   Value: ";


    private FetchTopicsTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // First check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final FetchParameters parameters;
                try {
                    parameters = extractParameters(request.arguments());
                }
                catch (Exception ex) {
                    return monoToolException("Invalid paramaters", ex, LOG);
                }


                final FetchRequest<Void> fetchRequest =
                    buildFetchRequest(session , parameters);

                LOG.info("Starting fetch: {}", parameters);

                // Handle the two different cases with proper typing
                if (parameters.includeValues) {
                    return executeWithValues(fetchRequest, parameters);
                }
                else {
                    return executeWithoutValues(fetchRequest, parameters);
                }
            })
            .build();
    }

    /**
     * Extract parameters from the supplied arguments, deriving defaults as
     * appropriate.
     */
    private static FetchParameters extractParameters(
        Map<String, Object> arguments) {

        final String topicSelector =
            stringArgument(arguments, TOPIC_SELECTOR, DEFAULT_SELECTOR);

        final TopicSelector selector =
            Diffusion.topicSelectors().parse(topicSelector);

        // Check if depth has been supplied
        final Integer depth = intArgument(arguments, DEPTH);

        // Check if values required - never if depth supplied
        final boolean includeValues =
            depth == null && argumentIsTrue(arguments, VALUES);

        final int defaultNumber =
            includeValues ? DEFAULT_NUMBER_WITH_VALUES :
                DEFAULT_NUMBER_WITHOUT_VALUES;

        final boolean includeSizes = argumentIsTrue(arguments, SIZES);

        final boolean includeUnpublishedDelayedTopics =
            argumentIsTrue(arguments, UNPUBLISHED_DELAYED_TOPICS);

        return new FetchParameters(
            selector,
            intArgument(arguments, NUMBER, defaultNumber),
            includeValues,
            stringArgument(arguments, AFTER),
            depth,
            includeSizes,
            includeUnpublishedDelayedTopics);
    }

    /**
     * Build base fetch request without values based on the parameters.
     */
    private static FetchRequest<Void> buildFetchRequest(
        Session session,
        FetchParameters params) {

        FetchRequest<Void> fetchRequest =
            session.feature(Topics.class).fetchRequest().first(params.number);

        if (params.after != null) {
            fetchRequest = fetchRequest.after(params.after);
        }

        if (params.depth != null) {
            fetchRequest =
                fetchRequest.limitDeepBranches(params.depth, 1);
        }

        if (params.includeSizes) {
            fetchRequest = fetchRequest.withSizes();
        }

        if (params.includeUnpublishedDelayedTopics) {
            fetchRequest = fetchRequest.withUnpublishedDelayedTopics();
        }

        return fetchRequest;
    }

    /**
     * Execute fetch request with values (returns FetchRequest<JSON>)
     */
    private static Mono<CallToolResult> executeWithValues(
        FetchRequest<Void> fetchRequest,
        FetchParameters params) {

        // Execute the fetch
        return Mono
            .fromFuture(
                fetchRequest
                    .withValues(Object.class)
                    .fetch(params.topicSelector))
            .timeout(TEN_SECONDS)
            .doOnNext(result -> LOG.debug(
                "Fetch with values completed successfully for selector: {}",
                params.topicSelector))
            .map(fetchResult -> buildResponse(params, true,
                fetchResult.results(), null))
            .onErrorMap(TimeoutException.class, e -> timex())
            .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
    }

    /**
     * Execute fetch request without values.
     */
    private static Mono<CallToolResult> executeWithoutValues(
        FetchRequest<Void> fetchRequest,
        FetchParameters params) {

        // Execute the fetch
        return Mono
            .fromFuture(fetchRequest.fetch(params.topicSelector))
            .timeout(TEN_SECONDS)
            .doOnNext(result -> LOG.debug(
                "Fetch without values completed successfully for selector: {}",
                params.topicSelector))
            .map(fetchResult -> {
                List<TopicResult<Void>> results = fetchResult.results();
                return buildResponse(params, false, null, results);
            })
            .onErrorMap(TimeoutException.class, e -> timex())
            .onErrorResume(ex -> monoToolException(
                TOOL_NAME + " " + params.topicSelector, ex, LOG));
    }

    /**
     * Build the response string for both with-values and without-values cases
     */
    private static CallToolResult buildResponse(
        FetchParameters params,
        boolean includeValues,
        List<TopicResult<Object>> resultsWithValues,
        List<TopicResult<Void>> resultsWithoutValues) {

        // Determine which results list to use
        final int resultCount =
            includeValues ?
                resultsWithValues.size() :
                    resultsWithoutValues.size();

        // Set up common info in the result
        final ToolResponse response =
            initialiseResponse(params, includeValues, resultCount);

        if (resultCount == 0) {
            response.addLine("No topics found matching the criteria.");

            // Provide pagination info even for empty results
            if (params.after != null) {
                response.addLine(
                    "This may indicate you've reached the end of the topic tree.");
            }
        }
        else {
            processResults(
                params,
                includeValues,
                resultCount,
                resultsWithValues,
                resultsWithoutValues,
                response);
        }

        LOG.info(
            "Successfully completed fetch: {} topics returned",
            resultCount);

        return toolResult(response);
    }

    /**
     * Sets up the common initial information in the result.
     */
    private static ToolResponse initialiseResponse(
        FetchParameters params,
        boolean includeValues,
        int resultCount) {

        final ToolResponse response = new ToolResponse()
            .addLine("=== Diffusion Topics Fetch Results ===")
            .addLine("Selector: %s", params.topicSelector.getExpression());
         if (params.after != null) {
             response.addLine("After: %s", params.after);
         }
         response.addLine("Requested: %d topics", params.number);
         response.addLine("Values included: %s", includeValues);
         if (params.depth != null) {
             response.addLine("Max depth: %d", params.depth);
         }
         response.addLine("Sizes included: %s", params.includeSizes);
         response.addLine("Unpublished delayed topics included: %s",
             params.includeUnpublishedDelayedTopics);
         response.addLine("Found: %d topics", resultCount);
         response.addLine();
         return response;
    }

    /**
     * Process the results, adding details to the supplied response.
     */
    private static void processResults(
        FetchParameters params,
        boolean includeValues,
        int resultCount,
        List<TopicResult<Object>> resultsWithValues,
        List<TopicResult<Void>> resultsWithoutValues,
        ToolResponse response) {

        // Process results based on type
        if (includeValues) {
            processResultsWithValues(resultsWithValues, params.includeSizes, response);
        }
        else {
            for (int i = 0; i < resultsWithoutValues.size(); i++) {
                final TopicResult<Void> topicResult = resultsWithoutValues.get(i);
                response.addLine("%d. %s", i + 1, topicResult.path());
                response.addLine("   Type: %s", topicResult.type());

                if (params.includeSizes) {
                    addSizeInfo(topicResult, response);
                }

                if (i < resultsWithoutValues.size() - 1) {
                    response.addLine();
                }
            }
        }

        // Pagination guidance
        response.addLine();
        response.addLine("=== Pagination Info ===");
        if (resultCount == params.number) {
            // Get the last topic path from the last result
            final String lastTopicPath = includeValues
                ? resultsWithValues.get(resultCount - 1).path()
                : resultsWithoutValues.get(resultCount - 1).path();

            response.addLine("Retrieved full batch of %d topics.", params.number);
            response.addLine(
                "To get the next batch, use: {\"after\": \"%s\", \"number\": %d}",
                lastTopicPath,
                params.number);
        }
        else {
            response.addLine(
                "Retrieved %d topics (less than requested %d) - likely reached end.",
                resultCount,
                params.number);
        }
    }

    /**
     * Process results with values, adding to the supplied response.
     */
    private static void processResultsWithValues(
        List<TopicResult<Object>> results,
        boolean includeSizes,
        ToolResponse response) {

        for (int i = 0; i < results.size(); i++) {

            final TopicResult<Object> result = results.get(i);

            response.addLine("%d. %s", i + 1, result.path());

            final TopicType type = result.type();
            response.addLine("   Type: %s", type);

            final Object topicValue = result.value();

            if (topicValue == null) {
                response.addLine(valueLine("<null>"));
            }
            else {
                if (type == TopicType.TIME_SERIES) {
                    response.addLine(valueLine(
                        "<Time Series> - Use " +
                            FetchTopicTool.TOOL_NAME + " tool to fetch value"));
                }
                else {
                    addValueToResponse(topicValue, type, response);
                }
            }

            if (includeSizes) {
                addSizeInfo(result, response);
            }

            if (i < results.size() - 1) {
                response.addLine();
            }
        }
    }

    /**
     * Add size information to the response for a topic result.
     */
    private static void addSizeInfo(TopicResult<?> result, ToolResponse response) {
        if (result.type() == TopicType.TIME_SERIES) {
            response.addLine("   Last event size: %d bytes", result.valueSize());
            response.addLine("   Event count: %d", result.valueCount());
            response.addLine("   Total size: %d bytes", result.valueTotalSize());
        }
        else {
            response.addLine("   Value size: %d bytes", result.valueSize());
        }
    }

    /**
     * Add the supplied value to the response, formatting according to topic
     * type.
     */
    private static void addValueToResponse(
        Object value,
        TopicType type,
        ToolResponse response) {

        if (value == null) {
            response.addLine(valueLine("<null>"));
        }
        else {
            switch(type) {
            case JSON:
                addJSONValueToResponse((JSON)value, response);
                break;
            case DOUBLE, INT64, STRING:
                response.addLine(valueLine(value));
                break;
            case BINARY:
                response.addLine(valueLine("<Binary Value>"));
                break;
            case RECORD_V2:
                response.addLine(valueLine(((RecordV2)value).asRecords()));
                break;
            case UNKNOWN_TOPIC_TYPE:
            default:
                response.addLine(valueLine("<Unknown>"));
                break;
            }
        }
    }

    /**
     * Add a (non null) JSON value to the response, truncating it if necessary.
     */
    private static void addJSONValueToResponse(JSON value, ToolResponse response) {
        String valueString;
        try {
            valueString = value.toJsonString();
            final int valueLength = valueString.length();
            if (valueLength > VALUE_TRUNCATE_LENGTH) {
                valueString = valueString.substring(0, VALUE_TRUNCATE_LENGTH);
                response.addLine(
                    valueLine(valueString + "... [truncated, full length: " +
                        valueLength + " chars]"));
            }
            else {
                response.addLine(valueLine(valueString));
            }
        }
        catch (Exception ex) {
            response.addLine(
                valueLine("Error extracting JSON value - " + ex.getMessage()));
        }
    }

    /**
     * Format a value line, wrapping the value with a prefix.
     */
    private static String valueLine(Object value) {
        return VALUE_PREFIX + value;
    }

    /**
     * Used to encapsulate the parameters of the fetch.
     */
    private record FetchParameters(
        TopicSelector topicSelector,
        int number,
        boolean includeValues,
        String after,
        Integer depth,
        boolean includeSizes,
        boolean includeUnpublishedDelayedTopics) {
    }
}