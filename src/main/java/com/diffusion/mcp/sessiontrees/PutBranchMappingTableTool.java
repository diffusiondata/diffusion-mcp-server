/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.prompts.ContextGuides.SESSION_TREES;
import static com.diffusion.mcp.tools.JsonSchemas.arrayProperty;
import static com.diffusion.mcp.tools.JsonSchemas.jsonSchemaBuilder;
import static com.diffusion.mcp.tools.JsonSchemas.objectProperty;
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

import java.util.List;
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
 * Put branch mapping table tool.
 * <p>
 * Creates or replaces a session tree branch mapping table. A branch mapping
 * table defines how session paths are mapped to topic paths for different
 * sessions based on session filters.
 *
 * @author DiffusionData Limited
 */
final class PutBranchMappingTableTool {

    private static final Logger LOG =
        LoggerFactory.getLogger(PutBranchMappingTableTool.class);

    static final String TOOL_NAME = "put_branch_mapping_table";

    private static final String TOOL_DESCRIPTION =
        "Creates or replaces a session tree branch mapping table. " +
            "A branch mapping table defines how session paths are mapped to topic " +
            "paths for different sessions based on session filters. If a table " +
            "with the same session tree branch already exists, it will be replaced. " +
            "Needs MODIFY_TOPIC permission for the sessin tree branch and EXPOSE_BRANCH " +
            "permission for each branch mapping of the table and any existing table. " +
            "Get the " + SESSION_TREES +
            " context to understand session trees.";

    /*
     * Parameters.
     */
    private static final String SESSION_TREE_BRANCH = "sessionTreeBranch";
    private static final String BRANCH_MAPPINGS = "branchMappings";
    private static final String SESSION_FILTER = "sessionFilter";
    private static final String TOPIC_TREE_BRANCH = "topicTreeBranch";

    /**
     * Tool input schema.
     */
    private static final JsonSchema INPUT_SCHEMA =
        jsonSchemaBuilder()
            .property(
                SESSION_TREE_BRANCH,
                stringProperty(
                    "The session tree branch path that this mapping table applies to " +
                        "(e.g., market/prices)"))
            .property(
                BRANCH_MAPPINGS,
                arrayProperty(
                    objectProperty(
                        Map.of(
                            SESSION_FILTER,
                            stringProperty(
                                "Session filter expression (e.g., USER_TIER is '1' or $Country is 'DE')"),
                            TOPIC_TREE_BRANCH,
                            stringProperty(
                                "Target topic tree branch for sessions matching the filter (e.g., backend/discounted_prices)")),
                        List.of(SESSION_FILTER, TOPIC_TREE_BRANCH), // required
                        false,
                        "A single branch mapping, session filter to topic tree branch"),
                    1, // minItems
                    20, // maxItems
                    null, // uniqueItems = false
                    "Array of branch mappings, processed in order until a session filter matches"))
            .required(SESSION_TREE_BRANCH, BRANCH_MAPPINGS)
            .additionalProperties(false)
            .build();

    private PutBranchMappingTableTool() {
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

                final Map<String, Object> arguments = request.arguments();

                final BranchMappingTable branchMappingTable;
                try {
                    branchMappingTable = createBranchMappingTable(arguments);
                }
                catch (Exception ex) {
                    final String message = ex.getMessage();
                    LOG.error("Failed to create branch mapping table : {}",
                        message);
                    return monoToolError(
                        "Failure to create branch mapping table '%s'", message);
                }

                final String sessionTreeBranch =
                    branchMappingTable.getSessionTreeBranch();

                LOG.info(
                    "About to call Diffusion putBranchMappingTable for: {}",
                    sessionTreeBranch);

                return Mono
                    .fromFuture(
                        session.feature(SessionTrees.class)
                            .putBranchMappingTable(branchMappingTable))
                    .doOnSubscribe(s -> LOG.info(
                        "Mono subscribed for: {}",
                        sessionTreeBranch))
                    .timeout(TEN_SECONDS)
                    .doOnNext(result -> LOG.info(
                        "Diffusion call completed successfully for: '{}'",
                        sessionTreeBranch))
                    .thenReturn(createResult(branchMappingTable))
                    .onErrorMap(TimeoutException.class, e -> timex())
                    .onErrorResume(ex -> monoToolException(
                        toolOperation(TOOL_NAME, sessionTreeBranch), ex, LOG));
            })
            .build();
    }

    private static BranchMappingTable createBranchMappingTable(
        Map<String, Object> arguments) {

        final String sessionTreeBranch =
            stringArgument(arguments, SESSION_TREE_BRANCH);

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> branchMappingsInput =
            (List<Map<String, String>>) arguments.get(BRANCH_MAPPINGS);

        LOG.info(
            "Creating branch mapping table for session tree branch: '{}' with {} mappings",
            sessionTreeBranch,
            branchMappingsInput.size());

        // Build the branch mapping table
        final BranchMappingTable.Builder builder =
            Diffusion.newBranchMappingTableBuilder();

        for (Map<String, String> mappingInput : branchMappingsInput) {

            final String sessionFilter =
                mappingInput.get(SESSION_FILTER).trim();
            final String topicTreeBranch =
                mappingInput.get(TOPIC_TREE_BRANCH).trim();

            LOG.debug(
                "Adding branch mapping - filter: '{}', target: '{}'",
                sessionFilter,
                topicTreeBranch);

            builder.addBranchMapping(
                sessionFilter,
                topicTreeBranch);
        }

        return builder.create(sessionTreeBranch);

    }

    private static CallToolResult createResult(
        BranchMappingTable branchMappingTable) {

        final String sessionTreeBranch =
            branchMappingTable.getSessionTreeBranch();

        final int mappingCount = branchMappingTable.getBranchMappings().size();

        LOG.info(
            "Successfully created branch mapping table for session tree branch '{}' with {} mappings",
            sessionTreeBranch,
            mappingCount);

        try {
            return toolResult(
                OBJECT_MAPPER.writeValueAsString(
                    Map.of(
                        SESSION_TREE_BRANCH, sessionTreeBranch,
                        "mappingCount", mappingCount,
                        BRANCH_MAPPINGS, branchMappingTable.getBranchMappings()
                            .stream()
                            .map(mapping -> Map.of(
                                SESSION_FILTER,
                                mapping.getSessionFilter(),
                                TOPIC_TREE_BRANCH,
                                mapping.getTopicTreeBranch()))
                            .toList(),
                        "status", "created")));
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