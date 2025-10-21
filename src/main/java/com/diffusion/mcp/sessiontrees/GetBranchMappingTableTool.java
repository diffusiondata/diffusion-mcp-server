/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSION_TREES;
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
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.BranchMappingTable;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import reactor.core.publisher.Mono;

/**
 * Get branch mapping table tool.
 * <p>
 * Retrieves a session tree branch mapping table by its session tree branch
 * path. Returns an empty table if no mappings exist for the specified branch.
 *
 * @author DiffusionData Limited
 */
final class GetBranchMappingTableTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(GetBranchMappingTableTool.class);

    static final String TOOL_NAME = "get_branch_mapping_table";

    private static final String TOOL_DESCRIPTION =
        "Retrieves a session tree branch mapping table by its session tree branch path. " +
            "Returns the table with all its branch mappings, or an empty table if no mappings " +
            "exist for the specified session tree branch. " +
            "Needs READ_TOPIC permission for the session tree branch. " +
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
                    "The session tree branch path to retrieve the mapping table for " +
                    "(e.g., 'market/prices')"))
            .required(SESSION_TREE_BRANCH)
            .additionalProperties(false)
            .build();

    private GetBranchMappingTableTool() {
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
                    "Retrieving branch mapping table for session tree branch: '{}'",
                    sessionTreeBranch);

                return Mono
                    .fromFuture(
                        session.feature(SessionTrees.class)
                            .getBranchMappingTable(sessionTreeBranch))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.debug(
                        "Branch mapping table retrieval completed successfully for session tree branch: '{}'",
                        sessionTreeBranch))
                    .map(GetBranchMappingTableTool::createResult)
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, sessionTreeBranch), ex, LOG));
            })
            .build();
    }

    private static CallToolResult createResult(
        BranchMappingTable branchMappingTable) {

        final String sessionTreeBranch =
            branchMappingTable.getSessionTreeBranch();

        final int mappingCount = branchMappingTable.getBranchMappings().size();

        LOG.info(
            "Successfully retrieved branch mapping table for session tree branch '{}' with {} mappings",
            sessionTreeBranch,
            mappingCount);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        SESSION_TREE_BRANCH, sessionTreeBranch,
                        "mappingCount", mappingCount,
                        "branchMappings", branchMappingTable.getBranchMappings()
                            .stream()
                            .map(mapping -> Map.of(
                                "sessionFilter", mapping.getSessionFilter(),
                                "topicTreeBranch",
                                mapping.getTopicTreeBranch()))
                            .toList(),
                        "status", "retrieved")));
        }
        catch (JsonProcessingException ex) {
            LOG.error(
                "Error serializing result for branch mapping table: '{}'",
                sessionTreeBranch,
                ex);
            return toolError(
                "Error serializing branch mapping table result: %s",
                ex.getMessage());
        }
    }
}