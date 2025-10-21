/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.prompts;

import static com.diffusion.mcp.prompts.PromptResourceLoader.loadPrompt;
import static com.diffusion.mcp.tools.JsonSchemas.CONSTANT;
import static com.diffusion.mcp.tools.JsonSchemas.DESCRIPTION;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.oneOf;
import static com.diffusion.mcp.tools.ToolUtils.monoToolResult;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Returns Diffusion MCP server context and usage guides (markdown).
 * Backed by the ContextGuides registry and PromptResourceLoader.
 */
public final class ContextTool {

    public static final String TOOL_NAME = "get_context";

    private static final String TOOL_DESCRIPTION =
        "Get Diffusion MCP server context and usage guides - start with 'introduction'.";

    private static final String CONTEXT_TYPE = "type";

    private ContextTool() {}

    /**
     * Tool input schema built with JsonSchemas helpers:
     * - property "type" uses oneOf with const/description pairs from the registry
     * - required ["type"]
     * - additionalProperties = false
     */
    private static final JsonSchema INPUT_SCHEMA = buildInputSchema();

    private static JsonSchema buildInputSchema() {
        // Build the oneOf options from the registry (const + description per entry)
        final List<Map<String, Object>> options = new ArrayList<>(ContextGuides.ALL.size());
        for (Map.Entry<String, String> e : ContextGuides.ALL.entrySet()) {
            options.add(Map.of(
                CONSTANT, e.getKey(),
                DESCRIPTION, e.getValue()
            ));
        }

        return jsonSchemaBuilder()
            .property(
                CONTEXT_TYPE,
                oneOf("The guide you want to retrieve", options))
            .required(CONTEXT_TYPE)
            .additionalProperties(false)
            .build();
    }

    /**
     * Create the tool specification.
     */
    public static AsyncToolSpecification create() {
        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {
                String type = stringArgument(
                    request.arguments(),
                    CONTEXT_TYPE,
                    ContextGuides.INTRODUCTION // sensible default if not provided
                );
                type = type.toLowerCase();

                // Validate against registry for clearer errors (optional)
                if (!ContextGuides.ALL.containsKey(type)) {
                    return monoToolResult("""
                        # Unknown guide
                        The guide "%s" is not available.
                        Available guides: %s
                        """.formatted(type, String.join(", ", ContextGuides.names())));
                }

                // Load and return the markdown from /prompts/<type>.md
                return monoToolResult(loadPrompt(type));
            })

            .build();
    }
}
