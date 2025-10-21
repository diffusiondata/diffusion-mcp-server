/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolOperation;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Remove topic view tool.
 * <p>
 * Removes a named topic view from the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class RemoveTopicViewTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveTopicViewTool.class);

    static final String TOOL_NAME = "remove_topic_view";

    private static final String TOOL_DESCRIPTION =
        "Removes a named topic view from the connected Diffusion server. " +
            "If the named view does not exist, the operation will complete successfully. " +
            "Needs MODIFY_TOPIC_VIEW permission. " +
            "See the " + TOPIC_VIEWS + " context to understand topic views.";

    private static final String VIEW_NAME = "name";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                VIEW_NAME,
                stringProperty("The name of the topic view to remove"))
            .required(VIEW_NAME)
            .additionalProperties(false)
            .build();

    private RemoveTopicViewTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // Check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                // Extract the name parameter
                final String name =
                    stringArgument(request.arguments(), VIEW_NAME);

                LOG.info("Starting remove topic view operation for view: '{}'",
                    name);

                return Mono
                    .fromFuture(
                        session.feature(Topics.class).removeTopicView(name))
                    .timeout(TEN_SECONDS)
                    .doOnSuccess(result -> LOG.debug(
                        "Topic view removal completed successfully for view: '{}'",
                        name))
                    .then(Mono.fromCallable(() -> {
                        LOG.info(
                            "Successfully removed topic view '{}'",
                            name);
                        return toolResult(
                            "Topic view '%s' removed successfully",
                            name);
                    }))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

}