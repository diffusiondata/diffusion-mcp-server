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
import com.pushtechnology.diffusion.client.features.control.Metrics.SessionMetricCollector;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * List session metric collectors tool.
 *
 * @author DiffusionData Limited
 */
final class ListSessionMetricCollectorsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListSessionMetricCollectorsTool.class);

    static final String TOOL_NAME = "list_session_metric_collectors";

    private static final String TOOL_DESCRIPTION =
        "Lists all session metric collectors configured on the server. " +
            "Needs VIEW_SERVER permission. " +
            "See " + METRICS + " context for more information about metrics.";

    private ListSessionMetricCollectorsTool() {
    }

    /**
     * List Session Metric Collectors Tool
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

                LOG.info("Listing session metric collectors");

                return Mono
                    .fromFuture(
                        session.feature(Metrics.class)
                            .listSessionMetricCollectors())
                    .timeout(TEN_SECONDS)
                    .doOnNext(collectors -> LOG.debug(
                        "Retrieved {} session metric collectors",
                        collectors.size()))
                    .map(
                        ListSessionMetricCollectorsTool::formatSessionCollectors)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    /**
     * Format session metric collectors for output
     */
    private static CallToolResult formatSessionCollectors(
        List<SessionMetricCollector> collectors) {

        try {
            final ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("collectorType", "session");
            response.put("count", collectors.size());

            if (collectors.isEmpty()) {
                response.put("message",
                    "No session metric collectors configured");
            }
            else {
                final ArrayNode collectorsArray =
                    response.putArray("collectors");

                for (SessionMetricCollector collector : collectors) {
                    final ObjectNode collectorNode =
                        collectorsArray.addObject();
                    collectorNode.put(
                        "name",
                        collector.getName());
                    collectorNode.put(
                        "sessionFilter",
                        collector.getSessionFilter());
                    collectorNode.put(
                        "exportsToPrometheus",
                        collector.exportsToPrometheus());
                    collectorNode.put(
                        "maximumGroups",
                        collector.maximumGroups());
                    collectorNode.put(
                        "removesMetricsWithNoMatches",
                        collector.removesMetricsWithNoMatches());

                    final List<String> groupByProperties =
                        collector.getGroupByProperties();
                    if (!groupByProperties.isEmpty()) {
                        final ArrayNode propertiesArray =
                            collectorNode.putArray("groupByProperties");
                        groupByProperties.forEach(propertiesArray::add);
                    }
                }
            }

            return toolResult(response.toString());

        }
        catch (Exception ex) {
            LOG.error("Error formatting session metric collectors", ex);
            return toolResult(
                "Error formatting session metric collectors: " +
                    ex.getMessage());
        }
    }

}
