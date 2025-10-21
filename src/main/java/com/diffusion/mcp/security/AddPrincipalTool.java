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
 * Add principal tool.
 * <p>
 * Adds a new principal to the system authentication store with password and assigned roles.
 * Optionally locks the principal so only a specified principal can modify it.
 *
 * @author DiffusionData Limited
 */
final class AddPrincipalTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(AddPrincipalTool.class);

    static final String TOOL_NAME = "add_principal";

    private static final String TOOL_DESCRIPTION =
        "Add a new principal to the system authentication store. The principal is assigned " +
            "a password and a set of roles. Optionally, the principal can be locked to another " +
            "principal, meaning only that principal can modify this principal's configuration. " +
            "The operation fails if the principal already exists. Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PRINCIPAL_NAME = "principalName";
    private static final String PASSWORD = "password";
    private static final String ROLES = "roles";
    private static final String LOCKING_PRINCIPAL = "lockingPrincipal";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PRINCIPAL_NAME,
                stringProperty("The name of the principal to add (e.g., 'alice', 'service_account')"))
            .property(
                PASSWORD,
                stringProperty("The password for the principal"))
            .property(
                ROLES,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Set of roles to assign to this principal (e.g., ['ADMIN', 'TRADER'])"))
            .property(
                LOCKING_PRINCIPAL,
                stringProperty(
                    "Optional: Name of the principal that will have exclusive rights to modify this principal"))
            .required(PRINCIPAL_NAME, PASSWORD, ROLES)
            .additionalProperties(false)
            .build();

    private AddPrincipalTool() {
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
                final String password = stringArgument(arguments, PASSWORD);

                @SuppressWarnings("unchecked")
                final Set<String> roles = new HashSet<>(
                    (List<String>) arguments.get(ROLES));

                final String lockingPrincipal = arguments.containsKey(LOCKING_PRINCIPAL) ?
                    stringArgument(arguments, LOCKING_PRINCIPAL) : null;

                LOG.info(
                    "Adding principal: '{}' with {} roles{}",
                    name,
                    roles.size(),
                    lockingPrincipal != null ? ", locked by '" + lockingPrincipal + "'" : "");

                final SystemAuthenticationControl.ScriptBuilder builder =
                    session.feature(SystemAuthenticationControl.class).scriptBuilder();

                final String script = lockingPrincipal != null ?
                    builder.addPrincipal(name, password, roles, lockingPrincipal).script() :
                    builder.addPrincipal(name, password, roles).script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully added principal: '{}'", name))
                    .thenReturn(
                        createResult(name, roles, lockingPrincipal))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        String principalName,
        Set<String> roles,
        String lockingPrincipal) {

        LOG.info(
            "Successfully added principal '{}' with {} roles",
            principalName,
            roles.size());

        try {
            final Map<String, Object> result = lockingPrincipal != null ?
                Map.of(
                    PRINCIPAL_NAME, principalName,
                    ROLES, roles,
                    LOCKING_PRINCIPAL, lockingPrincipal,
                    "status", "added") :
                Map.of(
                    PRINCIPAL_NAME, principalName,
                    ROLES, roles,
                    "status", "added");

            return toolResult(OBJECT_MAPPER.writeValueAsString(result));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for add principal: '{}'",
                principalName,
                ex);
            return toolError(
                "Error serialising add principal result: %s",
                ex.getMessage());
        }
    }
}