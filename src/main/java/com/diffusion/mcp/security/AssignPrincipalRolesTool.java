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
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Assign principal roles tool.
 * <p>
 * Changes the roles assigned to an existing principal in the system authentication store.
 * This replaces any existing role assignments.
 *
 * @author DiffusionData Limited
 */
final class AssignPrincipalRolesTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(AssignPrincipalRolesTool.class);

    static final String TOOL_NAME = "assign_principal_roles";

    private static final String TOOL_DESCRIPTION =
        "Change the roles assigned to an existing principal in the system authentication store. " +
            "This replaces all existing role assignments for the principal. " +
            "The principal must already exist or the operation will fail. " +
            "New sessions authenticated as this principal will receive the updated roles; " +
            "existing sessions retain their original roles until they reconnect. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PRINCIPAL_NAME = "principalName";
    private static final String ROLES = "roles";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PRINCIPAL_NAME,
                stringProperty("The name of the principal whose roles to update"))
            .property(
                ROLES,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of roles to assign to this principal (replaces existing roles)"))
            .required(PRINCIPAL_NAME, ROLES)
            .additionalProperties(false)
            .build();

    private AssignPrincipalRolesTool() {
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

                final String name = stringArgument(arguments, PRINCIPAL_NAME);

                @SuppressWarnings("unchecked")
                final Set<String> roles = new HashSet<>(
                    (List<String>) arguments.get(ROLES));

                LOG.info(
                    "Assigning {} roles to principal: '{}'",
                    roles.size(),
                    name);

                final String script =
                    session.feature(SystemAuthenticationControl.class)
                        .scriptBuilder()
                        .assignRoles(name, roles)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully assigned roles to principal: '{}'",
                        name))
                    .thenReturn(createResult(name, roles))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(
                        ex -> monoToolException(toolOperation(TOOL_NAME, name),
                            ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String principalName, Set<String> roles) {

        LOG.info(
            "Successfully assigned {} roles to principal '{}'",
            roles.size(),
            principalName);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PRINCIPAL_NAME, principalName,
                        ROLES, roles,
                        "roleCount", roles.size(),
                        "status", "roles_assigned")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for assign principal roles: '{}'",
                principalName,
                ex);
            return toolError(
                "Error serialising assign principal roles result: %s",
                ex.getMessage());
        }
    }
}
