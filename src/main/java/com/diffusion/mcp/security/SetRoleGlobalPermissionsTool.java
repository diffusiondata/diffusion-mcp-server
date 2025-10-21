/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.security.SecurityTools.getValidGlobalPermissions;
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
import com.pushtechnology.diffusion.client.types.GlobalPermission;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Set role global permissions tool.
 * <p>
 * Assigns global permissions to a role in the security store.
 *
 * @author DiffusionData Limited
 */
final class SetRoleGlobalPermissionsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetRoleGlobalPermissionsTool.class);

    static final String TOOL_NAME = "set_role_global_permissions";

    private static final String TOOL_DESCRIPTION =
        "Set the global permissions assigned to a role in the security store. " +
            "Global permissions control server-wide actions like viewing security configuration, " +
            "modifying security, registering handlers, etc. This replaces any existing global " +
            "permissions for the role. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLE_NAME = "roleName";
    private static final String PERMISSIONS = "permissions";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLE_NAME,
                stringProperty("The name of the role"))
            .property(
                PERMISSIONS,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of global permission names (e.g., ['VIEW_SECURITY', 'MODIFY_SECURITY', 'REGISTER_HANDLER'])"))
            .required(ROLE_NAME, PERMISSIONS)
            .additionalProperties(false)
            .build();

    private SetRoleGlobalPermissionsTool() {
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

                @SuppressWarnings("unchecked")
                final List<String> permissionNames = (List<String>) arguments.get(PERMISSIONS);

                // Convert permission names to GlobalPermission enum values
                final Set<GlobalPermission> permissions;
                try {
                    permissions = permissionNames.stream()
                        .map(String::toUpperCase)
                        .map(GlobalPermission::valueOf)
                        .collect(Collectors.toSet());
                }
                catch (IllegalArgumentException e) {
                    return monoToolError(
                        "Invalid global permission name: %s. Valid permissions include: %s",
                        e.getMessage(),
                        String.join(", ", getValidGlobalPermissions()));
                }

                LOG.info(
                    "Setting {} global permissions for role: '{}'",
                    permissions.size(),
                    roleName);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .setGlobalPermissions(roleName, permissions)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set global permissions for role: '{}'",
                        roleName))
                    .thenReturn(createResult(roleName, permissionNames))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, roleName), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String roleName, List<String> permissions) {

        LOG.info(
            "Successfully set {} global permissions for role '{}'",
            permissions.size(),
            roleName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLE_NAME, roleName,
                        PERMISSIONS, permissions,
                        "permissionCount", permissions.size(),
                        "status", "updated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set role global permissions: '{}'",
                roleName,
                ex);
            return toolError(
                "Error serialising set role global permissions result: %s",
                ex.getMessage());
        }
    }
}
