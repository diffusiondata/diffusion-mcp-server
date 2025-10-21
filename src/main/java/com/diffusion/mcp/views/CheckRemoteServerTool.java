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
import com.pushtechnology.diffusion.client.features.control.RemoteServers.CheckRemoteServerResult;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.CheckRemoteServerResult.ConnectionState;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool to check the current state of a remote server on the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class CheckRemoteServerTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(CheckRemoteServerTool.class);

    static final String TOOL_NAME = "check_remote_server";

    private static final String TOOL_DESCRIPTION =
        "Checks the current state of a named remote server on the connected Diffusion server. " +
            "Returns the connection state (INACTIVE, CONNECTED, RETRYING, FAILED, or MISSING) " +
            "and any failure message if applicable. For SECONDARY_INITIATOR remote servers in " +
            "INACTIVE or FAILED state, this operation will attempt a test connection and can be " +
            "used to forcibly retry a failed connection. " +
            "Needs CONTROL_SERVER permission. " +
            "See the " + REMOTE_SERVERS +
            " context for more information about working with remote servers.";

    private static final String NAME = "name";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                NAME,
                stringProperty("The name of the remote server to check"))
            .required(NAME)
            .additionalProperties(false)
            .build();

    private CheckRemoteServerTool() {
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

                LOG.info("Checking remote server: {}", name);

                return Mono
                    .fromFuture(
                        session.feature(RemoteServers.class)
                            .checkRemoteServer(name))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.info(
                        "Successfully checked remote server: {} - state: {}",
                        name,
                        result.getConnectionState()))
                    .map(result -> buildResponse(name, result))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult buildResponse(
        String name,
        CheckRemoteServerResult result) {

        final ToolResponse response = new ToolResponse()
            .addLine("=== Remote Server Status ===")
            .addLine("Name: %s", name)
            .addLine("Connection State: %s", result.getConnectionState())
            .addLine();

        final ConnectionState state = result.getConnectionState();
        final String failureMessage = result.getFailureMessage();

        // Add state-specific information
        switch (state) {
            case INACTIVE:
                response.addLine("The remote server is currently inactive.");
                response.addLine("For SECONDARY_INITIATOR, this may indicate that no components");
                response.addLine("are currently using the remote server, or a test connection");
                response.addLine("was successful but then closed.");
                break;

            case CONNECTED:
                response.addLine("The remote server is successfully connected and operational.");
                break;

            case RETRYING:
                response.addLine("The connection has failed and is currently retrying.");
                if (failureMessage != null && !failureMessage.isEmpty()) {
                    response.addLine()
                        .addLine("Failure reason:")
                        .addLine("  %s", failureMessage);
                }
                break;

            case FAILED:
                response.addLine("The connection has failed.");
                if (failureMessage != null && !failureMessage.isEmpty()) {
                    response.addLine()
                        .addLine("Failure reason:")
                        .addLine("  %s", failureMessage);
                }
                response.addLine()
                    .addLine("You can retry the connection by running check_remote_server again.");
                break;

            case MISSING:
                response.addLine("The named remote server does not exist.");
                response.addLine("Use list_remote_servers to see all configured remote servers.");
                break;
        }

        // Add note about current server scope
        if (state != ConnectionState.MISSING) {
            response.addLine()
                .addLine("Note: This status reflects the state on the server you are connected to.")
                .addLine("In a cluster, other servers may have different states.");
        }

        return toolResult(response);
    }
}
