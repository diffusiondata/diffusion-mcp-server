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
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
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
 * Set anonymous connection policy tool.
 * <p>
 * Configures how the system authentication handler treats anonymous connection attempts
 * and optionally the roles assigned to anonymous sessions.
 *
 * @author DiffusionData Limited
 */
final class SetAnonymousConnectionPolicyTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(SetAnonymousConnectionPolicyTool.class);

    static final String TOOL_NAME = "set_anonymous_connection_policy";

    private static final String TOOL_DESCRIPTION =
        "Configure the system authentication policy for anonymous connections. " +
            "Options: 'allow' (accept anonymous connections with specified roles), " +
            "'deny' (reject anonymous connections), or 'abstain' (defer to subsequent handlers). " +
            "When using 'allow', specify the roles to assign to anonymous sessions." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String ACTION = "action";
    private static final String ROLES = "roles";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                ACTION,
                stringProperty(
                    "Action for anonymous connections: 'allow', 'deny', or 'abstain'"))
            .property(
                ROLES,
                arrayProperty(
                    stringProperty(null),
                    0,
                    50,
                    true,
                    "Roles to assign to anonymous sessions (required if action is 'allow', ignored otherwise)"))
            .required(ACTION)
            .additionalProperties(false)
            .build();

    private SetAnonymousConnectionPolicyTool() {
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

                final String action = stringArgument(arguments, ACTION).toLowerCase();

                final SystemAuthenticationControl.ScriptBuilder builder =
                    session.feature(SystemAuthenticationControl.class).scriptBuilder();

                final String script;

                switch (action) {
                    case "allow":
                        if (!arguments.containsKey(ROLES)) {
                            return monoToolError(
                                "Roles must be specified when action is 'allow'");
                        }

                        @SuppressWarnings("unchecked")
                        final Set<String> roles = new HashSet<>(
                            (List<String>) arguments.get(ROLES));

                        LOG.info(
                            "Allowing anonymous connections with {} roles",
                            roles.size());

                        script = builder.allowAnonymousConnections(roles).script();
                        break;

                    case "deny":
                        LOG.info("Denying anonymous connections");
                        script = builder.denyAnonymousConnections().script();
                        break;

                    case "abstain":
                        LOG.info("Abstaining from anonymous connection decisions");
                        script = builder.abstainAnonymousConnections().script();
                        break;

                    default:
                        return monoToolError(
                            "Invalid action: '%s'. Must be 'allow', 'deny', or 'abstain'",
                            action);
                }

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SystemAuthenticationControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully set anonymous connection policy to: '{}'",
                        action))
                    .thenReturn(createResult(action, arguments.get(ROLES)))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, action), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String action, Object rolesObj) {

        LOG.info("Successfully set anonymous connection policy to '{}'", action);

        try {
            final Map<String, Object> result;

            if ("allow".equals(action) && rolesObj != null) {
                @SuppressWarnings("unchecked")
                final List<String> rolesList = (List<String>) rolesObj;
                result = Map.of(
                    ACTION, action,
                    ROLES, new HashSet<>(rolesList),
                    "status", "policy_updated");
            }
            else {
                result = Map.of(
                    ACTION, action,
                    "status", "policy_updated");
            }

            return toolResult(OBJECT_MAPPER.writeValueAsString(result));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for set anonymous connection policy",
                ex);
            return toolError(
                "Error serialising set anonymous connection policy result: %s",
                ex.getMessage());
        }
    }
}
