/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSIONS;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.stringProperty;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.stringArgument;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.diffusion.mcp.tools.ToolUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl;
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl.SessionFetchRequest;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get Diffusion sessions tool using the SessionFetchRequest API.
 *
 * @author DiffusionData Limited
 */
final class GetSessionsTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetSessionsTool.class);

    static final String TOOL_NAME = "get_sessions";

    private static final String TOOL_DESCRIPTION =
        "Retrieves a list of all currently connected session IDs from the Diffusion server. " +
            "Optionally accepts a session filter to limit results to sessions matching specific criteria. " +
            "Need VIEW_SESSION permission. " +
            "Get the " + SESSIONS + " context to understand sessions.";

    private static final String FILTER = "filter";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                FILTER,
                stringProperty(
                    "Optional session filter expression to limit results to sessions matching specific criteria. " +
                    "If not provided, all sessions will be returned."))
            .additionalProperties(false)
            .build();


    private GetSessionsTool() {
    }

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

                LOG.info("Fetching session IDs using SessionFetchRequest API");

                final String filter =
                    stringArgument(request.arguments(), FILTER);

                return Mono
                    .fromFuture(() -> {
                        return createFetchRequest(session, filter).fetch();
                    })
                    .map(fetchResult -> {
                        final String filterMsg =
                            filter != null ? " matching filter '" + filter + "'"
                                : "";
                        LOG.info(
                            "Successfully fetched {} sessions{} (total selected: {})",
                            fetchResult.size(),
                            filterMsg,
                            fetchResult.totalSelected());

                        if (fetchResult.hasMore()) {
                            LOG.warn(
                                "Result set was truncated - there are more sessions available");
                        }

                        // Extract session IDs from the results
                        final List<String> sessionIds =
                            fetchResult.sessions()
                                .stream()
                                .map(sessionResult -> sessionResult.properties()
                                    .get(Session.SESSION_ID))
                                .toList();

                        try {
                            return OBJECT_MAPPER.writeValueAsString(sessionIds);
                        }
                        catch (JsonProcessingException e) {
                            throw new SessionException(
                                "Error serializing session IDs: " +
                                    e.getMessage());
                        }
                    })
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG
                        .debug("Session fetch completed successfully"))
                    .map(ToolUtils::toolResult)
                    .onErrorMap(TimeoutException.class, e -> ToolUtils.timex())
                    .onErrorResume(
                        ex -> ToolUtils.monoToolException(TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static SessionFetchRequest createFetchRequest(
        Session session,
        String filter) {

        SessionFetchRequest fetchRequest =
            session.feature(InternalClientControl.class)
                .sessionFetchRequest()
                .withProperties(Session.SESSION_ID);

        // Apply filter if provided
        if (filter != null) {
            fetchRequest = fetchRequest.filter(filter);
        }
        return fetchRequest;
    }
}