/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS;
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
import java.util.Set;
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
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get topic view tool.
 * <p>
 * Retrieves details of a named topic view from the connected Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class GetTopicViewTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetTopicViewTool.class);

    static final String TOOL_NAME = "get_topic_view";

    private static final String TOOL_DESCRIPTION =
        "Retrieves details of a named topic view from the connected Diffusion server, " +
            "including its specification and roles. " +
            "Needs READ_TOPIC_VIEW permission." +
            "See the " + TOPIC_VIEWS + " context to understand topic views.";

    private static final String VIEW_NAME = "name";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                VIEW_NAME,
                stringProperty("The name of the topic view to retrieve"))
            .required(VIEW_NAME)
            .additionalProperties(false)
            .build();

    private GetTopicViewTool() {
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

                LOG.info("Starting get topic view operation for view: '{}'", name);

                return Mono
                    .fromFuture(
                        session.feature(Topics.class).getTopicView(name))
                    .map(topicView -> createResult(name, topicView))
                    .switchIfEmpty(Mono.fromSupplier(() -> {
                        LOG.info("Topic view '{}' not found", name);
                        return toolResult("Topic view '%s' not found", name);
                    }))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Get topic view completed successfully for view: '{}'",
                        name))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String name, TopicView topicView) {

        final String specification = topicView.getSpecification();
        final Set<String> roles = topicView.getRoles();

        LOG.info(
            "Successfully retrieved topic view '{}' with specification: '{}', roles: {}",
            name,
            specification,
            roles);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        VIEW_NAME, name,
                        "specification", specification,
                        "roles", roles,
                        "exists", true)));
        }
        catch (JsonProcessingException e) {
            LOG.error(
                "Error serializing result for topic view: '{}'",
                name,
                e);
            return toolError(
                "Error serializing topic view result: %s",
                e.getMessage());
        }

    }

}
