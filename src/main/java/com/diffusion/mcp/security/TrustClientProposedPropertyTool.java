/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.tools.JsonSchemas.arrayProperty;
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
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
 * Trust client proposed property tool.
 * <p>
 * Configures the system authentication to trust a client proposed session property
 * with a specific set of allowed values. This is required for session trees to use
 * user-defined session properties.
 *
 * @author DiffusionData Limited
 */
final class TrustClientProposedPropertyTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(TrustClientProposedPropertyTool.class);

    static final String TOOL_NAME = "trust_client_proposed_property";

    private static final String TOOL_DESCRIPTION =
        "Configures the system authentication to trust a client proposed session property " +
            "with a specific set of allowed values. This is required for session trees to use " +
            "user-defined session properties for routing. The property will only be accepted " +
            "if the client's proposed value matches one of the allowed values." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PROPERTY_NAME = "propertyName";
    private static final String ALLOWED_VALUES = "allowedValues";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PROPERTY_NAME,
                stringProperty(
                    "The name of the client proposed property to trust " +
                    "(e.g., 'USER_TIER', 'DEPARTMENT')"))
            .property(
                ALLOWED_VALUES,
                arrayProperty(
                    stringProperty(null), // items: non-empty strings
                    1,    // minItems
                    20,   // maxItems
                    true, // unique
                    "Set of allowed values for this property (e.g., ['premium', 'standard', 'basic'])"))
            .required(PROPERTY_NAME, ALLOWED_VALUES)
            .additionalProperties(false)
            .build();

    private TrustClientProposedPropertyTool() {
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

                final Map<String, Object> arguments = request.arguments();

                // Extract parameters
                final String propertyName =
                    stringArgument(arguments, PROPERTY_NAME);

                @SuppressWarnings("unchecked")
                final Set<String> allowedValues =
                    ((java.util.List<String>) arguments.get(ALLOWED_VALUES))
                        .stream()
                        .map(String::trim)
                        .collect(Collectors.toSet());

                LOG.info(
                    "Trusting client proposed property: '{}' with {} allowed values",
                    propertyName,
                    allowedValues.size());

                // Build script
                final String script =
                    session.feature(SystemAuthenticationControl.class)
                        .scriptBuilder()
                        .trustClientProposedPropertyIn(propertyName, allowedValues)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully trusted client proposed property: '{}'",
                        propertyName))
                    .thenReturn(createResult(propertyName, allowedValues))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, propertyName), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        String propertyName,
        Set<String> allowedValues) {

        LOG.info(
            "Successfully configured trust for client proposed property '{}' with {} allowed values",
            propertyName,
            allowedValues.size());

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PROPERTY_NAME, propertyName,
                        ALLOWED_VALUES, allowedValues,
                        "valueCount", allowedValues.size(),
                        "status", "trusted")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing result for trust client proposed property: '{}'",
                propertyName,
                ex);
            return toolError(
                "Error serializing trust client proposed property result: %s",
                ex.getMessage());
        }
    }
}
