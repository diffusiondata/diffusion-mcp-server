/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS_ADVANCED;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;
import static com.pushtechnology.diffusion.client.topics.details.TopicSpecification.TIME_SERIES_EVENT_VALUE_TYPE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.TimeSeries.Event;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.Topics.FetchResult.TopicResult;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.Bytes;
import com.pushtechnology.diffusion.datatype.DataType;
import com.pushtechnology.diffusion.datatype.InvalidDataException;
import com.pushtechnology.diffusion.datatype.json.JSON;
import com.pushtechnology.diffusion.datatype.recordv2.RecordV2;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Fetch topic tool.
 * <p>
 * Fetches a single topic value and the topic's properties.
 *
 * @author DiffusionData Limited
 */
final class FetchTopicTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(FetchTopicTool.class);

    static final String TOOL_NAME = "fetch_topic";

    private static final String TOOL_DESCRIPTION =
        "Fetches the string value, type, and properties of a topic from the connected Diffusion server. " +
            "Needs SELECT_TOPIC permission to the topic path. The topic will ony be returned if the caller has " +
            "READ_TOPIC permission. " +
            "See the " + TOPICS + " context for information about topics and " +
            TOPICS_ADVANCED + " to understand the topic prperties.";

    private static final String TOPIC_PATH = "topicPath";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_PATH,
                stringProperty(
                    "The path of the topic to fetch the value from"))
            .required(TOPIC_PATH)
            .additionalProperties(false)
            .build();

    private FetchTopicTool() {
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

                // Extract the topic path parameter
                final String topicPath =
                    stringArgument(request.arguments(), TOPIC_PATH);

                LOG.info("Starting fetch operation for topic: {}", topicPath);

                return Mono
                    .fromFuture(
                        session.feature(Topics.class)
                            .fetchRequest()
                            .withValues(Object.class)
                            .withProperties() // Always fetch properties
                            .fetch(topicPath))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Fetch completed successfully for topic: {}",
                        topicPath))
                    .map(fetchResult -> {
                        // Process the fetch result
                        final List<TopicResult<Object>> results =
                            fetchResult.results();

                        if (results.isEmpty()) {
                            return toolError("Topic '%s' not found", topicPath);
                        }

                        if (results.size() > 1) {
                            return toolError(
                                "Topic '%s' resolved to more than one value",
                                topicPath);
                        }

                        final TopicResult<Object> topicResult = results.get(0);
                        final Object value = topicResult.value();
                        final TopicSpecification specification =
                            topicResult.specification();

                        // Extract type and properties from specification
                        final TopicType type = specification.getType();
                        final Map<String, String> topicProperties =
                            specification.getProperties();

                        final String parsedValue =
                            parseTopicValue(value, type, topicProperties);

                        // Create comprehensive result
                        final Map<String, Object> result = new HashMap<>();
                        result.put("path", topicPath);
                        result.put("value", parsedValue);
                        result.put("type", type.toString());
                        result.put("properties", topicProperties);

                        LOG.info("Successfully fetched topic '{}'", topicPath);

                        try {
                            return toolResult(
                                OBJECT_MAPPER.writeValueAsString(result));
                        }
                        catch (JsonProcessingException e) {
                            LOG.error(
                                "Error serializing result for topic: {}",
                                topicPath,
                                e);
                            return toolError(
                                "Error serializing topic result: %s",
                                e.getMessage());
                        }

                    })
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, topicPath), ex, LOG));
            })
            .build();
    }

    private static String parseTopicValue(Object value, TopicType type, Map<String, String> properties) {
        try {
            if (type == TopicType.TIME_SERIES) {
                @SuppressWarnings("unchecked")
                final Event<Bytes> event = (Event<Bytes>)value;
                final String eventType = properties.get(TIME_SERIES_EVENT_VALUE_TYPE);
                final DataType<?> dataType = Diffusion.dataTypes().getByName(eventType);
                final Object eventValue = dataType.readValue(event.value());
                return parseValue(eventValue, TopicType.valueOf(eventType.toUpperCase()));
            }
            else {
                return parseValue(value, type);
            }
        }
        catch (InvalidDataException ex) {
            LOG.debug("Invalid topic data");
            return "<Invalid>";
        }
    }

    private static String parseValue(Object value, TopicType type) {
        if (value == null) {
            return "<null>";
        }
        switch(type) {
        case BINARY, DOUBLE, INT64, STRING:
            return value.toString();
        case JSON:
            return ((JSON)value).toJsonString();
        case RECORD_V2:
            return ((RecordV2)value).asRecords().toString();
        case UNKNOWN_TOPIC_TYPE:
        default:
            return "<Unknown>";
        }
    }
}