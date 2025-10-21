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
 * Set principal password tool.
 * <p>
 * Updates the password for an existing principal in the system authentication store.
 *
 * @author DiffusionData Limited
 */
final class SetPrincipalPasswordTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetPrincipalPasswordTool.class);

    static final String TOOL_NAME = "set_principal_password";

    private static final String TOOL_DESCRIPTION =
        "Update the password for an existing principal in the system authentication store. " +
            "The principal must already exist or the operation will fail. " +
            "Existing sessions authenticated as this principal remain connected." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PRINCIPAL_NAME = "principalName";
    private static final String PASSWORD = "password";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PRINCIPAL_NAME,
                stringProperty("The name of the principal whose password to update"))
            .property(
                PASSWORD,
                stringProperty("The new password for the principal"))
            .required(PRINCIPAL_NAME, PASSWORD)
            .additionalProperties(false)
            .build();

    private SetPrincipalPasswordTool() {
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

                final String principalName = stringArgument(arguments, PRINCIPAL_NAME);
                final String password = stringArgument(arguments, PASSWORD);

                LOG.info("Setting password for principal: '{}'", principalName);

                final String script =
                    session.feature(SystemAuthenticationControl.class)
                        .scriptBuilder()
                        .setPassword(principalName, password)
                        .script();

                LOG.debug("Generated script for principal: '{}'", principalName);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set password for principal: '{}'",
                        principalName))
                    .thenReturn(createResult(principalName))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, principalName), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String principalName) {

        LOG.info("Successfully set password for principal '{}'", principalName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PRINCIPAL_NAME, principalName,
                        "status", "password_updated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set principal password: '{}'",
                principalName,
                ex);
            return toolError(
                "Error serialising set principal password result: %s",
                ex.getMessage());
        }
    }
}
