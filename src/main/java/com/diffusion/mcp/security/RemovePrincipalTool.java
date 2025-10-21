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
 * Remove principal tool.
 * <p>
 * Removes an existing principal from the system authentication store.
 *
 * @author DiffusionData Limited
 */
final class RemovePrincipalTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemovePrincipalTool.class);

    static final String TOOL_NAME = "remove_principal";

    private static final String TOOL_DESCRIPTION =
        "Remove an existing principal from the system authentication store. " +
            "The principal must already exist or the operation will fail. " +
            "All sessions authenticated as this principal will remain connected but cannot reconnect." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PRINCIPAL_NAME = "principalName";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PRINCIPAL_NAME,
                stringProperty("The name of the principal to remove"))
            .required(PRINCIPAL_NAME)
            .additionalProperties(false)
            .build();

    private RemovePrincipalTool() {
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

                final String name =
                    stringArgument(request.arguments(), PRINCIPAL_NAME);

                LOG.info("Removing principal: '{}'", name);

                final String script =
                    session.feature(SystemAuthenticationControl.class)
                        .scriptBuilder()
                        .removePrincipal(name)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully removed principal: '{}'",
                        name))
                    .thenReturn(createResult(name))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String principalName) {

        LOG.info("Successfully removed principal '{}'", principalName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PRINCIPAL_NAME, principalName,
                        "status", "removed")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for remove principal: '{}'",
                principalName,
                ex);
            return toolError(
                "Error serialising remove principal result: %s",
                ex.getMessage());
        }
    }
}
