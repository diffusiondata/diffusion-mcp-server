/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.prompts.ContextGuides.TOPICS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPICS_ADVANCED;
import static com.diffusion.mcp.tools.JsonSchemas.enumProperty;
import static com.diffusion.mcp.tools.JsonSchemas.intProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.intArgument;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.features.TimeSeries;
import com.pushtechnology.diffusion.client.features.TimeSeries.Event;
import com.pushtechnology.diffusion.client.features.TimeSeries.QueryResult;
import com.pushtechnology.diffusion.client.features.TimeSeries.RangeQuery;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.details.TopicType;
import com.pushtechnology.diffusion.datatype.binary.Binary;
import com.pushtechnology.diffusion.datatype.json.JSON;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool to perform value range queries on time series topics.
 *
 * @author DiffusionData Limited
 */
final class TimeSeriesValueRangeQueryTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(TimeSeriesValueRangeQueryTool.class);

    static final String TOOL_NAME = "time_series_value_range_query";

    private static final String TOOL_DESCRIPTION =
        "Queries a time series topic to retrieve a value range (merged view with latest edits). " +
            "Can specify range by sequence number, timestamp, count, or duration. " +
            "Returns event metadata (sequence, timestamp, author) and values. " +
            "Needs READ_TOPIC permission to the topic path. " +
            "See the " + TOPICS +
            " context for information about time series topics and " +
            TOPICS_ADVANCED +
            " for more about teh time series properties.";

    private static final String TOPIC_PATH = "topicPath";
    private static final String EVENT_VALUE_TYPE = "eventValueType";
    private static final String FROM_SEQUENCE = "fromSequence";
    private static final String FROM_TIMESTAMP = "fromTimestamp";
    private static final String FROM_LAST = "fromLast";
    private static final String TO_SEQUENCE = "toSequence";
    private static final String TO_TIMESTAMP = "toTimestamp";
    private static final String NEXT = "next";
    private static final String MAX_RESULTS = "maxResults";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_PATH,
                stringProperty("The path of the time series topic to query"))
            .property(
                EVENT_VALUE_TYPE,
                enumProperty(
                    "The data type of events in the time series",
                    List.of("STRING", "INT64", "DOUBLE", "JSON", "BINARY")))
            .property(
                FROM_SEQUENCE,
                intProperty(
                    "Start from this sequence number (optional)",
                    0,
                    null,
                    null))
            .property(
                FROM_TIMESTAMP,
                stringProperty(
                    "Start from this ISO-8601 timestamp (optional)"))
            .property(
                FROM_LAST,
                intProperty(
                    "Start from this many events before the end (optional)",
                    0,
                    null,
                    null))
            .property(
                TO_SEQUENCE,
                intProperty(
                    "End at this sequence number (optional)",
                    0,
                    null,
                    null))
            .property(
                TO_TIMESTAMP,
                stringProperty(
                    "End at this ISO-8601 timestamp (optional)"))
            .property(
                NEXT,
                intProperty(
                    "Return this many events after the start (optional)",
                    1,
                    null,
                    null))
            .property(
                MAX_RESULTS,
                intProperty(
                    "Maximum number of events to return (default: 100)",
                    1,
                    1000,
                    100))
            .required(TOPIC_PATH, EVENT_VALUE_TYPE)
            .additionalProperties(false)
            .build();

    private TimeSeriesValueRangeQueryTool() {
    }

    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()
            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())
            .callHandler((exchange, request) -> {

                final Session session =
                    sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final Map<String, Object> arguments = request.arguments();
                final String topicPath =
                    stringArgument(arguments, TOPIC_PATH);
                final String eventValueType =
                    stringArgument(arguments, EVENT_VALUE_TYPE);

                LOG.info("Querying time series topic: {} with event type: {}",
                    topicPath, eventValueType);

                try {
                    final RangeQuery<?> query =
                        buildQuery(session, eventValueType, arguments);

                    return Mono
                        .fromFuture(query.selectFrom(topicPath))
                        .timeout(TEN_SECONDS)
                        .doOnNext(result -> LOG.info(
                            "Successfully queried time series topic: {} - {} events selected",
                            topicPath,
                            result.selectedCount()))
                        .map(result -> buildResponse(
                            topicPath,
                            result,
                            eventValueType))
                        .onErrorMap(TimeoutException.class, e -> timex())
                        .onErrorResume(ex -> monoToolException(
                            toolOperation(TOOL_NAME, topicPath), ex, LOG));
                }
                catch (IllegalArgumentException e) {
                    return monoToolError(
                        "Invalid query parameters: %s",
                        e.getMessage());
                }
            })
            .build();
    }

    private static RangeQuery<?> buildQuery(
        Session session,
        String eventValueType,
        Map<String, Object> arguments) {

        final Class<?> valueClass = getValueClass(eventValueType);

        RangeQuery<?> query = session.feature(TimeSeries.class)
            .rangeQuery()
            .forValues()
            .as(valueClass);

        // Set anchor (from)
        final Integer fromSequence =
            intArgument(arguments, FROM_SEQUENCE);
        final String fromTimestamp =
            stringArgument(arguments, FROM_TIMESTAMP);
        final Integer fromLast =
            intArgument(arguments, FROM_LAST);

        if (fromSequence != null) {
            query = query.from(fromSequence.longValue());
        }
        else if (fromTimestamp != null) {
            query = query.from(Instant.parse(fromTimestamp));
        }
        else if (fromLast != null) {
            query = query.fromLast(fromLast.longValue());
        }
        else {
            query = query.fromStart();
        }

        // Set span (to/next)
        final Integer toSequence = intArgument(arguments, TO_SEQUENCE);
        final String toTimestamp = stringArgument(arguments, TO_TIMESTAMP);
        final Integer next = intArgument(arguments, NEXT);

        if (toSequence != null) {
            query = query.to(toSequence.longValue());
        }
        else if (toTimestamp != null) {
            query = query.to(Instant.parse(toTimestamp));
        }
        else if (next != null) {
            query = query.next(next.longValue());
        }
        // Otherwise no span - query to end of series

        // Set limit
        final Integer maxResults = intArgument(arguments, MAX_RESULTS);
        final int limit = maxResults != null ? maxResults : 100;
        query = query.limit(limit);

        return query;
    }

    private static Class<?> getValueClass(String eventValueType) {
        return switch (eventValueType.toUpperCase()) {
        case "STRING" -> String.class;
        case "INT64" -> Long.class;
        case "DOUBLE" -> Double.class;
        case "JSON" -> JSON.class;
        case "BINARY" -> Binary.class;
        default -> throw new IllegalArgumentException(
            "Unsupported event value type: " + eventValueType);
        };
    }

    private static CallToolResult buildResponse(
        String topicPath,
        QueryResult<?> result,
        String eventValueType) {

        final ToolResponse response = new ToolResponse()
            .addLine("=== Time Series Query Results ===")
            .addLine("Topic: %s", topicPath)
            .addLine("Event Value Type: %s", eventValueType)
            .addLine("Selected Events: %d", result.selectedCount())
            .addLine("Complete: %s", result.isComplete() ? "Yes" : "No")
            .addLine();

        final List<Event<?>> events = result.stream()
            .collect(Collectors.toList());

        if (events.isEmpty()) {
            response.addLine("No events found in the specified range.");
        }
        else {
            response.addLine("Events");
            response.addLine();

            for (Event<?> event : events) {
                response.addLine("Sequence: %d", event.sequence());
                response.addLine("  Timestamp: %s (%d)",
                    Instant.ofEpochMilli(event.timestamp()),
                    event.timestamp());
                response.addLine("  Author: %s", event.author());

                if (event.isEditEvent()) {
                    response.addLine("  [Edited - Original Sequence: %d]",
                        event.originalEvent().sequence());
                }

                final String valueString =
                    formatValue(event.value(), eventValueType);
                if (valueString.length() > 200) {
                    response.addLine("  Value: %s... (%d chars)",
                        valueString.substring(0, 200),
                        valueString.length());
                }
                else {
                    response.addLine("  Value: %s", valueString);
                }

                response.addLine();
            }

            if (!result.isComplete()) {
                response.addLine("... and %d more events not shown.",
                    result.selectedCount() - events.size());
                response.addLine("Increase maxResults parameter to see more.");
            }
        }

        return toolResult(response);
    }

    private static String formatValue(Object value, String eventValueType) {

        if (value == null) {
            return "<null>";
        }

        try {
            final TopicType type =
                TopicType.valueOf(eventValueType.toUpperCase());
            if (TopicType.JSON == type) {
                return ((JSON) value).toJsonString();
            }
            else {
                return value.toString();
            }
        }
        catch (Exception e) {
            LOG.warn(
                "Error formatting value of type {}: {}",
                eventValueType,
                e.getMessage());
            return value.toString();
        }
    }
}