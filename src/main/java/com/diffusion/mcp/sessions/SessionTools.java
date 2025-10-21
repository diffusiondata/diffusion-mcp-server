/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import java.util.List;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Session tools factory.
 *
 * @author DiffusionData Limited
 */
public final class SessionTools {

    private SessionTools() {
    }

    /**
     * Creates a list of session tools.
     */
    public static List<AsyncToolSpecification> createSessionTools(SessionManager sessionManager) {
        return List.of(
            GetSessionDetailsTool.create(sessionManager),
            GetSessionsTool.create(sessionManager));
    }
}