/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import java.util.List;
import java.util.Set;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Security tools factory.
 *
 * @author DiffusionData Limited
 */
public final class SecurityTools {

    private SecurityTools() {
    }

    /**
     * Creates a list of session tree tools.
     */
    public static List<AsyncToolSpecification> createSecurityTools(
        SessionManager sessionManager) {
        return List.of(
            // System Authentication Store Tools
            GetSystemAuthenticationTool.create(sessionManager),
            AddPrincipalTool.create(sessionManager),
            RemovePrincipalTool.create(sessionManager),
            SetPrincipalPasswordTool.create(sessionManager),
            AssignPrincipalRolesTool.create(sessionManager),
            SetAnonymousConnectionPolicyTool.create(sessionManager),
            TrustClientProposedPropertyTool.create(sessionManager),
            IgnoreClientProposedPropertyTool.create(sessionManager),
            // Security Store Tools
            GetSecurityTool.create(sessionManager),
            SetRolesForAnonymousSessionsTool.create(sessionManager),
            SetRolesForNamedSessionsTool.create(sessionManager),
            SetRoleGlobalPermissionsTool.create(sessionManager),
            SetRoleDefaultPathPermissionsTool.create(sessionManager),
            SetRolePathPermissionsTool.create(sessionManager),
            RemoveRolePathPermissionsTool.create(sessionManager),
            SetRoleIncludesTool.create(sessionManager),
            IsolatePathTool.create(sessionManager),
            DeisolatePathTool.create(sessionManager),
            LockRoleToPrincipalTool.create(sessionManager));
    }

    static Set<String> getValidGlobalPermissions() {
        return Set.of(
            "VIEW_SECURITY",
            "MODIFY_SECURITY",
            "VIEW_SESSION",
            "MODIFY_SESSION",
            "REGISTER_HANDLER",
            "AUTHENTICATE",
            "VIEW_SERVER",
            "CONTROL_SERVER",
            "READ_TOPIC_VIEWS",
            "MODIFY_TOPIC_VIEWS");
    }

    static Set<String> getValidPathPermissions() {
        return Set.of(
            "SELECT_TOPIC",
            "READ_TOPIC",
            "UPDATE_TOPIC",
            "MODIFY_TOPIC",
            "SEND_TO_MESSAGE_HANDLER",
            "SEND_TO_SESSION",
            "QUERY_OBSOLETE_TIME_SERIES_EVENTS",
            "EDIT_TIME_SERIES_EVENTS",
            "EDIT_OWN_TIME_SERIES_EVENTS",
            "ACQUIRE_LOCK",
            "EXPOSE_BRANCH");
    }
}