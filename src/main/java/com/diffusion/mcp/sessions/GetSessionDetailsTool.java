/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSIONS;
import static com.diffusion.mcp.sessions.SessionPropertyFormatter.formatProperties;
import static com.diffusion.mcp.tools.JsonSchemas.arrayProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;
import static com.pushtechnology.diffusion.client.Diffusion.sessionIdFromString;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.clients.ClientControl;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionId;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get session details tool.
 *
 * @author DiffusionData Limited
 */
final class GetSessionDetailsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetSessionDetailsTool.class);

    static final String TOOL_NAME = "get_session_details";

    private static final String TOOL_DESCRIPTION =
        "Retrieves detailed properties for a specific Diffusion session by session ID. " +
            "Needs VIEW_SESSION permission. " +
            "Get the " + SESSIONS + " context to understand sessions.";

    private static final String SESSION_ID = "sessionId";
    private static final String PROPERTIES = "properties";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                SESSION_ID,
                stringProperty("The session ID to get details for"))
            .property(
                PROPERTIES,
                arrayProperty(
                    stringProperty(null), // items: non-empty strings
                    1,    // minItems
                    50,   // maxItems
                    true, // unique
                    "Optional array of specific property keys to retrieve. " +
                    "If not provided, all fixed and user properties will be retrieved."))
            .required(SESSION_ID)
            .additionalProperties(false)
            .build();

    private static final List<String> DEFAULT_PROPERTIES =
        List.of(
            Session.ALL_FIXED_PROPERTIES,
            Session.ALL_USER_PROPERTIES);

    private GetSessionDetailsTool() {
    }

    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // Check if we have an active session
                Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final Map<String, Object> arguments = request.arguments();

                // Extract parameters
                final String sessionIdString =
                    stringArgument(arguments, SESSION_ID);

                // Parse session ID
                final SessionId sessionId;
                try {
                    sessionId = sessionIdFromString(sessionIdString);
                }
                catch (IllegalArgumentException e) {
                    return monoToolError(
                        "Error: Invalid session ID format: %s",
                        sessionIdString);
                }

                // Extract properties parameter (optional)
                @SuppressWarnings("unchecked")
                final List<String> propertiesParam =
                    (List<String>) arguments.get(PROPERTIES);

                // Default to all properties if not specified
                final Collection<String> requestedProperties;
                if (propertiesParam != null && !propertiesParam.isEmpty()) {
                    requestedProperties = propertiesParam;
                }
                else {
                    requestedProperties = DEFAULT_PROPERTIES;
                }

                LOG.info(
                    "Getting session details for session: {} with properties: {}",
                    sessionId,
                    requestedProperties);

                return Mono
                    .fromFuture(
                        session.feature(ClientControl.class)
                            .getSessionProperties(
                                sessionId,
                                requestedProperties))
                    .timeout(TEN_SECONDS)
                    .doOnNext(properties -> LOG.debug(
                        "Retrieved properties for session: {} - {} properties found",
                        sessionId,
                        properties.size()))
                    .map(properties -> {

                        LOG.info(
                            "Successfully retrieved {} properties for session: {}",
                            properties.size(),
                            sessionId);

                        // Format properties for better readability
                        final Map<String, String> formattedProperties =
                            formatProperties(properties);

                        try {
                            return toolResult(
                                OBJECT_MAPPER.writeValueAsString(
                                    Map.of(
                                        SESSION_ID,
                                        sessionIdString,
                                        PROPERTIES,
                                        formattedProperties,
                                        "propertyCount",
                                        formattedProperties.size())));
                        }
                        catch (JsonProcessingException e) {
                            LOG.error(
                                "Error serializing session details for: {}",
                                sessionId,
                                e);
                            return toolError(
                                "Error serializing session details: %s",
                                e.getMessage());
                        }
                    })
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, sessionIdString), ex, LOG));
            })
            .build();
    }
}