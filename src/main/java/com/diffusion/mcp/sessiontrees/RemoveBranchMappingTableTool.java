/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSION_TREES;
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
import static java.util.Collections.emptyList;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.BranchMappingTable;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Remove branch mapping table tool.
 * <p>
 * Removes a session tree branch mapping table by creating an empty table. This
 * effectively removes all mappings for the specified session tree branch.
 *
 * @author DiffusionData Limited
 */
final class RemoveBranchMappingTableTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(RemoveBranchMappingTableTool.class);

    static final String TOOL_NAME = "remove_branch_mapping_table";

    private static final String TOOL_DESCRIPTION =
        "Removes a session tree branch mapping table by clearing all its mappings. " +
            "After removal, sessions accessing the specified session tree branch will " +
            "use the identical topic path instead of any custom mappings. " +
            "Needs EXPOSE_BRANCH permission for each branch of the existing table. " +
            "Get the " + SESSION_TREES +
            " context to understand session trees.";

    private static final String SESSION_TREE_BRANCH = "sessionTreeBranch";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                SESSION_TREE_BRANCH,
                stringProperty(
                    "The session tree branch path to remove the mapping table for " +
                    "(e.g., 'market/prices')"))
            .required(SESSION_TREE_BRANCH)
            .additionalProperties(false)
            .build();

    private RemoveBranchMappingTableTool() {
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
                final Session session =
                    sessionManager.get(exchange.sessionId());
                if (session == null) {
                    return noActiveSession();
                }

                // Extract parameters
                final String sessionTreeBranch =
                    stringArgument(request.arguments(), SESSION_TREE_BRANCH);

                LOG.info(
                    "Removing branch mapping table for session tree branch: '{}'",
                    sessionTreeBranch);

                // Create an empty branch mapping table to remove the existing
                // one
                final BranchMappingTable emptyTable;
                try {
                    emptyTable = Diffusion.newBranchMappingTableBuilder()
                        .create(sessionTreeBranch);
                }
                catch (Exception e) {
                    LOG.error(
                        "Error creating empty branch mapping table for session tree branch: '{}'",
                        sessionTreeBranch,
                        e);
                    return monoToolError(
                        "Error creating empty branch mapping table: %s",
                        e.getMessage());
                }

                return Mono
                    .fromFuture(
                        session.feature(SessionTrees.class)
                            .putBranchMappingTable(emptyTable))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Branch mapping table removal completed successfully for session tree branch: '{}'",
                        sessionTreeBranch))
                    .then(Mono
                        .fromCallable(() -> createResult(sessionTreeBranch)))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, sessionTreeBranch), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(String sessionTreeBranch) {

        LOG.info(
            "Successfully removed branch mapping table for session tree branch '{}'",
            sessionTreeBranch);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        SESSION_TREE_BRANCH, sessionTreeBranch,
                        "mappingCount", 0,
                        "branchMappings", emptyList(),
                        "status", "removed")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing result for branch mapping table removal: '{}'",
                sessionTreeBranch,
                ex);
            return toolError(
                "Error serializing branch mapping table removal result: %s",
                ex.getMessage());
        }
    }
}