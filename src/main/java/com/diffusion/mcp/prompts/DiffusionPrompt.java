/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.prompts;

import static com.diffusion.mcp.prompts.PromptResourceLoader.loadPrompt;

import java.util.List;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;

/**
 * A prompt.
 *
 * @author DiffusionData Limited
 */
final class DiffusionPrompt {

    private final String theName;
    private final String theDescription;

    /**
     * Constructor.
     *
     * @param name
     * @param description
     */
    public DiffusionPrompt(String name, String description) {
        theName = name;
        theDescription = description;
    }

    /**
     * Creates the diffusion prompt.
     */
    static AsyncPromptSpecification create(String name, String description) {
        final DiffusionPrompt instance = new DiffusionPrompt(name, description);
        return new AsyncPromptSpecification(
            new Prompt(name, description, List.of()),
            instance::handlePrompt);
    }

    /**
     * Handle the prompt request.
     */
    private Mono<GetPromptResult> handlePrompt(
        McpAsyncServerExchange exchange,
        GetPromptRequest request) {
        return Mono.just(
            createPromptResult(theDescription, loadPrompt(theName)));
    }

    /**
     * Helper method to create a GetPromptResult with consistent formatting.
     */
    private static GetPromptResult createPromptResult(
        String description,
        String content) {

        return new GetPromptResult(
            description,
            List.of(
                new PromptMessage(
                    Role.ASSISTANT,
                    new TextContent(content))));
    }

}
