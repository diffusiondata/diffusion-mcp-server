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
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricAlert;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * List metric alerts tool.
 *
 * @author DiffusionData Limited
 */
final class ListMetricAlertsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListMetricAlertsTool.class);

    static final String TOOL_NAME = "list_metric_alerts";

    private static final String TOOL_DESCRIPTION =
        "Lists all metric alerts configured on the server. " +
            "Needs VIEW_SERVER permission. " +
            "See " + METRICS + " context for more information about metric alerts.";

    private ListMetricAlertsTool() {
    }

    /**
     * List Metric Alerts Tool
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

                LOG.info("Listing metric alerts");

                return Mono
                    .fromFuture(
                        session.feature(Metrics.class)
                            .listMetricAlerts())
                    .timeout(TEN_SECONDS)
                    .doOnNext(alerts -> LOG.debug(
                        "Retrieved {} metric alerts",
                        alerts.size()))
                    .map(ListMetricAlertsTool::formatMetricAlerts)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    /**
     * Format metric alerts for output
     */
    private static CallToolResult formatMetricAlerts(
        List<MetricAlert> alerts) {

        try {
            final ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("count", alerts.size());

            if (alerts.isEmpty()) {
                response.put("message",
                    "No metric alerts configured");
            }
            else {
                final ArrayNode alertsArray =
                    response.putArray("alerts");

                for (MetricAlert alert : alerts) {
                    final ObjectNode alertNode =
                        alertsArray.addObject();
                    alertNode.put(
                        "name",
                        alert.getName());
                    alertNode.put(
                        "specification",
                        alert.getSpecification());
                    alertNode.put(
                        "principal",
                        alert.getPrincipal());
                }
            }

            return toolResult(response.toString());

        }
        catch (Exception ex) {
            LOG.error("Error formatting metric alerts", ex);
            return toolResult(
                "Error formatting metric alerts: " +
                    ex.getMessage());
        }
    }

}
