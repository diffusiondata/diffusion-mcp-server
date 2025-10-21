/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SECURITY;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Deisolate path tool.
 * <p>
 * Removes path isolation, allowing the path to inherit permissions from parent paths.
 *
 * @author DiffusionData Limited
 */
final class DeisolatePathTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(DeisolatePathTool.class);

    static final String TOOL_NAME = "deisolate_path";

    private static final String TOOL_DESCRIPTION =
        "Remove isolation from a path in the security store. This allows the path to inherit " +
            "path permissions from parent path assignments and default path permissions. " +
            "Use this to remove a security boundary that was previously created with isolate_path." +
            " Needs MODIFY_SECURITY permission. " +
            "Get the " + SECURITY +
            " context to understand security and permissions.";

    private static final String PATH = "path";

    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                PATH,
                stringProperty("The path to deisolate"))
            .required(PATH)
            .additionalProperties(false)
            .build();

    private DeisolatePathTool() {
    }

    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final String path = stringArgument(request.arguments(), PATH);

                LOG.info("Deisolating path: '{}'", path);

                final String script =
                    session.feature(SecurityControl.class)
                        .scriptBuilder()
                        .deisolatePath(path)
                        .script();

                LOG.debug("Generated script: {}", script);

                return Mono
                    .fromFuture(
                        session.feature(SecurityControl.class)
                            .updateStore(script))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Successfully deisolated path: '{}'",
                        path))
                    .thenReturn(createResult(path))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, path), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String path) {

        LOG.info("Successfully deisolated path '{}'", path);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        PATH, path,
                        "status", "deisolated")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serialising result for deisolate path: '{}'",
                path,
                ex);
            return toolError(
                "Error serialising deisolate path result: %s",
                ex.getMessage());
        }
    }
}