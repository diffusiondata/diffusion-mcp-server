/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import java.util.List;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Session tree tools factory.
 *
 * @author DiffusionData Limited
 */
public final class SessionTreeTools {

    private SessionTreeTools() {
    }

    /**
     * Creates a list of session tree tools.
     */
    public static List<AsyncToolSpecification> createSessionTreeTools(
        SessionManager sessionManager) {
        return List.of(
            PutBranchMappingTableTool.create(sessionManager),
            ListSessionTreeBranchesTool.create(sessionManager),
            GetBranchMappingTableTool.create(sessionManager),
            RemoveBranchMappingTableTool.create(sessionManager));
    }
}