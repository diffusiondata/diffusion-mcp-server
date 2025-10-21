/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for loading prompt content from resource files.
 *
 * @author DiffusionData Limited
 */
final class PromptResourceLoader {

    private static final Logger LOG =
        LoggerFactory.getLogger(PromptResourceLoader.class);

    private static final String PROMPTS_BASE_PATH = "/prompts/";

    // Cache loaded prompts to avoid repeated file I/O
    private static final Map<String, String> promptCache =
        new ConcurrentHashMap<>();

    private PromptResourceLoader() {
    }

    /**
     * Load prompt content from a resource file.
     *
     * @param promptName The name of the prompt (without .md extension)
     * @return The prompt content, or a fallback message if loading fails
     */
    static String loadPrompt(String promptName) {
        return promptCache.computeIfAbsent(
            promptName,
            PromptResourceLoader::loadPromptFromFile);
    }

    /**
     * Load prompt content with variable substitution.
     *
     * @param promptName The name of the prompt
     * @param variables Map of variables to substitute (e.g., {{domain}} ->
     *        "sensors")
     * @return The prompt content with variables substituted
     */
    static String loadPrompt(String promptName, Map<String, String> variables) {

        String content = loadPrompt(promptName);

        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                content = content.replace(placeholder, entry.getValue());
            }
        }

        return content;
    }

    private static String loadPromptFromFile(String promptName) {

        final String resourcePath = PROMPTS_BASE_PATH + promptName + ".md";

        try (InputStream inputStream =
            PromptResourceLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                LOG.error("Prompt resource not found: {}", resourcePath);
                return createFallbackContent(promptName);
            }

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8);

        }
        catch (IOException e) {
            LOG.error("Error loading prompt resource: {}", resourcePath, e);
            return createFallbackContent(promptName);
        }
    }

    private static String createFallbackContent(String promptName) {
        return String.format(
            "# %s%n%nPrompt content could not be loaded from resources.%n" +
                "Please check that the prompt file exists at `/prompts/%s.md`",
            promptName.replace("_", " ").toUpperCase(),
            promptName);
    }

}