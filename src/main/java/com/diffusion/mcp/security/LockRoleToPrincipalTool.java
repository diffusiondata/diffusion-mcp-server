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
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Lock role to principal tool.
 * <p>
 * Locks a role so that only a specified principal can modify it.
 *
 * @author DiffusionData Limited
 */
final class LockRoleToPrincipalTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(LockRoleToPrincipalTool.class);

    static final String TOOL_NAME = "lock_role_to_principal";

    private static final String TOOL_DESCRIPTION =
        "Lock a role in the security store to a specific principal. Once locked, only sessions " +
            "authenticated as the locking principal can modify the role's configuration. This provides " +
            "administrative segregation and prevents unauthorised changes to sensitive roles. " +
            "Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLE_NAME = "roleName";
    private static final String PRINCIPAL_NAME = "principalName";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLE_NAME,
                stringProperty("The name of the role to lock"))
            .property(
                PRINCIPAL_NAME,
                stringProperty("The principal that will have exclusive rights to modify this role"))
            .required(ROLE_NAME, PRINCIPAL_NAME)
            .additionalProperties(false)
            .build();

    private LockRoleToPrincipalTool() {
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

                final String roleName = stringArgument(arguments, ROLE_NAME);
                final String principalName = stringArgument(arguments, PRINCIPAL_NAME);

                LOG.info(
                    "Locking role: '{}' to principal: '{}'",
                    roleName,
                    principalName);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .setRoleLockedByPrincipal(roleName, principalName)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully locked role: '{}' to principal: '{}'",
                        roleName,
                        principalName))
                    .thenReturn(createResult(roleName, principalName))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, roleName, principalName),
                        ex,
                        LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String roleName, String principalName) {

        LOG.info(
            "Successfully locked role '{}' to principal '{}'",
            roleName,
            principalName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLE_NAME, roleName,
                        PRINCIPAL_NAME, principalName,
                        "status", "locked")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for lock role to principal: '{}', '{}'",
                roleName,
                principalName,
                ex);
            return toolError(
                "Error serialising lock role to principal result: %s",
                ex.getMessage());
        }
    }
}
