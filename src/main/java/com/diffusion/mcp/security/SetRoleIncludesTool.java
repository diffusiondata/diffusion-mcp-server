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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Set role includes tool.
 * <p>
 * Configures which roles are included within another role, establishing role hierarchy
 * and permission inheritance.
 *
 * @author DiffusionData Limited
 */
final class SetRoleIncludesTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetRoleIncludesTool.class);

    static final String TOOL_NAME = "set_role_includes";

    private static final String TOOL_DESCRIPTION =
        "Set the roles included within a role in the security store. When a role includes other " +
            "roles, it inherits all their permissions (both global and path permissions). This creates " +
            "a role hierarchy. This replaces any existing role inclusion relationships for the role." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLE_NAME = "roleName";
    private static final String INCLUDED_ROLES = "includedRoles";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLE_NAME,
                stringProperty("The name of the role"))
            .property(
                INCLUDED_ROLES,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of role names to include in this role (e.g., ['AUTHENTICATED', 'READ_ONLY'])"))
            .required(ROLE_NAME, INCLUDED_ROLES)
            .additionalProperties(false)
            .build();

    private SetRoleIncludesTool() {
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
                final Set<String> includedRoles = new HashSet<>(
                    (List<String>) arguments.get(INCLUDED_ROLES));

                LOG.info(
                    "Setting {} included roles for role: '{}'",
                    includedRoles.size(),
                    roleName);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .setRoleIncludes(roleName, includedRoles)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set included roles for role: '{}'",
                        roleName))
                    .thenReturn(createResult(roleName, includedRoles))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, roleName), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String roleName, Set<String> includedRoles) {

        LOG.info(
            "Successfully set {} included roles for role '{}'",
            includedRoles.size(),
            roleName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLE_NAME, roleName,
                        INCLUDED_ROLES, includedRoles,
                        "includedRoleCount", includedRoles.size(),
                        "status", "updated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set role includes: '{}'",
                roleName,
                ex);
            return toolError(
                "Error serialising set role includes result: %s",
                ex.getMessage());
        }
    }
}