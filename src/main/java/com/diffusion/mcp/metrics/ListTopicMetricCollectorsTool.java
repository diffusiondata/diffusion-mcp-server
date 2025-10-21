/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.METRICS;
import static com.diffusion.mcp.tools.ToolUtils.EMPTY_INPUT_SCHEMA;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.TopicMetricCollector;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * List topic metric collectors tool.
 *
 * @author DiffusionData Limited
 */
final class ListTopicMetricCollectorsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListTopicMetricCollectorsTool.class);

    static final String TOOL_NAME = "list_topic_metric_collectors";

    private static final String TOOL_DESCRIPTION =
        "Lists all topic metric collectors configured on the server. " +
            "Needs VIEW_SERVER permission. " +
            "See " + METRICS + " context for more information about metrics.";

    private ListTopicMetricCollectorsTool() {
    }

    /**
     * List Topic Metric Collectors Tool
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(EMPTY_INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                final Session session =
                    sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                LOG.info("Listing topic metric collectors");

                return Mono
                    .fromFuture(
                        session.feature(Metrics.class)
                            .listTopicMetricCollectors())
                    .timeout(TEN_SECONDS)
                    .doOnNext(collectors -> LOG.debug(
                        "Retrieved {} topic metric collectors",
                        collectors.size()))
                    .map(ListTopicMetricCollectorsTool::formatTopicCollectors)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    /**
     * Format topic metric collectors for output
     */
    private static CallToolResult formatTopicCollectors(
        List<TopicMetricCollector> collectors) {

        try {
            final ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("collectorType", "topic");
            response.put("count", collectors.size());

            if (collectors.isEmpty()) {
                response.put(
                    "message",
                    "No topic metric collectors configured");
            }
            else {
                final ArrayNode collectorsArray =
                    response.putArray("collectors");

                for (TopicMetricCollector collector : collectors) {
                    final ObjectNode collectorNode =
                        collectorsArray.addObject();
                    collectorNode.put(
                        "name",
                        collector.getName());
                    collectorNode.put(
                        "topicSelector",
                        collector.getTopicSelector());
                    collectorNode.put(
                        "exportsToPrometheus",
                        collector.exportsToPrometheus());
                    collectorNode.put(
                        "maximumGroups",
                        collector.maximumGroups());
                    collectorNode.put(
                        "groupsByTopicType",
                        collector.groupsByTopicType());
                    collectorNode.put(
                        "groupsByTopicView",
                        collector.groupsByTopicView());
                    collectorNode.put(
                        "groupByPathPrefixParts",
                        collector.groupByPathPrefixParts());
                }
            }

            return toolResult(response.toString());

        }
        catch (Exception ex) {
            LOG.error("Error formatting topic metric collectors", ex);
            return toolResult(
                "Error formatting topic metric collectors: " + ex.getMessage());
        }
    }

}
