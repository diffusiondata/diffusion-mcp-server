/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import java.util.List;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Topic views tools factory.
 *
 * @author DiffusionData Limited
 */
public final class TopicViewTools {

    private TopicViewTools() {
    }

    /**
     * Creates a list of topic views tools.
     */
    public static List<AsyncToolSpecification> createTopicViewTools(
        SessionManager sessionManager) {
        return List.of(
            CreateTopicViewTool.create(sessionManager),
            GetTopicViewTool.create(sessionManager),
            ListTopicViewsTool.create(sessionManager),
            RemoveTopicViewTool.create(sessionManager),
            CreateRemoteServerTool.create(sessionManager),
            ListRemoteServersTool.create(sessionManager),
            CheckRemoteServerTool.create(sessionManager),
            RemoveRemoteServerTool.create(sessionManager));
    }
}