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
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
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
 * Set roles for named sessions tool.
 * <p>
 * Configures the default roles assigned to named (authenticated) sessions in the security store.
 *
 * @author DiffusionData Limited
 */
final class SetRolesForNamedSessionsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetRolesForNamedSessionsTool.class);

    static final String TOOL_NAME = "set_roles_for_named_sessions";

    private static final String TOOL_DESCRIPTION =
        "Set the default roles assigned to named (authenticated) sessions in the security store. " +
            "These roles are assigned in addition to any roles assigned to the specific principal " +
            "in the system authentication store. This provides a baseline set of permissions for " +
            "all authenticated users. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ROLES = "roles";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ROLES,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of roles to assign to all named sessions"))
            .required(ROLES)
            .additionalProperties(false)
            .build();

    private SetRolesForNamedSessionsTool() {
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

                @SuppressWarnings("unchecked")
                final Set<String> roles = new HashSet<>(
                    (List<String>) request.arguments().get(ROLES));

                LOG.info(
                    "Setting {} roles for named sessions",
                    roles.size());

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .setRolesForNamedSessions(roles)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set roles for named sessions"))
                    .thenReturn(createResult(roles))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(Set<String> roles) {

        LOG.info(
            "Successfully set {} roles for named sessions",
            roles.size());

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        ROLES, roles,
                        "roleCount", roles.size(),
                        "status", "updated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set roles for named sessions",
                ex);
            return toolError(
                "Error serialising set roles for named sessions result: %s",
                ex.getMessage());
        }
    }
}