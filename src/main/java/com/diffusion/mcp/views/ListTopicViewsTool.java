/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS;
import static com.diffusion.mcp.tools.ToolUtils.EMPTY_INPUT_SCHEMA;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.control.topics.views.TopicView;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * List topic views tool.
 * <p>
 * Lists all topic views that have been created on the connected Diffusion
 * server.
 *
 * @author DiffusionData Limited
 */
final class ListTopicViewsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListTopicViewsTool.class);

    static final String TOOL_NAME = "list_topic_views";

    private static final String TOOL_DESCRIPTION =
        "Lists all topic views that have been created on the connected Diffusion server, " +
            "sorted by their creation order. " +
            "Needs READ_TOPIC_VIEW permission. " +
            "See the " + TOPIC_VIEWS + " context to understand topic views.";

    private ListTopicViewsTool() {
    }

    /**
     * Create the tool.
     */
    static AsyncToolSpecification create(SessionManager sessionManager) {

        return AsyncToolSpecification.builder()

            .tool(Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(EMPTY_INPUT_SCHEMA)
                .build())

            .callHandler((exchange, request) -> {

                // Check if we have an active session
                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                LOG.info("Starting list topic views operation");

                return Mono
                    .fromFuture(
                        session.feature(Topics.class)
                            .listTopicViews())
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "List topic views completed successfully, found {} views",
                        result.size()))
                    .map(ListTopicViewsTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(List<TopicView> topicViews) {

        if (topicViews.isEmpty()) {
            LOG.info("No topic views found");

            try {
                return toolResult(
                    OBJECT_MAPPER.writeValueAsString(
                        Map.of(
                            "views", new ArrayList<>(),
                            "count", 0,
                            "message", "No topic views found")));
            }
            catch (JsonProcessingException ex) {
                LOG.error("Error serializing empty result", ex);
                return toolError(
                    "Error serializing result: %s",
                    ex.getMessage());
            }
        }

        // Create list of view details
        final List<Map<String, Object>> viewList = new ArrayList<>();
        for (TopicView topicView : topicViews) {
            viewList.add(
                Map.of(
                    "name", topicView.getName(),
                    "specification", topicView.getSpecification(),
                    "roles", topicView.getRoles()));
        }

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        "views", viewList,
                        "count", topicViews.size())));
        }
        catch (JsonProcessingException ex) {
            LOG.error("Error serializing topic views result", ex);
            return toolError(
                "Error serializing topic views result: %s",
                ex.getMessage());
        }

    }

}