/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS_ADVANCED;
import static com.diffusion.mcp.tools.JsonSchemas.boolProperty;
import static com.diffusion.mcp.tools.JsonSchemas.enumProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.argumentIsFalse;
import static com.diffusion.mcp.tools.ToolUtils.argumentIsTrue;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;
import static com.pushtechnology.diffusion.client.Diffusion.dataTypes;
import static com.pushtechnology.diffusion.client.Diffusion.newTopicSpecification;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.TopicCreationResult;
import com.pushtechnology.diffusion.client.features.TopicUpdate;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Add topic tool.
 * <p>
 * Creates a new topic with the specified path, type, properties, and optional
 * initial value.
 *
 * @author DiffusionData Limited
 */
final class AddTopicTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(AddTopicTool.class);

    static final String TOOL_NAME = "add_topic";

    private static final String TOOL_DESCRIPTION =
        "Creates a new topic on the connected Diffusion server with the specified path, type, properties, and optional initial value. " +
            "Needs MODIFY_TOPIC permission for the topic path. " +
            "See the " + TOPICS +
            " context for general information about topics and the " +
            TOPICS_ADVANCED + " context  for " +
            "details of the properties that can be supplied.";

    /*
     * Parameters.
     */
    private static final String TOPIC_PATH = "topicPath";
    private static final String TOPIC_TYPE = "type";
    private static final String INITIAL_VALUE = "initialValue";
    private static final String COMPRESSION = "compression";
    private static final String CONFLATION = "conflation";
    private static final String DONT_RETAIN_VALUE = "dontRetainValue";
    private static final String OWNER = "owner";
    private static final String PERSISTENT = "persistent";
    private static final String PRIORITY = "priority";
    private static final String PUBLISH_VALUES_ONLY = "publishValuesOnly";
    private static final String REMOVAL = "removal";
    private static final String TIDY_ON_UNSUBSCRIBE = "tidyOnUnsubscribe";
    private static final String TIME_SERIES_EVENT_VALUE_TYPE = "timeSeriesEventValueType";
    private static final String TIME_SERIES_RETAINED_RANGE = "timeSeriesRetainedRange";
    private static final String TIME_SERIES_SUBSCRIPTION_RANGE = "timeSeriesSubscriptionRange";
    private static final String VALIDATE_VALUES = "validateValues";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_PATH,
                stringProperty("The path of the topic to create"))
            .property(
                TOPIC_TYPE,
                enumProperty(
                    "The type of the topic",
                    List.of("STRING", "JSON", "BINARY", "DOUBLE", "INT64", "TIME_SERIES")))
            .property(
                INITIAL_VALUE,
                stringProperty("Optional initial value for the topic"))
            .property(
                COMPRESSION,
                enumProperty(
                    "Compression policy for the topic. Default is 'low'",
                    List.of("off", "low", "medium", "high", "true", "false") ))
            .property(
                CONFLATION,
                enumProperty(
                    "Conflation policy for the topic. Default is 'conflate'",
                    List.of("off", "conflate", "unsubscribe", "always")))
            .property(
                DONT_RETAIN_VALUE,
                boolProperty(
                    "Whether the topic should not retain its last value (default false)"))
            .property(
                OWNER,
                stringProperty("Principal that owns the topic " +
                    "(to assign a topic to someone other than the creator)."))
            .property(
                PERSISTENT,
                boolProperty(
                    "Whether the topic should be persisted, " +
                    "if persistence is enabled at the server. (default is true)"))
            .property(
                PRIORITY,
                enumProperty(
                    "Delivery priority for the topic. Default is 'default'",
                    List.of("low", "default", "high")))
            .property(
                PUBLISH_VALUES_ONLY,
                boolProperty(
                    "Whether the topic should publish only values (disable delta streams) - (default is false)"))
            .property(
                REMOVAL,
                stringProperty(
                    "Removal policy expression for automatic topic removal. " +
                    "Supports: 'when time after <period>' (e.g., 'when time after 10m', 'when time after 2h'), " +
                    "absolute milliseconds since epoch, or RFC 1123 date format. " +
                    "Period format: number followed by s(econds), m(inutes), h(ours), or d(ays). " +
                    "For other removal policies see the advanced topics guide. " +
                    "By default the topic will not be automatically removed."))
            .property(
                TIDY_ON_UNSUBSCRIBE,
                boolProperty(
                    "Whether to remove queued updates when sessions unsubscribe (default is false)"))
            .property(
                TIME_SERIES_EVENT_VALUE_TYPE,
                enumProperty(
                    "Event data type for TIME_SERIES topics. Mandatory if 'type' is TIME_SERIES.",
                    List.of("string", "json", "binary", "double", "int64")))
            .property(
                TIME_SERIES_RETAINED_RANGE,
                stringProperty(
                    "Range of events retained by TIME_SERIES topics. " +
                    "If not supplied for a TIME_SERIES topic the last 10 events will be retained."))
            .property(
                TIME_SERIES_SUBSCRIPTION_RANGE,
                stringProperty(
                    "Range of events sent to new subscribers of TIME_SERIES topics. " +
                    "If not supplied for a TIME_SERIES topic subscribers will only receive the latest event."))
            .property(
                VALIDATE_VALUES,
                boolProperty(
                    "Whether the topic should validate inbound values. (default is false)"))
            .required(TOPIC_PATH)
            .additionalProperties(false)
            .build();

    private AddTopicTool() {
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

                // Check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final Map<String, Object> arguments = request.arguments();

                // Extract parameters
                final String topicPath = stringArgument(arguments, TOPIC_PATH);
                final String topicTypeString =
                    stringArgument(arguments, TOPIC_TYPE, "JSON");
                final String initialValue =
                    stringArgument(arguments, INITIAL_VALUE);

                LOG.info(
                    "Starting add topic operation for path: {} with type: {}",
                    topicPath,
                    topicTypeString);

                final TopicType topicType;
                try {
                    // Parse topic type
                    topicType = TopicType.valueOf(topicTypeString.toUpperCase());
                }
                catch (IllegalArgumentException e) {
                    LOG.warn("Invalid topic type: {}", topicTypeString);
                    return monoToolError("Invalid topic type '%s'", topicTypeString);
                }

                // Build topic specification
                final TopicSpecification specification;
                try {
                    specification = createSpecification(topicType, arguments);
                }
                catch (IllegalArgumentException ex) {
                    LOG.warn("Invalid topic specification : {}", ex.getMessage());
                    return monoToolError("Invalid specification : %s", ex.getMessage());
                }

                // Create the topic with or without initial value
                if (initialValue != null) {
                    return createTopicWithValue(
                        session,
                        topicPath,
                        topicType,
                        specification,
                        initialValue);
                }
                else {
                    return createTopicWithoutValue(
                        session,
                        topicPath,
                        topicType,
                        specification);
                }
            })
            .build();
    }

    private static TopicSpecification createSpecification(
        TopicType type,
        Map<String, Object> arguments) {

        // Build topic specification
        TopicSpecification specification = newTopicSpecification(type);

        // Add properties based on the arguments
        final String compression = stringArgument(arguments, COMPRESSION);
        if (compression != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.COMPRESSION, compression);
        }

        final String conflation = stringArgument(arguments, CONFLATION);
        if (conflation != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.CONFLATION, conflation);
        }

        if (argumentIsTrue(arguments, DONT_RETAIN_VALUE)) {
            specification =
                specification.withProperty(
                    TopicSpecification.DONT_RETAIN_VALUE, "true");
        }

        final String owner = stringArgument(arguments, OWNER);
        if (owner != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.OWNER, owner);
        }

        if (argumentIsFalse(arguments, PERSISTENT)) {
            specification =
                specification.withProperty(
                    TopicSpecification.PERSISTENT, "false");
        }

        final String priority = stringArgument(arguments, PRIORITY);
        if (priority != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.PRIORITY, priority);
        }

        if (argumentIsTrue(arguments, PUBLISH_VALUES_ONLY)) {
            specification =
                specification.withProperty(
                    TopicSpecification.PUBLISH_VALUES_ONLY, "true");
        }

        final String removal = stringArgument(arguments, REMOVAL);
        if (removal != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.REMOVAL,
                    parseRemovalPolicy(removal));
        }

        if (argumentIsTrue(arguments, TIDY_ON_UNSUBSCRIBE)) {
            specification =
                specification.withProperty(
                    TopicSpecification.TIDY_ON_UNSUBSCRIBE, "true");
        }

        if (argumentIsTrue(arguments, VALIDATE_VALUES)) {
            specification =
                specification.withProperty(
                    TopicSpecification.VALIDATE_VALUES, "true");
        }

        if (type == TopicType.TIME_SERIES) {
            specification = parseTimeSeriesArguments(arguments, specification);
        }

        return specification;
    }

    private static TopicSpecification parseTimeSeriesArguments(
        Map<String, Object> arguments,
        TopicSpecification currentSpecification) {

        TopicSpecification specification = currentSpecification;
        final String timeSeriesEventType =
            stringArgument(arguments, TIME_SERIES_EVENT_VALUE_TYPE);
        if (timeSeriesEventType != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.TIME_SERIES_EVENT_VALUE_TYPE,
                    timeSeriesEventType);
        }
        else {
            throw new IllegalArgumentException(
                "Event value type must be specified for a time series topic");
        }

        final String timeSeriesRetainedRange =
            stringArgument(arguments, TIME_SERIES_RETAINED_RANGE);
        if (timeSeriesRetainedRange != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.TIME_SERIES_RETAINED_RANGE,
                    timeSeriesRetainedRange);
        }

        final String timeSeriesSubscriptionRange =
            stringArgument(arguments, TIME_SERIES_SUBSCRIPTION_RANGE);
        if (timeSeriesSubscriptionRange != null) {
            specification =
                specification.withProperty(
                    TopicSpecification.TIME_SERIES_SUBSCRIPTION_RANGE,
                    timeSeriesSubscriptionRange);
        }

        return specification;
    }

    /**
     * Parse removal policy, converting 'time after <period>' clauses to
     * 'time after <absoluteMillis>' while preserving the rest of the expression.
     * <p>
     * This overcomes the problem that Diffusion does not support this simple
     * command format but it is easier for an LLM to use as the LLM does not
     * know the time at the MCP server and it is more difficult to specify a
     * relative time - like topic should remove after 10 minutes.
     * <p>
     * Examples:<br>
     * - "time after 10m" -> "time after 1696..."<br>
     * - "when no updates for 2h" -> unchanged<br>
     * - "when no updates for 2h or time after 3h" -> "when no updates for 2h or time after 1696..."<br>
     * - "when this session closes or time after 1h" -> "when this session closes or time after 1696..."<br>
     */
    private static String parseRemovalPolicy(String removal) {

        if (removal == null) {
            return null;
        }

        // Fast path: nothing to do if "time after" isn't present at all
        final Pattern checkPattern =
            Pattern.compile("time\\s+after", CASE_INSENSITIVE);
        if (!checkPattern.matcher(removal).find()) {
            return removal;
        }

        // Only match the 'time after <period>' fragment (never 'when ')
        final Pattern pattern =
            Pattern.compile("(?i)\\btime\\s+after\\s+(\\d+)\\s*([smhd])\\b");

        final Matcher matcher = pattern.matcher(removal);
        final StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            final String periodString =
                matcher.group(1) + matcher.group(2); // e.g. "10m"
            final long periodMillis = parsePeriod(periodString);
            final long absoluteTimeMillis = System.currentTimeMillis() + periodMillis;

            // Keep 'time after' in the text so compound expressions remain valid
            matcher.appendReplacement(sb, "time after " + absoluteTimeMillis);
        }

        matcher.appendTail(sb);
        final String result = sb.toString();

        if (!result.equals(removal)) {
            LOG.info("Converted removal policy from '{}' to '{}'", removal, result);
        }

        return result;
    }

    /**
     * Parse a period string like "10m", "2h", "30s", "1d" into milliseconds.
     *
     * @param periodString the period string
     * @return milliseconds
     * @throws IllegalArgumentException if format is invalid
     */
    private static long parsePeriod(String periodString) {

        if (periodString.length() < 2) {
            throw new IllegalArgumentException(
                "Invalid period format: '" + periodString +
                "'. Expected format: <number><unit> (e.g., 10m, 2h, 30s, 1d)");
        }

        // Extract number and unit
        final char unit = periodString.charAt(periodString.length() - 1);
        final String numberString =
            periodString.substring(0, periodString.length() - 1);

        final long number;
        try {
            number = Long.parseLong(numberString);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid period format: '" + periodString +
                "'. Number part '" + numberString + "' is not a valid integer");
        }

        if (number <= 0) {
            throw new IllegalArgumentException(
                "Invalid period: " + number + ". Period must be positive");
        }

        // Convert to milliseconds based on unit
        switch (Character.toLowerCase(unit)) {
            case 's':  // seconds
                return number * 1000L;
            case 'm':  // minutes
                return number * 60L * 1000L;
            case 'h':  // hours
                return number * 60L * 60L * 1000L;
            case 'd':  // days
                return number * 24L * 60L * 60L * 1000L;
            default:
                throw new IllegalArgumentException(
                    "Invalid period unit: '" + unit +
                    "'. Valid units: s(econds), m(inutes), h(ours), d(ays)");
        }
    }

    private static Mono<CallToolResult> createTopicWithoutValue(
        Session session,
        String topicPath,
        TopicType topicType,
        TopicSpecification specification) {

        return Mono
            .fromFuture(
                session.feature(TopicControl.class)
                    .addTopic(topicPath, specification))
            .timeout(TEN_SECONDS)
            .doOnNext(result -> LOG.info(
                "Successfully created topic: {} with result: {}",
                topicPath,
                result))
            .<CallToolResult> map(addResult -> {
                try {
                    return toolResult(
                        OBJECT_MAPPER.writeValueAsString(
                            Map.of(
                                "path", topicPath,
                                "type", topicType.toString(),
                                "created", true)));
                }
                catch (JsonProcessingException e) {
                    LOG.error("Error serialising result for topic: {}",
                        topicPath, e);
                    return toolError("Error serialising topic result: %s",
                        e.getMessage());
                }
            })
            .onErrorMap(TimeoutException.class, e -> timex())
            .onErrorResume(
                ex -> monoToolException(
                    toolOperation(TOOL_NAME, topicPath), ex, LOG));
    }

    // Helper method to handle the generic typing for addAndSet
    @SuppressWarnings("unchecked")
    private static <T> Mono<TopicCreationResult> addAndSetTyped(
        Session session,
        String topicPath,
        TopicSpecification specification,
        ValueClassPair valueClassPair) {

        final Class<T> valueClass = (Class<T>) valueClassPair.valueClass;
        final T value = (T) valueClassPair.value;

        return Mono.fromFuture(
            session.feature(TopicUpdate.class)
                .addAndSet(topicPath, specification, valueClass, value));
    }

    private static Mono<CallToolResult> createTopicWithValue(
        Session session,
        String topicPath,
        TopicType topicType,
        TopicSpecification specification,
        String initialValue) {

        try {
            // Convert the initial value to the appropriate data type and class
            final ValueClassPair valueClassPair =
                convertValue(initialValue, topicType);

            // Use proper generic typing for addAndSet
            return addAndSetTyped(
                session,
                topicPath,
                specification,
                valueClassPair)
                .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.info(
                        "Successfully created topic with value: {} with result: {}",
                        topicPath, result))
                    .<CallToolResult> map(creationResult -> {
                        // Create result
                        final Map<String, Object> result = new HashMap<>();
                        result.put("path", topicPath);
                        result.put("type", topicType.toString());
                        result.put("created", true);

                        try {
                            final String jsonResult =
                                OBJECT_MAPPER.writeValueAsString(result);
                            return toolResult(jsonResult);
                        }
                        catch (JsonProcessingException e) {
                            LOG.error("Error serialising result for topic: {}",
                                topicPath, e);
                            return toolError(
                                "Error serialising topic result: %s",
                                e.getMessage());
                        }
                    })
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, topicPath), ex, LOG));

        }
        catch (Exception e) {
            return monoToolError(
                "Error converting initial value '%s' for type %s: %s",
                initialValue, topicType, e.getMessage());
        }
    }

    private static ValueClassPair convertValue(
        String value,
        TopicType topicType) {

        switch (topicType) {

        case STRING:
            return new ValueClassPair(String.class, value);

        case JSON:
            // Validate JSON and convert to JSON datatype
            final Object jsonValue =
                dataTypes().json().fromJsonString(value);
            return new ValueClassPair(jsonValue.getClass(), jsonValue);

        case BINARY:
            // Convert string to bytes (assuming UTF-8) and read as Binary
            final Object binaryValue =
                dataTypes().binary().readValue(value.getBytes(StandardCharsets.UTF_8));
            return new ValueClassPair(binaryValue.getClass(), binaryValue);

        case DOUBLE:
            return new ValueClassPair(Double.class, Double.parseDouble(value));

        case INT64:
            return new ValueClassPair(Long.class, Long.parseLong(value));

        case TIME_SERIES:
            throw new IllegalArgumentException(
                "Initial values are not supported for TIME_SERIES topics");

        default:
            throw new IllegalArgumentException(
                "Unsupported topic type: " + topicType);
        }
    }

    // Helper class to hold both value and its class
    private static class ValueClassPair {
        final Class<?> valueClass;
        final Object value;

        private ValueClassPair(Class<?> valueClass, Object value) {
            this.valueClass = valueClass;
            this.value = value;
        }
    }
}