/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.tools.ToolUtils.EMPTY_INPUT_SCHEMA;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
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
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl.Role;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl.SecurityConfiguration;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.types.GlobalPermission;
import com.pushtechnology.diffusion.client.types.PathPermission;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get security configuration tool.
 * <p>
 * Retrieves the current security store configuration from Diffusion,
 * including roles, permissions, and path isolation settings.
 *
 * @author DiffusionData Limited
 */
final class GetSecurityTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetSecurityTool.class);

    static final String TOOL_NAME = "get_security";

    private static final String TOOL_DESCRIPTION =
        "Retrieve the current security store configuration from Diffusion, " +
            "including roles, permissions, and path isolation settings. " +
            "Returns information about default roles for anonymous and named sessions, " +
            "all defined roles with their global permissions, path permissions, " +
            "included roles, and isolated paths. Needs VIEW_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private GetSecurityTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(EMPTY_INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // Check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                LOG.info("Retrieving security configuration");

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class).getSecurity())
                    .timeout(TEN_SECONDS)
                    .doOnNext(config -> LOG.debug(
                        "Successfully retrieved security configuration with {} roles",
                        config.getRoles().size()))
                    .map(GetSecurityTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(SecurityConfiguration config) {

        LOG.info(
            "Successfully retrieved security configuration with {} roles, {} isolated paths",
            config.getRoles().size(),
            config.getIsolatedPaths().size());

        try {
            final List<Map<String, Object>> roles = config.getRoles().stream()
                .map(GetSecurityTool::convertRole)
                .collect(Collectors.toList());

            final Map<String, Object> result = Map.of(
                "rolesForAnonymousSessions", config.getRolesForAnonymousSessions(),
                "rolesForNamedSessions", config.getRolesForNamedSessions(),
                "roles", roles,
                "isolatedPaths", config.getIsolatedPaths()
            );

            return toolResult(OBJECT_MAPPER.writeValueAsString(result));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing security configuration result",
                ex);
            return toolError(
                "Error serializing security configuration result: %s",
                ex.getMessage());
        }
    }

    private static Map<String, Object> convertRole(Role role) {

        final Set<String> globalPerms = role.getGlobalPermissions().stream()
            .map(GlobalPermission::name)
            .collect(Collectors.toSet());

        final Set<String> defaultPathPerms =
            role.getDefaultPathPermissions()
                .stream()
                .map(PathPermission::name)
                .collect(Collectors.toSet());

        final Map<String, Set<String>> pathPerms =
            role.getPathPermissions().entrySet()
                .stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream()
                        .map(PathPermission::name)
                        .collect(Collectors.toSet())));

        return Map.of(
            "name", role.getName(),
            "globalPermissions", globalPerms,
            "defaultPathPermissions", defaultPathPerms,
            "pathPermissions", pathPerms,
            "includedRoles", role.getIncludedRoles(),
            "lockingPrincipal", role.getLockingPrincipal().orElse("")
        );
    }
}