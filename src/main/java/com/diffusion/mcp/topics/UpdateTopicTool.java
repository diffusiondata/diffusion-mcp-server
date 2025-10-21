/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS;
import static com.diffusion.mcp.tools.JsonSchemas.enumProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;
import static com.pushtechnology.diffusion.client.Diffusion.dataTypes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.TopicUpdate;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.binary.Binary;
import com.pushtechnology.diffusion.datatype.json.JSON;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Update topic tool.
 * <p>
 * Updates an existing topic with a new value on the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class UpdateTopicTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(UpdateTopicTool.class);

    static final String TOOL_NAME = "update_topic";

    private static final String TOOL_DESCRIPTION =
        "Updates an existing topic with a new value on the connected Diffusion server. " +
            "Needs UPDATE_TOPIC permission to the topic path. " +
            "See the " + TOPICS +
            " context for more information about working with topics.";

    /*
     * Parameters.
     */

    private static final String TOPIC_PATH = "topicPath";
    private static final String TOPIC_TYPE = "type";
    private static final String VALUE = "value";
    private static final String EVENT_TYPE = "eventType";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_PATH,
                stringProperty("The path of the topic to update"))
            .property(
                TOPIC_TYPE,
                enumProperty(
                    "The type of the topic being updated",
                    List.of("STRING", "JSON", "BINARY", "DOUBLE", "INT64", "TIME_SERIES")))
            .property(
                VALUE,
                stringProperty(
                    "The new value for the topic. " +
                    "For STRING, JSON, BINARY, DOUBLE, and INT64 topics. " +
                    "For TIME_SERIES topics, this is the event data."))
            .property(
                EVENT_TYPE,
                enumProperty(
                    "Required for TIME_SERIES topics - " +
                    "the data type of the event being added to the time series.",
                    List.of("string", "json", "binary", "double", "int64")))
            .required(TOPIC_PATH, TOPIC_TYPE, VALUE)
            .additionalProperties(false)
            .build();

    private UpdateTopicTool() {
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

                // Extract parameters
                final Map<String, Object> arguments = request.arguments();
                final String topicPath = stringArgument(arguments, TOPIC_PATH);
                final String typeString = stringArgument(arguments, TOPIC_TYPE);
                final String value = stringArgument(arguments, VALUE);
                final String eventType = stringArgument(arguments, EVENT_TYPE);

                LOG.info(
                    "Starting update topic operation for path: {} with type: {}",
                    topicPath,
                    typeString);

                final TopicType topicType;
                try {
                    // Parse topic type
                    topicType = TopicType.valueOf(typeString.toUpperCase());
                }
                catch (IllegalArgumentException e) {
                    return monoToolError("Invalid topic type '%s'", typeString);
                }

                // Validate TIME_SERIES requirements
                if (topicType == TopicType.TIME_SERIES && eventType == null) {
                    return monoToolError(
                        EVENT_TYPE + " is required for TIME_SERIES topics");
                }

                // Update the topic
                return updateTopic(session, topicPath, topicType, value, eventType);
            })
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Mono<CallToolResult> updateTopic(
        Session session,
        String topicPath,
        TopicType topicType,
        String value,
        String eventType) {

        try {
            final ValueClassPair valueClassPair =
                convertValue(value, topicType, eventType);

            final Class<?> valueClass = valueClassPair.valueClass;
            final Object valueToSet = valueClassPair.value;

            return Mono
                .fromFuture(
                    session.feature(TopicUpdate.class)
                        .set(topicPath, (Class<Object>) valueClass, valueToSet))
                .timeout(TEN_SECONDS)
                .doOnSuccess(updateResult -> LOG.debug(
                    "Successfully updated topic: {}", topicPath))
                .then(Mono.fromCallable(() -> {
                    try {
                        return toolResult(
                            OBJECT_MAPPER.writeValueAsString(
                                Map.of(
                                    "path", topicPath,
                                    "type", topicType.toString(),
                                    "updated", true)));
                    }
                    catch (JsonProcessingException e) {
                        throw new SessionException(
                            "Error serializing update result: " + e.getMessage());
                    }
                }))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex -> monoToolException(
                    toolOperation(TOOL_NAME, topicPath), ex, LOG));
        }
        catch (Exception e) {
            // Wrap as SessionException and let monoToolException handle it
            return monoToolException(
                "updating topic " + topicPath,
                new SessionException(
                    "Error converting value '" + value + "' for type " +
                    topicType + ": " + e.getMessage()),
                LOG);
        }
    }

    // Helper class to hold both value and its class
    private static class ValueClassPair {
        final Class<?> valueClass;
        final Object value;

        ValueClassPair(Class<?> valueClass, Object value) {
            this.valueClass = valueClass;
            this.value = value;
        }
    }

    private static ValueClassPair convertValue(
        String value,
        TopicType topicType,
        String eventType) {

        switch (topicType) {

        case STRING:
            return new ValueClassPair(String.class, value);

        case JSON:
            return new ValueClassPair(
                JSON.class,
                dataTypes().json().fromJsonString(value));

        case BINARY:
            // Convert string to bytes (assuming UTF-8) and read as Binary
            return new ValueClassPair(
                Binary.class,
                dataTypes().binary().readValue(
                    value.getBytes(StandardCharsets.UTF_8)));

        case DOUBLE:
            return new ValueClassPair(Double.class, Double.parseDouble(value));

        case INT64:
            return new ValueClassPair(Long.class, Long.parseLong(value));

        case TIME_SERIES:
            // For TIME_SERIES, convert based on the event type
            return convertTimeSeriesValue(value, eventType);

        default:
            throw new IllegalArgumentException(
                "Unsupported topic type: " + topicType);
        }
    }

    private static ValueClassPair convertTimeSeriesValue(
        String value,
        String eventType) {

        switch (eventType.toLowerCase()) {
        case "string":
            return new ValueClassPair(String.class, value);

        case "json":
            return new ValueClassPair(
                JSON.class,
                dataTypes().json().fromJsonString(value));

        case "binary":
            return new ValueClassPair(
                Binary.class,
                dataTypes().binary().readValue(
                    value.getBytes(StandardCharsets.UTF_8)));

        case "double":
            return new ValueClassPair(Double.class, Double.parseDouble(value));

        case "int64":
            return new ValueClassPair(Long.class, Long.parseLong(value));

        default:
            throw new IllegalArgumentException(
                "Unsupported TIME_SERIES event type: " + eventType);
        }
    }
}