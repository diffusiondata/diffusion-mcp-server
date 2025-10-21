/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.prompts.ContextGuides.REMOTE_SERVERS;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
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
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.PrimaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryAcceptor;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryServer;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool to list all remote servers on the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class ListRemoteServersTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListRemoteServersTool.class);

    static final String TOOL_NAME = "list_remote_servers";

    private static final String TOOL_DESCRIPTION =
        "Lists all remote server configurations on the connected Diffusion server. " +
            "Returns details about each remote server including type, name, and configuration. " +
            "Needs VIEW_SERVER permission." +
            "See the " + REMOTE_SERVERS +
            " context for more information about working with remote servers.";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .additionalProperties(false)
            .build();

    private ListRemoteServersTool() {
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

                LOG.info("Listing remote servers");

                return Mono
                    .fromFuture(
                        session.feature(RemoteServers.class)
                            .listRemoteServers())
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.info(
                        "Successfully listed {} remote servers",
                        result.size()))
                    .map(servers -> buildResponse(servers))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult buildResponse(List<RemoteServer> servers) {

        final ToolResponse response = new ToolResponse()
            .addLine("=== Remote Servers ===")
            .addLine("Total: %d", servers.size())
            .addLine();

        if (servers.isEmpty()) {
            response.addLine("No remote servers configured.");
        }
        else {
            for (int i = 0; i < servers.size(); i++) {
                final RemoteServer server = servers.get(i);
                response.addLine("%d. %s", i + 1, server.getName());
                response.addLine("   Type: %s", server.getType());

                switch (server.getType()) {
                    case SECONDARY_INITIATOR:
                        addSecondaryInitiatorDetails(
                            response,
                            (SecondaryInitiator) server);
                        break;
                    case PRIMARY_INITIATOR:
                        addPrimaryInitiatorDetails(
                            response,
                            (PrimaryInitiator) server);
                        break;
                    case SECONDARY_ACCEPTOR:
                        addSecondaryAcceptorDetails(
                            response,
                            (SecondaryAcceptor) server);
                        break;
                }

                if (i < servers.size() - 1) {
                    response.addLine();
                }
            }
        }

        return toolResult(response);
    }

    private static void addSecondaryInitiatorDetails(
        ToolResponse response,
        SecondaryInitiator server) {

        response.addLine("   URL: %s", server.getUrl());
        addSecondaryServerCommonDetails(response, server);
    }

    private static void addPrimaryInitiatorDetails(
        ToolResponse response,
        PrimaryInitiator server) {

        response.addLine("   URLs: %s", String.join(", ", server.getUrls()));
        response.addLine("   Connector: %s", server.getConnector());
        response.addLine("   Retry Delay: %d ms", server.getRetryDelay());
    }

    private static void addSecondaryAcceptorDetails(
        ToolResponse response,
        SecondaryAcceptor server) {

        response.addLine(
            "   Primary Host Name: %s",
            server.getPrimaryHostName());
        addSecondaryServerCommonDetails(response, server);
    }

    private static void addSecondaryServerCommonDetails(
        ToolResponse response,
        SecondaryServer server) {

        final String principal = server.getPrincipal();
        response.addLine("   Principal: %s",
            principal != null && !principal.isEmpty() ?
                principal :
                    "<anonymous>");

        final String filter = server.getMissingTopicNotificationFilter();
        if (filter != null) {
            response.addLine("   Missing Topic Filter: %s", filter);
        }
    }
}
