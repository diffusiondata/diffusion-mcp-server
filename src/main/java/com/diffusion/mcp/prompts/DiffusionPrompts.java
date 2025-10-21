/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.prompts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;

public final class DiffusionPrompts {

    private DiffusionPrompts() {}

    public static List<AsyncPromptSpecification> createPrompts() {
        final List<AsyncPromptSpecification> list = new ArrayList<>(ContextGuides.ALL.size());
        for (Map.Entry<String, String> e : ContextGuides.ALL.entrySet()) {
            list.add(DiffusionPrompt.create(e.getKey(), e.getValue()));
        }
        return list;
    }
}
