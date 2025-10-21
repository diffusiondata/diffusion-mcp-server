/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static com.diffusion.mcp.tools.ToolUtils.EMPTY_INPUT_SCHEMA;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Disconnect tool.
 * <p>
 * Closes the current Diffusion session if there is one.
 *
 * @author DiffusionData Limited
 */
final class DisconnectTool {

    static final String TOOL_NAME = "disconnect";

    private static final String TOOL_DESCRIPTION =
        "Disconnects from the current Diffusion server session if one exists.";

    private DisconnectTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(EMPTY_INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                try {
                    final Session session =
                        sessionManager.disconnect(exchange.sessionId());

                    if (session == null) {
                        return toolResult(
                            "No active Diffusion session to disconnect");
                    }
                    else {
                        return toolResult(
                            "Successfully disconnected from Diffusion server");
                    }
                }
                catch (Exception e) {
                    return toolError(
                        "Error disconnecting from Diffusion server: %s",
                        e.getMessage());
                }
            }))
            .build();
    }
}