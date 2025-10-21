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
 * Remove role path permissions tool.
 * <p>
 * Removes specific path permission assignments for a role at a particular path,
 * allowing permissions to be inherited from parent paths or default permissions.
 *
 * @author DiffusionData Limited
 */
final class RemoveRolePathPermissionsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveRolePathPermissionsTool.class);

    static final String TOOL_NAME = "remove_role_path_permissions";

    private static final String TOOL_DESCRIPTION =
        "Remove specific path permission assignments for a role at a particular path in the " +
            "security store. This is different from setting no permissions - removing the assignment " +
            "allows the role to inherit permissions from parent path assignments or from default " +
            "path permissions. Descendant paths that don't have their own assignments will also " +
            "inherit differently after this change. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLE_NAME = "roleName";
    private static final String PATH = "path";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLE_NAME,
                stringProperty("The name of the role"))
            .property(
                PATH,
                stringProperty("The path whose permission assignment to remove"))
            .required(ROLE_NAME, PATH)
            .additionalProperties(false)
            .build();

    private RemoveRolePathPermissionsTool() {
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
                final String path = stringArgument(arguments, PATH);

                LOG.info(
                    "Removing path permissions for role: '{}' at path: '{}'",
                    roleName,
                    path);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .removePathPermissions(roleName, path)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully removed path permissions for role: '{}' at path: '{}'",
                        roleName,
                        path))
                    .thenReturn(createResult(roleName, path))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, roleName, path), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String roleName, String path) {

        LOG.info(
            "Successfully removed path permissions for role '{}' at path '{}'",
            roleName,
            path);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLE_NAME, roleName,
                        PATH, path,
                        "status", "removed")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for remove role path permissions: '{}' at path: '{}'",
                roleName,
                path,
                ex);
            return toolError(
                "Error serialising remove role path permissions result: %s",
                ex.getMessage());
        }
    }
}
