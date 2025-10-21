/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_SELECTORS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_VIEWS_ADVANCED;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolError;
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
 * Create topic view tool.
 * <p>
 * Creates a new named topic view using a topic view specification.
 *
 * @author DiffusionData Limited
 */
final class CreateTopicViewTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(CreateTopicViewTool.class);

    static final String TOOL_NAME = "create_topic_view";

    private static final String TOOL_DESCRIPTION =
        "Creates a new named topic view using a topic view specification. " +
            "If a view with the same name already exists, it will be replaced by a view with the new specification. " +
            "Needs MODIFY_TOPIC_VIEW permission. " +
            "See the " + TOPIC_VIEWS +
            " context to understand topic views. See the " +
            TOPIC_VIEWS_ADVANCED +
            " context for more advanced uses, and see the " + TOPIC_SELECTORS +
            " context before attempting to specify a topic view.";

    private static final String VIEW_NAME = "name";
    private static final String SPECIFICATION = "specification";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                VIEW_NAME,
                stringProperty("The name of the topic view."))
            .property(
                SPECIFICATION,
                stringProperty(
                    "The topic view specification string using the topic view DSL syntax " +
                    "(e.g., 'map ?sensors// to dashboard/<path(1)>')"))
            .required(VIEW_NAME, SPECIFICATION)
            .additionalProperties(false)
            .build();

    private CreateTopicViewTool() {
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

                final Map<String, Object> arguments = request.arguments();

                // Extract parameters
                final String name = stringArgument(arguments, VIEW_NAME);
                // avoid trimming
                final String specification = (String)arguments.get(SPECIFICATION);
                if (specification == null) {
                    return monoToolError("No specification provided");
                }

                LOG.info(
                    "Creating topic view: '{}' with specification: '{}'",
                    name,
                    specification);

                return Mono
                    .fromFuture(
                        session.feature(Topics.class)
                            .createTopicView(name, specification))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Topic view creation completed successfully for view: '{}'",
                        name))
                    .map(CreateTopicViewTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, name), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(TopicView topicView) {

        final String name = topicView.getName();
        final String specification = topicView.getSpecification();
        final Set<String> roles = topicView.getRoles();

        LOG.info(
            "Successfully created topic view '{}' with specification: '{}', roles: {}",
            name,
            specification,
            roles);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        VIEW_NAME, name,
                        SPECIFICATION, specification,
                        "roles", roles,
                        "status", "created")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing result for topic view: '{}'",
                name,
                ex);
            return toolError(
                "Error serializing topic view result: %s",
                ex.getMessage());
        }
    }

}