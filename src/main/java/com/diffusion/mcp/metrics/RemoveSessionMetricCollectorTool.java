/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
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
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Remove session metric collector tool.
 *
 * @author DiffusionData Limited
 */
final class RemoveSessionMetricCollectorTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveSessionMetricCollectorTool.class);

    static final String TOOL_NAME = "remove_session_metric_collector";

    private static final String TOOL_DESCRIPTION =
        "Removes a session metric collector by name. Needs CONTROL_SERVER permission.";

    private static final String COLLECTOR_NAME = "name";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                COLLECTOR_NAME,
                stringProperty("Name of the session metric collector to remove"))
            .required(COLLECTOR_NAME)
            .additionalProperties(false)
            .build();

    private RemoveSessionMetricCollectorTool() {
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

                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final String name =
                    stringArgument(request.arguments(), COLLECTOR_NAME);

                LOG.info("Removing session metric collector: {}", name);

                return Mono
                    .fromFuture(
                        session.feature(Metrics.class)
                            .removeSessionMetricCollector(name))
                    .timeout(TEN_SECONDS)
                    .doOnSuccess(result -> LOG.debug(
                        "Session metric collector removed successfully: {}",
                        name))
                    .then(Mono.fromCallable(() -> toolResult(
                        "Successfully removed session metric collector: %s",
                        name)))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(
                        ex -> monoToolException(toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }
}
