/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import java.util.List;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Topic tools factory.
 *
 * @author DiffusionData Limited
 */
public final class TopicTools {

    private TopicTools() {
    }

    /**
     * Creates a list of topic views tools.
     */
    public static List<AsyncToolSpecification> createTopicTools(
        SessionManager sessionManager) {

        return List.of(
            AddTopicTool.create(sessionManager),
            RemoveTopicsTool.create(sessionManager),
            UpdateTopicTool.create(sessionManager),
            FetchTopicsTool.create(sessionManager),
            FetchTopicTool.create(sessionManager),
            TimeSeriesValueRangeQueryTool.create(sessionManager));
    }
}