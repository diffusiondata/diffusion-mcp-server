/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSION_TREES;
import static com.diffusion.mcp.tools.ToolUtils.EMPTY_INPUT_SCHEMA;
import static com.diffusion.mcp.tools.ToolUtils.TEN_SECONDS;
import static com.diffusion.mcp.tools.ToolUtils.monoToolException;
import static com.diffusion.mcp.tools.ToolUtils.noActiveSession;
import static com.diffusion.mcp.tools.ToolUtils.timex;
import static com.diffusion.mcp.tools.ToolUtils.toolError;
import static com.diffusion.mcp.tools.ToolUtils.toolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * List session tree branches tool.
 * <p>
 * Lists all session tree branches that have branch mapping tables configured.
 * Only returns branches for which the calling session has READ_TOPIC
 * permission.
 *
 * @author DiffusionData Limited
 */
final class ListSessionTreeBranchesTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(ListSessionTreeBranchesTool.class);


    static final String TOOL_NAME = "list_session_tree_branches";

    private static final String TOOL_DESCRIPTION =
        "Lists all session tree branches that have branch mapping tables configured. " +
            "Returns branches in path order, and only includes branches for which the calling " +
            "session has READ_TOPIC permission. " +
            "Get the " + SESSION_TREES +
            " context to understand session trees.";

    private ListSessionTreeBranchesTool() {
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
                final Session session =
                    sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                LOG.info("Listing session tree branches with mappings");

                return Mono
                    .fromFuture(
                        session.feature(SessionTrees.class)
                            .listSessionTreeBranchesWithMappings())
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Session tree branches listing completed successfully, found {} branches",
                        result.size()))
                    .map(ListSessionTreeBranchesTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        TOOL_NAME, ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        List<String> sessionTreeBranches) {

        final int branchCount = sessionTreeBranches.size();

        LOG.info(
            "Successfully listed {} session tree branches with mappings",
            branchCount);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        "sessionTreeBranches", sessionTreeBranches,
                        "branchCount", branchCount,
                        "status", "listed")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing session tree branches list result",
                ex);
            return toolError(
                "Error serializing session tree branches list result: %s",
                ex.getMessage());
        }
    }
}
