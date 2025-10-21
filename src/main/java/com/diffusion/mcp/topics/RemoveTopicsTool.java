/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.TOPIC_SELECTORS;
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
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.TopicSelector;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Remove topics tool.
 * <p>
 * Removes topics matching the specified topic selector from the connected
 * Diffusion server.
 *
 * @author DiffusionData Limited
 */
final class RemoveTopicsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveTopicsTool.class);

    static final String TOOL_NAME = "remove_topics";

    private static final String TOOL_DESCRIPTION =
        "Removes topics matching the specified topic selector from the connected Diffusion server. " +
            "Returns the number of topics actually removed. " +
            "Only topics to which the caller has MODIFY_TOPIC permission will be removed. " +
            "Se the " + TOPIC_SELECTORS +
            " context for how to specifytopic selectors.";

    private static final String TOPIC_SELECTOR = "topicSelector";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                TOPIC_SELECTOR,
                stringProperty(
                    "Topic selector expression specifying the topics to remove. " +
                    "Use descendant qualifier '//' to remove complete branches (topic and all descendants), " +
                    "or '/' to remove only descendant topics."))
            .required(TOPIC_SELECTOR)
            .additionalProperties(false)
            .build();

    private RemoveTopicsTool() {
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

                final Session session = sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                final String topicSelector =
                    stringArgument(request.arguments(), TOPIC_SELECTOR);

                final TopicSelector selector;
                try {
                    selector = Diffusion.topicSelectors().parse(topicSelector);
                }
                catch (Exception ex) {
                    return monoToolException("Invalid topic selector", ex, LOG);
                }

                LOG.info(
                    "Starting remove topics operation for selector: {}",
                    selector.getExpression());

                return removeTopics(session, selector);
            })
            .build();
    }

    private static Mono<CallToolResult> removeTopics(
        Session session,
        TopicSelector topicSelector) {

        return Mono
            .fromFuture(
                session.feature(TopicControl.class).removeTopics(topicSelector))
            .timeout(TEN_SECONDS)
            .doOnNext(result -> LOG.info(
                "Successfully removed topics for selector: {} - removed count: {}",
                topicSelector.getExpression(),
                result.getRemovedCount()))
            .<CallToolResult> map(removalResult -> {
                try {
                    return toolResult(
                        OBJECT_MAPPER.writeValueAsString(
                            Map.of(
                                TOPIC_SELECTOR,
                                topicSelector.getExpression(),
                                "removedCount",
                                removalResult.getRemovedCount())));
                }
                catch (JsonProcessingException e) {
                    LOG.error(
                        "Error serializing result for topic selector: {}",
                        topicSelector.getExpression(),
                        e);
                    return toolError(
                        "Error serializing removal result: %s",
                        e.getMessage());
                }
            })
            .onErrorMap(TimeoutException.class, e -> timex())
            .onErrorResume(ex -> monoToolException(
                toolOperation(TOOL_NAME, topicSelector.getExpression()), ex, LOG));
    }
}