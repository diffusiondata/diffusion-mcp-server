/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.prompts.ContextGuides.METRICS;
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

import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool for removing metric alerts.
 *
 * @author DiffusionData Limited
 */
final class RemoveMetricAlertTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveMetricAlertTool.class);

    static final String TOOL_NAME = "remove_metric_alert";

    private static final String TOOL_DESCRIPTION =
        "Removes a metric alert with the specified name. " +
            "Needs CONTROL_SERVER permission. " +
            "If the alert does not exist, the operation completes successfully. " +
            "See " + METRICS + " context for more information about metric alerts.";

    /*
     * Parameters.
     */
    private static final String ALERT_NAME = "name";

    /*
     * Input.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ALERT_NAME,
                stringProperty("Name of the metric alert to remove"))
            .required(ALERT_NAME)
            .additionalProperties(false)
            .build();

    private RemoveMetricAlertTool() {
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

                    final String name =
                        stringArgument(request.arguments(), ALERT_NAME);
                    return removeMetricAlert(session, name);
                }
                catch (Exception e) {
                    return monoToolError("Invalid arguments: %s",
                        e.getMessage());
                }
            })
            .build();
    }

    private static Mono<CallToolResult> removeMetricAlert(
        Session session,
        String name) {

        LOG.info("Removing metric alert: {}", name);

        try {
            return Mono
                .fromFuture(session.feature(Metrics.class)
                    .removeMetricAlert(name))
                .timeout(TEN_SECONDS)
                .doOnSuccess(result -> LOG.info(
                    "Metric alert removed successfully: {}", name))
                .then(Mono.fromCallable(() -> formatRemoveResult(name)))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex -> monoToolException(
                    toolOperation(TOOL_NAME, name), ex, LOG));
        }
        catch (Exception ex) {
            return monoToolException(toolOperation(TOOL_NAME, name), ex, LOG);
        }
    }

    private static CallToolResult formatRemoveResult(String name) {

        final ToolResponse response = new ToolResponse()
            .addLine("Successfully removed metric alert: %s", name)
            .addLine("")
            .addLine("The alert has been removed and will no longer trigger.");

        return toolResult(response);
    }
}
