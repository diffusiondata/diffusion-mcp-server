/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static com.diffusion.mcp.metrics.MetricsTools.createMetricsTools;
import static com.diffusion.mcp.security.SecurityTools.createSecurityTools;
import static com.diffusion.mcp.sessions.SessionTools.createSessionTools;
import static com.diffusion.mcp.sessiontrees.SessionTreeTools.createSessionTreeTools;
import static com.diffusion.mcp.topics.TopicTools.createTopicTools;
import static com.diffusion.mcp.views.TopicViewTools.createTopicViewTools;

import java.util.ArrayList;
import java.util.List;

import com.diffusion.mcp.prompts.ContextTool;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Provides tools and utility methods.
 *
 * @author DiffusionData Limited
 */
public final class DiffusionTools {

    private DiffusionTools() {
        // Utility class
    }

    /**
     * Provides a full list of initial tools.
     */
    public static List<AsyncToolSpecification> createTools(
        SessionManager sessionManager) {

        final List<AsyncToolSpecification> tools = new ArrayList<>();

        // Connection management
        tools.add(ConnectTool.create(sessionManager));
        tools.add(DisconnectTool.create(sessionManager));

        // Topic tools
        tools.addAll(createTopicTools(sessionManager));

        // Session Tools
        tools.addAll(createSessionTools(sessionManager));

        // Metrics tools
        tools.addAll(createMetricsTools(sessionManager));

        // Topic View tools
        tools.addAll(createTopicViewTools(sessionManager));

        // Session Tree tools
        tools.addAll(createSessionTreeTools(sessionManager));

        // Security Tools
        tools.addAll(createSecurityTools(sessionManager));

        // Context tool (temporary until prompts work)
        tools.add(ContextTool.create());

        return tools;
    }

}
