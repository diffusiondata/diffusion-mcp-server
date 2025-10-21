/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.security.SecurityTools.getValidPathPermissions;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.types.PathPermission;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Set role path permissions tool.
 * <p>
 * Assigns specific path permissions to a role for a particular path in the security store.
 *
 * @author DiffusionData Limited
 */
final class SetRolePathPermissionsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetRolePathPermissionsTool.class);

    static final String TOOL_NAME = "set_role_path_permissions";

    private static final String TOOL_DESCRIPTION =
        "Set specific path permissions for a role at a particular path in the security store. " +
            "These permissions apply to the specified path and all descendant paths, unless " +
            "overridden by more specific path permission assignments or path isolation. " +
            "This replaces any existing path permission assignment for this role at this path." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLE_NAME = "roleName";
    private static final String PATH = "path";
    private static final String PERMISSIONS = "permissions";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLE_NAME,
                stringProperty("The name of the role"))
            .property(
                PATH,
                stringProperty("The path in the hierarchy (e.g., 'markets/forex/', 'games/chess/')"))
            .property(
                PERMISSIONS,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of path permission names (e.g., ['READ_TOPIC', 'UPDATE_TOPIC'])"))
            .required(ROLE_NAME, PATH, PERMISSIONS)
            .additionalProperties(false)
            .build();

    private SetRolePathPermissionsTool() {
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

                @SuppressWarnings("unchecked")
                final List<String> permissionNames = (List<String>) arguments.get(PERMISSIONS);

                // Convert permission names to PathPermission enum values
                final Set<PathPermission> permissions;
                try {
                    permissions = permissionNames.stream()
                        .map(String::toUpperCase)
                        .map(PathPermission::valueOf)
                        .collect(Collectors.toSet());
                }
                catch (IllegalArgumentException e) {
                    return monoToolError(
                        "Invalid path permission name: %s. Valid permissions include: %s",
                        e.getMessage(),
                        String.join(", ", getValidPathPermissions()));
                }

                LOG.info(
                    "Setting {} path permissions for role: '{}' at path: '{}'",
                    permissions.size(),
                    roleName,
                    path);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .setPathPermissions(roleName, path, permissions)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set path permissions for role: '{}' at path: '{}'",
                        roleName,
                        path))
                    .thenReturn(createResult(roleName, path, permissionNames))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(
                        ex -> monoToolException(
                            toolOperation(TOOL_NAME, roleName, path), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        String roleName,
        String path,
        List<String> permissions) {

        LOG.info(
            "Successfully set {} path permissions for role '{}' at path '{}'",
            permissions.size(),
            roleName,
            path);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLE_NAME, roleName,
                        PATH, path,
                        PERMISSIONS, permissions,
                        "permissionCount", permissions.size(),
                        "status", "updated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set role path permissions: '{}' at path: '{}'",
                roleName,
                path,
                ex);
            return toolError(
                "Error serialising set role path permissions result: %s",
                ex.getMessage());
        }
    }
}
