/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Ignore client proposed property tool.
 * <p>
 * Configures the system authentication to ignore a previously trusted client
 * proposed session property. This removes the trust configuration for the property.
 *
 * @author DiffusionData Limited
 */
final class IgnoreClientProposedPropertyTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(IgnoreClientProposedPropertyTool.class);

    static final String TOOL_NAME = "ignore_client_proposed_property";

    private static final String TOOL_DESCRIPTION =
        "Configures the system authentication to ignore a previously trusted client proposed " +
            "session property. This removes the trust configuration, preventing clients from " +
            "proposing values for this property. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PROPERTY_NAME = "propertyName";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PROPERTY_NAME,
                stringProperty(
                    "The name of the client proposed property to ignore " +
                    "(e.g., 'USER_TIER', 'DEPARTMENT')"))
            .required(PROPERTY_NAME)
            .additionalProperties(false)
            .build();

    private IgnoreClientProposedPropertyTool() {
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

                // Check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }


                // Extract parameters
                final String propertyName =
                    stringArgument(request.arguments(), PROPERTY_NAME);

                LOG.info(
                    "Ignoring client proposed property: '{}'",
                    propertyName);

                // Build script
                final String script =
                    session.feature(SystemAuthenticationControl.class)
                        .scriptBuilder()
                        .ignoreClientProposedProperty(propertyName)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully ignored client proposed property: '{}'",
                        propertyName))
                    .thenReturn(createResult(propertyName))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, propertyName), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String propertyName) {

        LOG.info(
            "Successfully configured system authentication to ignore client proposed property '{}'",
            propertyName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PROPERTY_NAME, propertyName,
                        "status", "ignored")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing result for ignore client proposed property: '{}'",
                propertyName,
                ex);
            return toolError(
                "Error serializing ignore client proposed property result: %s",
                ex.getMessage());
        }
    }
}