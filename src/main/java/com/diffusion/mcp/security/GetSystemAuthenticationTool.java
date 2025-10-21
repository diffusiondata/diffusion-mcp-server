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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation.MatchesSessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation.ValuesSessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SystemAuthenticationConfiguration;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SystemPrincipal;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get system authentication configuration tool.
 * <p>
 * Retrieves the current system authentication store configuration from Diffusion,
 * including principals, anonymous connection settings, and trusted client properties.
 *
 * @author DiffusionData Limited
 */
final class GetSystemAuthenticationTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetSystemAuthenticationTool.class);

    static final String TOOL_NAME = "get_system_authentication";

    private static final String TOOL_DESCRIPTION =
        "Retrieve the current system authentication store configuration from Diffusion, " +
            "including system principals with their assigned roles and locking status, " +
            "anonymous connection action (allow/deny/abstain) and roles, " +
            "and trusted client proposed properties with their validation rules." +
            " Needs VIEW_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private GetSystemAuthenticationTool() {
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

                LOG.info("Retrieving system authentication configuration");

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .getSystemAuthentication())
                    .timeout(TEN_SECONDS)
                    .doOnNext(config -> LOG.debug(
                        "Successfully retrieved system authentication configuration with {} principals",
                        config.getPrincipals().size()))
                    .map(GetSystemAuthenticationTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        SystemAuthenticationConfiguration config) {

        LOG.info(
            "Successfully retrieved system authentication configuration with {} principals, {} trusted properties",
            config.getPrincipals().size(),
            config.getTrustedClientProposedProperties().size());

        try {
            final List<Map<String, Object>> principals = config.getPrincipals().stream()
                .map(GetSystemAuthenticationTool::convertPrincipal)
                .collect(Collectors.toList());

            final Map<String, Map<String, Object>> trustedProperties =
                convertTrustedProperties(config.getTrustedClientProposedProperties());

            final Map<String, Object> result = Map.of(
                "principals", principals,
                "anonymousAction", config.getAnonymousAction().name(),
                "rolesForAnonymousSessions", config.getRolesForAnonymousSessions(),
                "trustedClientProposedProperties", trustedProperties
            );

            return toolResult(OBJECT_MAPPER.writeValueAsString(result));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing system authentication configuration result",
                ex);
            return toolError(
                "Error serializing system authentication configuration result: %s",
                ex.getMessage());
        }
    }

    private static Map<String, Object> convertPrincipal(SystemPrincipal principal) {
        return Map.of(
            "name", principal.getName(),
            "assignedRoles", principal.getAssignedRoles(),
            "lockingPrincipal", principal.getLockingPrincipal().orElse("")
        );
    }

    private static Map<String, Map<String, Object>> convertTrustedProperties(
        Map<String, SessionPropertyValidation> trustedProperties) {

        final Map<String, Map<String, Object>> result = new HashMap<>();

        trustedProperties.forEach((propertyName, validation) -> {
            final Map<String, Object> validationInfo = new HashMap<>();

            if (validation instanceof ValuesSessionPropertyValidation valuesValidation) {
                validationInfo.put("type", "values");
                validationInfo.put("values", valuesValidation.getValues());
            }
            else if (validation instanceof MatchesSessionPropertyValidation matchesValidation) {
                validationInfo.put("type", "regex");
                validationInfo.put("regex", matchesValidation.getRegex());
            }
            else {
                validationInfo.put("type", "unknown");
            }

            result.put(propertyName, validationInfo);
        });

        return result;
    }
}
