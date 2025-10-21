/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.prompts.ContextGuides.METRICS;
import static com.diffusion.mcp.prompts.ContextGuides.SESSIONS;
import static com.diffusion.mcp.tools.JsonSchemas.boolProperty;
import static com.diffusion.mcp.tools.JsonSchemas.intProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.argumentIsTrue;
import static com.diffusion.mcp.tools.ToolUtils.intArgument;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.SessionMetricCollector;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool for creating session metric collectors.
 *
 * @author DiffusionData Limited
 */
final class CreateSessionMetricCollectorTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(CreateSessionMetricCollectorTool.class);

    static final String TOOL_NAME = "create_session_metric_collector";

    private static final String TOOL_DESCRIPTION =
        "Creates a new session metric collector with specified configuration. " +
            "Needs CONTROL_SERVER permission. " +
            "Consult " + METRICS +
            " context before creating metric collectors. " +
            "Also see the " + SESSIONS +
            " context to understand session filters.";

    /*
     * Parameters.
     */
    private static final String COLLECTOR_NAME = "name";
    private static final String SESSION_FILTER = "sessionFilter";
    private static final String GROUP_BY_PROPERTIES = "groupByProperties";
    private static final String EXPORT_TO_PROMETHEUS = "exportToPrometheus";
    private static final String MAXIMUM_GROUPS = "maximumGroups";
    private static final String REMOVE_METRICS_WITH_NO_MATCHES =
        "removeMetricsWithNoMatches";

    /*
     * Input.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                COLLECTOR_NAME,
                stringProperty("Unique name for the metric collector"))
            .property(
                SESSION_FILTER,
                stringProperty(
                    "Session filter to select which sessions to collect metrics for " +
                        "(e.g., '$Principal is \"user1\"' or 'all')"))
            .property(
                GROUP_BY_PROPERTIES,
                stringProperty(
                    "Comma-separated list of session properties to group by " +
                        "(optional, e.g., '$Principal,$ClientIP')"))
            .property(
                EXPORT_TO_PROMETHEUS,
                boolProperty(
                    "Whether to export metrics to Prometheus endpoint (default: false)"))
            .property(
                MAXIMUM_GROUPS,
                intProperty(
                    "Maximum number of groups to maintain (optional, default: unlimited)"))
            .property(
                REMOVE_METRICS_WITH_NO_MATCHES,
                boolProperty(
                    "Whether to remove metrics that have no matches (default: false)"))
            .required(COLLECTOR_NAME, SESSION_FILTER)
            .additionalProperties(false)
            .build();

    private CreateSessionMetricCollectorTool() {
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
                try {
                    final Session session =
                        sessionManager.get(exchange.sessionId());
                    if (session == null) {
                        return noActiveSession();
                    }

                    return createSessionMetricCollector(
                        session,
                        buildSessionMetricCollector(request.arguments()));
                }
                catch (Exception e) {
                    return monoToolError("Invalid arguments: %s",
                        e.getMessage());
                }
            })
            .build();
    }

    private static SessionMetricCollector buildSessionMetricCollector(
        Map<String, Object> arguments) {

        SessionMetricCollector.Builder builder =
            Diffusion.newSessionMetricCollectorBuilder();

        // Optional parameters
        if (argumentIsTrue(arguments, EXPORT_TO_PROMETHEUS)) {
            builder = builder.exportToPrometheus(true);
        }

        final Integer maximumGroups =
            intArgument(arguments, MAXIMUM_GROUPS);
        if (maximumGroups != null) {
            builder = builder.maximumGroups(maximumGroups);
        }

        if (argumentIsTrue(arguments, REMOVE_METRICS_WITH_NO_MATCHES)) {
            builder = builder.removeMetricsWithNoMatches(true);
        }

        final String groupByProperties =
            (String) arguments.get(GROUP_BY_PROPERTIES);
        if (groupByProperties != null) {
            final List<String> properties =
                Arrays.stream(groupByProperties.split(","))
                    .map(String::trim)
                    .filter(prop -> !prop.isEmpty())
                    .toList();

            if (!properties.isEmpty()) {
                builder = builder.groupByProperties(properties);
            }
        }

        return builder.create(
            stringArgument(arguments, COLLECTOR_NAME),
            stringArgument(arguments, SESSION_FILTER));
    }

    private static Mono<CallToolResult> createSessionMetricCollector(
        Session session,
        SessionMetricCollector collector) {

        LOG.info("Creating session metric collector: {}", collector);

        final String name = collector.getName();

        try {
            return Mono
                .fromFuture(session.feature(Metrics.class)
                    .putSessionMetricCollector(collector))
                .timeout(TEN_SECONDS)
                .doOnSuccess(result -> LOG.info(
                    "Session metric collector created successfully: {}", name))
                .then(Mono.fromCallable(() -> formatCreationResult(collector)))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex -> monoToolException(
                    toolOperation(TOOL_NAME, name), ex, LOG));
        }
        catch (Exception ex) {
            return monoToolException(toolOperation(TOOL_NAME, name), ex, LOG);
        }
    }

    private static CallToolResult formatCreationResult(
        SessionMetricCollector collector) {

        final ToolResponse response = new ToolResponse()
            .addLine("Successfully created session metric collector:")
            .addLine("  Name: %s", collector.getName())
            .addLine("  Session Filter: %s", collector.getSessionFilter())
            .addLine("  Exports to Prometheus: %s",
                collector.exportsToPrometheus())
            .addLine("  Maximum Groups: %s",
                collector.maximumGroups() > 0 ? collector.maximumGroups()
                    : "unlimited")
            .addLine("  Removes Metrics with No Matches: %s",
                collector.removesMetricsWithNoMatches());

        if (!collector.getGroupByProperties().isEmpty()) {
            response.addLine("  Group By Properties: %s",
                String.join(", ", collector.getGroupByProperties()));
        }

        response.addLine(
            "The collector is now active and collecting metrics for matching sessions.");

        return toolResult(response);
    }
}