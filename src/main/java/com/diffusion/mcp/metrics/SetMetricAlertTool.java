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

import java.util.Map;
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
 * Tool for setting or updating metric alerts.
 *
 * @author DiffusionData Limited
 */
final class SetMetricAlertTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetMetricAlertTool.class);

    static final String TOOL_NAME = "set_metric_alert";

    private static final String TOOL_DESCRIPTION =
        "Creates or updates a metric alert with specified configuration. " +
            "Needs CONTROL_SERVER permission. " +
            "Consult " + METRICS +
            " context before attempting to create metric alerts. " +
            "Use fetch_metrics tool to determine what metrics are available to use. " +
            "Metric alerts use a DSL to define conditions for triggering notifications.";

    /*
     * Parameters.
     */
    private static final String ALERT_NAME = "name";
    private static final String SPECIFICATION = "specification";

    /*
     * Input.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ALERT_NAME,
                stringProperty("Unique name for the metric alert"))
            .property(
                SPECIFICATION,
                stringProperty(
                    "The metric alert specification using the DSL format. " +
                        "Consult " + METRICS +
                        " context for detailed description of DSL."))
            .required(ALERT_NAME, SPECIFICATION)
            .additionalProperties(false)
            .build();

    private SetMetricAlertTool() {
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

                    final Map<String, Object> arguments = request.arguments();
                    return setMetricAlert(
                        session,
                        stringArgument(arguments, ALERT_NAME),
                        stringArgument(arguments, SPECIFICATION));
                }
                catch (Exception e) {
                    return monoToolError("Invalid arguments: %s",
                        e.getMessage());
                }
            })
            .build();
    }

    private static Mono<CallToolResult> setMetricAlert(
        Session session,
        String name,
        String specification) {

        LOG.info("Setting metric alert: {}", name);

        try {
            return Mono
                .fromFuture(session.feature(Metrics.class)
                    .setMetricAlert(name, specification))
                .timeout(TEN_SECONDS)
                .doOnSuccess(result -> LOG.info(
                    "Metric alert set successfully: {}", name))
                .then(Mono.fromCallable(() -> formatSetResult(name, specification)))
                .onErrorMap(TimeoutException.class, e -> timex())
                .onErrorResume(ex -> monoToolException(
                    toolOperation(TOOL_NAME, name), ex, LOG));
        }
        catch (Exception ex) {
            return monoToolException(toolOperation(TOOL_NAME, name), ex, LOG);
        }
    }

    private static CallToolResult formatSetResult(
        String name,
        String specification) {

        final ToolResponse response = new ToolResponse()
            .addLine("Successfully set metric alert:")
            .addLine("  Name: %s", name)
            .addLine("  Specification: %s", specification)
            .addLine("")
            .addLine(
                "The alert is now active and will trigger when conditions are met.");

        return toolResult(response);
    }
}
