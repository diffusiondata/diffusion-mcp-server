/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static com.diffusion.mcp.prompts.ContextGuides.INTRODUCTION;
import static com.diffusion.mcp.prompts.ContextGuides.SESSIONS;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Connect tool.
 * <p>
 * Connects to a Diffusion server at the supplied URL, with the supplied
 * credentials and optional session properties.
 *
 * @author DiffusionData Limited
 */
public final class ConnectTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ConnectTool.class);

    static final String TOOL_NAME = "connect";

    private static final String TOOL_DESCRIPTION =
        "Connects to a Diffusion server with the provided credentials and optional session properties. " +
            "Session properties can be used with session trees for routing and access control. " +
            "Returns success or failure reason. " +
            "Session will time out if you have not used it for more than 5 minutes and you will have to reconnect. " +
            "Before connecting read the " + INTRODUCTION +
            " context to understand what can be done once a session has been connected. " +
            "For more information about sessions see the " + SESSIONS +
            " context.";

    /*
     * Parameters.
     */
    private static final String URL = "url";
    private static final String PRINCIPAL = "principal";
    private static final String PASSWORD = "password";
    private static final String SESSION_PROPERTIES = "sessionProperties";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                URL,
                stringProperty("The URL of the Diffusion server"))
            .property(
                PRINCIPAL,
                stringProperty("The principal/username for authentication"))
            .property(
                PASSWORD,
                stringProperty("The password for authentication"))
            .property(
                SESSION_PROPERTIES,
                Map.of(
                    "type", "object",
                    "description",
                        "Optional session properties as key-value pairs " +
                        "(e.g., {'USER_TIER': 'premium', 'DEPARTMENT': 'finance'})",
                    "additionalProperties", Map.of("type", "string"))
                )
            .required(URL, PRINCIPAL, PASSWORD)
            .additionalProperties(false)
            .build();

    private ConnectTool() {
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

            .callHandler((exchange, request) -> Mono.fromCallable(() -> {
                LOG.info("CONNECTING " + exchange.sessionId());
                try {
                    // Extract arguments from CallToolRequest
                    final Map<String, Object> arguments = request.arguments();

                    // Extract the connection parameters
                    final String url = stringArgument(arguments, URL);
                    final String principal = stringArgument(arguments, PRINCIPAL);
                    final String password = stringArgument(arguments, PASSWORD);

                    // Extract optional session properties
                    @SuppressWarnings("unchecked")
                    final Map<String, String> sessionProperties =
                        (Map<String, String>) arguments.get(SESSION_PROPERTIES);

                    final Session session =
                        sessionManager.connect(
                            exchange.sessionId(),
                            principal,
                            password,
                            url,
                            sessionProperties);

                    // Build result message with session properties info
                    final StringBuilder result = new StringBuilder();
                    result.append(String.format(
                        "Successfully connected to Diffusion server at %s with session id %s",
                        url,
                        session.getSessionId()));

                    if (sessionProperties != null && !sessionProperties.isEmpty()) {
                        result.append(String.format(
                            ". Session properties: %s",
                            sessionProperties));
                    }

                    return toolResult(result.toString());

                }
                catch (Exception e) {
                    return toolError(
                        "Error connecting to Diffusion server: %s",
                        e.getMessage());
                }
            }))
            .build();
    }
}