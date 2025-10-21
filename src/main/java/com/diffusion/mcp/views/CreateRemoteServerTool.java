/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.prompts.ContextGuides.REMOTE_SERVERS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS_ADVANCED;
import static com.diffusion.mcp.tools.JsonSchemas.arrayProperty;
import static com.diffusion.mcp.tools.JsonSchemas.enumProperty;
import static com.diffusion.mcp.tools.JsonSchemas.intProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.objectProperty;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.intArgument;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;
import static com.pushtechnology.diffusion.client.Diffusion.credentials;
import static com.pushtechnology.diffusion.client.Diffusion.newRemoteServerBuilder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolResponse;
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.PrimaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.PrimaryInitiator.PrimaryInitiatorBuilder;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer.ConnectionOption;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryAcceptor;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryAcceptor.SecondaryAcceptorBuilder;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryInitiator.SecondaryInitiatorBuilder;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryServer;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryServer.SecondaryBuilder;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Tool to create a remote server on the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class CreateRemoteServerTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(CreateRemoteServerTool.class);

    static final String TOOL_NAME = "create_remote_server";

    private static final String TOOL_DESCRIPTION =
        "Creates a remote server configuration on the connected Diffusion server. " +
            "Supports three types: SECONDARY_INITIATOR (secondary connects to primary), " +
            "PRIMARY_INITIATOR (primary connects to secondary), and SECONDARY_ACCEPTOR " +
            "(accepts connection from primary). " +
            "Needs CONTROL_SERVER permission. " +
            "See the " + TOPIC_VIEWS_ADVANCED +
            "context for information about using remote servers in topic views and " +
            "the " + REMOTE_SERVERS +
            " context to understand how to configure remote servers.";

    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String URL = "url";
    private static final String URLS = "urls";
    private static final String CONNECTOR = "connector";
    private static final String PRIMARY_HOST_NAME = "primaryHostName";
    private static final String PRINCIPAL = "principal";
    private static final String PASSWORD = "password";
    private static final String CONNECTION_OPTIONS = "connectionOptions";
    private static final String MISSING_TOPIC_NOTIFICATION_FILTER =
        "missingTopicNotificationFilter";
    private static final String RETRY_DELAY = "retryDelay";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TYPE,
                enumProperty(
                    "The type of remote server to create",
                    List.of(
                        "SECONDARY_INITIATOR",
                        "PRIMARY_INITIATOR",
                        "SECONDARY_ACCEPTOR")))
            .property(
                NAME,
                stringProperty("The name of the remote server"))
            .property(
                URL,
                stringProperty(
                    "The URL to connect to the primary server " +
                    "(required for SECONDARY_INITIATOR)"))
            .property(
                URLS,
                arrayProperty(
                    stringProperty("Secondary server URL"),
                    1,
                    null,
                    null,
                    "List of URLs to connect to secondary servers " +
                    "(required for PRIMARY_INITIATOR)"))
            .property(
                CONNECTOR,
                stringProperty(
                    "The connector name for establishing connections " +
                    "(required for PRIMARY_INITIATOR)"))
            .property(
                PRIMARY_HOST_NAME,
                stringProperty(
                    "The primary server host name for SSL validation " +
                    "(required for SECONDARY_ACCEPTOR)"))
            .property(
                PRINCIPAL,
                stringProperty(
                    "The principal name for authentication. " +
                    "Empty string indicates anonymous connection. " +
                    "(optional for SECONDARY_INITIATOR and SECONDARY_ACCEPTOR)"))
            .property(
                PASSWORD,
                stringProperty(
                    "The password for authentication " +
                    "(optional for SECONDARY_INITIATOR and SECONDARY_ACCEPTOR)"))
            .property(
                CONNECTION_OPTIONS,
                objectProperty(
                    null,
                    null,
                    null,
                    "Map of connection options (e.g., RECONNECTION_TIMEOUT, " +
                    "RETRY_DELAY, INPUT_BUFFER_SIZE, etc.) " +
                    "(optional for SECONDARY_INITIATOR and SECONDARY_ACCEPTOR)"))
            .property(
                MISSING_TOPIC_NOTIFICATION_FILTER,
                stringProperty(
                    "Topic selector expression to filter missing topic notifications. " +
                    "Use '*.*' to propagate all notifications " +
                    "(optional for SECONDARY_INITIATOR and SECONDARY_ACCEPTOR)"))
            .property(
                RETRY_DELAY,
                intProperty(
                    "Delay in milliseconds between connection retries. " +
                    "Default is 1000 (1 second) " +
                    "(optional for PRIMARY_INITIATOR)",
                    1,
                    null,
                    1000))
            .required(TYPE, NAME)
            .additionalProperties(false)
            .build();

    private CreateRemoteServerTool() {
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

                final Map<String, Object> arguments = request.arguments();
                final String type = stringArgument(arguments, TYPE);
                final String name = stringArgument(arguments, NAME);

                LOG.info("Creating remote server: type={}, name={}", type, name);

                try {
                    final RemoteServer remoteServer =
                        buildRemoteServer(type, name, arguments);

                    return Mono
                        .fromFuture(
                            session.feature(RemoteServers.class)
                                .createRemoteServer(remoteServer))
                        .timeout(TEN_SECONDS)
                        .doOnNext(result -> LOG.info(
                            "Successfully created remote server: {}",
                            name))
                        .map(result -> buildResponse(result))
                        .onErrorMap(TimeoutException.class, e -> timex())
                        .onErrorResume(ex -> monoToolException(
                            toolOperation(TOOL_NAME, name), ex, LOG));
                }
                catch (IllegalArgumentException e) {
                    return monoToolError("Invalid parameters: %s", e.getMessage());
                }
            })
            .build();
    }

    private static RemoteServer buildRemoteServer(
        String type,
        String name,
        Map<String, Object> arguments) {

        return switch (type) {
            case "SECONDARY_INITIATOR" ->
                buildSecondaryInitiator(name, arguments);
            case "PRIMARY_INITIATOR" ->
                buildPrimaryInitiator(name, arguments);
            case "SECONDARY_ACCEPTOR" ->
                buildSecondaryAcceptor(name, arguments);
            default -> throw new IllegalArgumentException(
                "Invalid remote server type: " + type);
        };
    }

    private static SecondaryInitiator buildSecondaryInitiator(
        String name,
        Map<String, Object> arguments) {

        final String url = stringArgument(arguments, URL);
        if (url == null) {
            throw new IllegalArgumentException(
                "URL is required for SECONDARY_INITIATOR");
        }

        var builder = newRemoteServerBuilder(SecondaryInitiatorBuilder.class);

        applySecondaryServerOptions(builder, arguments);

        return builder.build(name, url);
    }

    private static PrimaryInitiator buildPrimaryInitiator(
        String name,
        Map<String, Object> arguments) {

        @SuppressWarnings("unchecked")
        final List<String> urls = (List<String>) arguments.get(URLS);
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException(
                "URLs list is required for PRIMARY_INITIATOR");
        }

        final String connector = stringArgument(arguments, CONNECTOR);
        if (connector == null) {
            throw new IllegalArgumentException(
                "Connector is required for PRIMARY_INITIATOR");
        }

        var builder = newRemoteServerBuilder(PrimaryInitiatorBuilder.class);

        final Integer retryDelay = intArgument(arguments, RETRY_DELAY);
        if (retryDelay != null) {
            builder.retryDelay(retryDelay);
        }

        return builder.build(name, urls, connector);
    }

    private static SecondaryAcceptor buildSecondaryAcceptor(
        String name,
        Map<String, Object> arguments) {

        final String primaryHostName =
            stringArgument(arguments, PRIMARY_HOST_NAME);
        if (primaryHostName == null) {
            throw new IllegalArgumentException(
                "primaryHostName is required for SECONDARY_ACCEPTOR");
        }

        var builder = newRemoteServerBuilder(SecondaryAcceptorBuilder.class);

        applySecondaryServerOptions(builder, arguments);

        return builder.build(name, primaryHostName);
    }

    private static <B extends SecondaryBuilder<B>>
        void applySecondaryServerOptions(
            B builder,
            Map<String, Object> arguments) {

        final String principal = stringArgument(arguments, PRINCIPAL);
        if (principal != null) {
            builder.principal(principal);
        }

        final String password = stringArgument(arguments, PASSWORD);
        if (password != null) {
            builder.credentials(credentials().password(password));
        }

        @SuppressWarnings("unchecked")
        final Map<String, String> connectionOptions =
            (Map<String, String>) arguments.get(CONNECTION_OPTIONS);
        if (connectionOptions != null && !connectionOptions.isEmpty()) {
            final Map<ConnectionOption, String> options =
                new EnumMap<>(ConnectionOption.class);
            connectionOptions.forEach((key, value) -> {
                try {
                    options.put(ConnectionOption.valueOf(key), value);
                }
                catch (IllegalArgumentException e) {
                    LOG.warn("Invalid connection option: {}", key);
                }
            });
            builder.connectionOptions(options);
        }

        final String filter =
            stringArgument(arguments, MISSING_TOPIC_NOTIFICATION_FILTER);
        if (filter != null) {
            builder.missingTopicNotificationFilter(filter);
        }
    }

    private static CallToolResult buildResponse(RemoteServer remoteServer) {

        final ToolResponse response = new ToolResponse()
            .addLine("=== Remote Server Created ===")
            .addLine("Name: %s", remoteServer.getName())
            .addLine("Type: %s", remoteServer.getType())
            .addLine();

        switch (remoteServer.getType()) {
            case SECONDARY_INITIATOR:
                final SecondaryInitiator secondaryInitiator =
                    (SecondaryInitiator) remoteServer;
                response.addLine("URL: %s", secondaryInitiator.getUrl());
                addSecondaryServerDetails(response, secondaryInitiator);
                break;
            case PRIMARY_INITIATOR:
                final PrimaryInitiator primaryInitiator =
                    (PrimaryInitiator) remoteServer;
                response.addLine(
                    "URLs: %s",
                    String.join(", ", primaryInitiator.getUrls()));
                response.addLine(
                    "Connector: %s",
                    primaryInitiator.getConnector());
                response.addLine(
                    "Retry Delay: %d ms",
                    primaryInitiator.getRetryDelay());
                break;
            case SECONDARY_ACCEPTOR:
                final SecondaryAcceptor secondaryAcceptor =
                    (SecondaryAcceptor) remoteServer;
                response.addLine(
                    "Primary Host Name: %s",
                    secondaryAcceptor.getPrimaryHostName());
                addSecondaryServerDetails(response, secondaryAcceptor);
                break;
        }

        response.addLine()
            .addLine("Remote server has been successfully created and is now available.");

        return toolResult(response);
    }

    private static void addSecondaryServerDetails(
        ToolResponse response,
        SecondaryServer server) {

        response.addLine(
            "Principal: %s",
            server.getPrincipal() != null && !server.getPrincipal().isEmpty() ?
                server.getPrincipal() :
                    "<anonymous>");

        final String filter = server.getMissingTopicNotificationFilter();
        if (filter != null) {
            response.addLine("Missing Topic Notification Filter: %s", filter);
        }

        final Map<ConnectionOption, String> options =
            server.getConnectionOptions();
        if (options != null && !options.isEmpty()) {
            response.addLine("Connection Options:");
            options.forEach((key, value) ->
                response.addLine("  %s: %s", key, value));
        }
    }
}
