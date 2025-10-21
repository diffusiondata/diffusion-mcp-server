/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.prompts.ContextGuides.METRICS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_SELECTORS;
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

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.TopicMetricCollector;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.TopicSelector;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool for creating topic metric collectors.
 *
 * @author DiffusionData Limited
 */
final class CreateTopicMetricCollectorTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(CreateTopicMetricCollectorTool.class);

    static final String TOOL_NAME = "create_topic_metric_collector";

    private static final String TOOL_DESCRIPTION =
        "Creates a new topic metric collector with specified configuration. " +
            "Needs CONTROL_SERVER permission. " +
            "Consult " + METRICS +
            " context before creating metric collectors. " +
            "Also see the " + TOPIC_SELECTORS +
            " context to understand how to specify topic selectors.";

    /*
     * Parameters.
     */
    private static final String COLLECTOR_NAME = "name";
    private static final String TOPIC_SELECTOR = "topicSelector";
    private static final String EXPORT_TO_PROMETHEUS = "exportToPrometheus";
    private static final String MAXIMUM_GROUPS = "maximumGroups";
    private static final String GROUP_BY_TOPIC_TYPE = "groupByTopicType";
    private static final String GROUP_BY_TOPIC_VIEW = "groupByTopicView";
    private static final String GROUP_BY_PATH_PREFIX_PARTS =
        "groupByPathPrefixParts";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                COLLECTOR_NAME,
                stringProperty("Unique name for the metric collector"))
            .property(
                TOPIC_SELECTOR,
                stringProperty(
                    "Topic selector to specify which topics to collect metrics for " +
                        "(e.g., '?sensors//', 'games/*/scores')"))
            .property(
                EXPORT_TO_PROMETHEUS,
                boolProperty(
                    "Whether to export metrics to Prometheus endpoint (default: false)"))
            .property(
                MAXIMUM_GROUPS,
                intProperty(
                    "Maximum number of groups to maintain (optional, default: unlimited)"))
            .property(
                GROUP_BY_TOPIC_TYPE,
                boolProperty(
                    "Whether to group metrics by topic type (default: false)"))
            .property(
                GROUP_BY_TOPIC_VIEW,
                boolProperty(
                    "Whether to group metrics by topic view (default: false)"))
            .property(
                GROUP_BY_PATH_PREFIX_PARTS,
                intProperty(
                    "Number of leading path parts to group by (default is no grouping)",
                    1, null, null))
            .required(COLLECTOR_NAME, TOPIC_SELECTOR)
            .additionalProperties(false)
            .build();

    private CreateTopicMetricCollectorTool() {
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
                    // Check session
                    final Session session =
                        sessionManager.get(exchange.sessionId());
                    if (session == null) {
                        return noActiveSession();
                    }

                    return createTopicMetricCollector(
                        session,
                        buildTopicMetricCollector(request.arguments()));
                }
                catch (Exception ex) {
                    return monoToolError(
                        "Invalid arguments: %s",
                        ex.getMessage());
                }

            })
            .build();
    }

    private static TopicMetricCollector buildTopicMetricCollector(
        Map<String, Object> arguments) {

        TopicMetricCollector.Builder builder =
            Diffusion.newTopicMetricCollectorBuilder();

        // Extract optional parameters and add to builder
        if (argumentIsTrue(arguments, EXPORT_TO_PROMETHEUS)) {
            builder = builder.exportToPrometheus(true);
        }

        final Integer maximumGroups =
            intArgument(arguments, MAXIMUM_GROUPS);
        if (maximumGroups != null) {
            builder = builder.maximumGroups(maximumGroups);
        }

        if (argumentIsTrue(arguments, GROUP_BY_TOPIC_TYPE)) {
            builder = builder.groupByTopicType(true);
        }

        if (argumentIsTrue(arguments, GROUP_BY_TOPIC_VIEW)) {
            builder = builder.groupByTopicView(true);
        }

        final Integer groupByPathPrefixParts =
            intArgument(arguments, GROUP_BY_PATH_PREFIX_PARTS);
        if (groupByPathPrefixParts != null) {
            builder = builder.groupByPathPrefixParts(groupByPathPrefixParts);
        }

        final TopicSelector selector =
            Diffusion.topicSelectors().parse(
                stringArgument(arguments, TOPIC_SELECTOR));

        return builder.create(
            stringArgument(arguments, COLLECTOR_NAME),
            selector.getExpression());
    }

    private static Mono<CallToolResult> createTopicMetricCollector(
        Session session,
        TopicMetricCollector collector) {

        LOG.info("Creating topic metric collector: {}", collector);
        final String name = collector.getName();

        try {
            return Mono
                .fromFuture(session.feature(Metrics.class)
                    .putTopicMetricCollector(collector))
                .timeout(TEN_SECONDS)
                .doOnSuccess(result -> LOG.info(
                    "Topic metric collector created successfully: {}", name))
                .then(Mono.fromCallable(() -> formatCreationResult(collector)))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex ->
                    monoToolException(toolOperation(TOOL_NAME, name), ex, LOG));
        }
        catch (Exception ex) {
            return monoToolException(toolOperation(TOOL_NAME, name), ex, LOG);
        }
    }

    private static CallToolResult formatCreationResult(
        TopicMetricCollector collector) {

        final ToolResponse response = new ToolResponse()
            .addLine("Successfully created topic metric collector:")
            .addLine("  Name: %s", collector.getName())
            .addLine("  Topic Selector: %s", collector.getTopicSelector())
            .addLine("  Exports to Prometheus: %s",
                collector.exportsToPrometheus())
            .addLine("  Maximum Groups: %s",
                collector.maximumGroups() > 0 ? collector.maximumGroups()
                    : "unlimited")
            .addLine("  Groups by Topic Type: %s",
                collector.groupsByTopicType())
            .addLine("  Groups by Topic View: %s",
                collector.groupsByTopicView());

        if (collector.groupByPathPrefixParts() > 0) {
            response.addLine("  Groups by Path Prefix Parts: %d",
                collector.groupByPathPrefixParts());
        }

        response.addLine(
            "The collector is now active and collecting metrics for matching topics.");

        return toolResult(response);
    }
}