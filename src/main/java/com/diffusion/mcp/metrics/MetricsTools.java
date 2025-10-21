/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import java.util.List;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;

/**
 * Metrics tools factory.
 * @author DiffusionData Limited
 */
public final class MetricsTools {

    private MetricsTools() {
    }

    /**
     * Creates a list of metric collector tools.
     */
    public static List<AsyncToolSpecification> createMetricsTools(
        SessionManager sessionManager) {

        return List.of(
            FetchMetricsTool.create(sessionManager),
            CreateSessionMetricCollectorTool.create(sessionManager),
            CreateTopicMetricCollectorTool.create(sessionManager),
            RemoveSessionMetricCollectorTool.create(sessionManager),
            RemoveTopicMetricCollectorTool.create(sessionManager),
            ListSessionMetricCollectorsTool.create(sessionManager),
            ListTopicMetricCollectorsTool.create(sessionManager),
            SetMetricAlertTool.create(sessionManager),
            ListMetricAlertsTool.create(sessionManager),
            RemoveMetricAlertTool.create(sessionManager)
            );
    }
}