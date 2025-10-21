/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.prompts.ContextGuides.REMOTE_SERVERS;
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
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool to remove a remote server from the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class RemoveRemoteServerTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveRemoteServerTool.class);

    static final String TOOL_NAME = "remove_remote_server";

    private static final String TOOL_DESCRIPTION =
        "Removes a remote server configuration from the connected Diffusion server. " +
            "If the remote server does not exist, the operation completes successfully. " +
            "Any topic views that depend on the removed remote server will be disabled. " +
            "Needs CONTROL_SERVER permission. " +
            "See the " + REMOTE_SERVERS +
            " context for more information about working with remote servers.";

    private static final String NAME = "name";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                NAME,
                stringProperty("The name of the remote server to remove"))
            .required(NAME)
            .additionalProperties(false)
            .build();

    private RemoveRemoteServerTool() {
    }

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

                final String name = stringArgument(request.arguments(), NAME);

                LOG.info("Removing remote server: {}", name);

                return Mono
                    .fromFuture(
                        session.feature(RemoteServers.class)
                            .removeRemoteServer(name))
                    .timeout(TEN_SECONDS)
                    .doOnSuccess(v -> LOG.info(
                        "Successfully removed remote server: {}",
                        name))
                    .thenReturn(buildResponse(name))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult buildResponse(String name) {
        final ToolResponse response = new ToolResponse()
            .addLine("=== Remote Server Removed ===")
            .addLine("Name: %s", name)
            .addLine()
            .addLine("The remote server has been successfully removed.")
            .addLine("Any topic views depending on this remote server will be disabled.");

        return toolResult(response);
    }
}
