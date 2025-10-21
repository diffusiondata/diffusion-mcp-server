/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.BranchMapping;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.BranchMappingTable;
import com.pushtechnology.diffusion.client.session.PermissionsException;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for GetBranchMappingTableTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetBranchMappingTableToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SessionTrees sessionTrees;

    @Mock
    private BranchMappingTable branchMappingTable;

    @Mock
    private BranchMapping branchMapping1;

    @Mock
    private BranchMapping branchMapping2;

    @Mock
    private BranchMapping branchMapping3;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_branch_mapping_table";
    private static final String SESSION_TREE_BRANCH = "market/prices";

    @BeforeEach
    void setUp() {
        toolSpec = GetBranchMappingTableTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieves a session tree branch mapping table");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetBranchMappingTable_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter()).thenReturn("$Principal is 'admin'");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics/admin");

        when(branchMapping2.getSessionFilter()).thenReturn("$Principal is 'user'");
        when(branchMapping2.getTopicTreeBranch()).thenReturn("topics/user");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(SESSION_TREE_BRANCH);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1, branchMapping2));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_TREE_BRANCH)
                            .contains("mappingCount")
                            .contains("branchMappings")
                            .contains("retrieved")
                            .contains("$Principal is 'admin'")
                            .contains("topics/admin")
                            .contains("$Principal is 'user'")
                            .contains("topics/user");
                })
                .verifyComplete();

        verify(sessionTrees).getBranchMappingTable(SESSION_TREE_BRANCH);
    }

    @Test
    void testGetBranchMappingTable_singleMapping() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter()).thenReturn("$ClientIP is '192.168.1.1'");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics/local");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(SESSION_TREE_BRANCH);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"mappingCount\":1")
                            .contains("$ClientIP is '192.168.1.1'")
                            .contains("topics/local");
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_emptyMappings() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(SESSION_TREE_BRANCH);
        when(branchMappingTable.getBranchMappings()).thenReturn(List.of());

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"mappingCount\":0")
                            .contains(SESSION_TREE_BRANCH)
                            .contains("retrieved");
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_multipleMappings() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter()).thenReturn("$Principal is 'admin'");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics/admin");

        when(branchMapping2.getSessionFilter()).thenReturn("$Principal is 'user'");
        when(branchMapping2.getTopicTreeBranch()).thenReturn("topics/user");

        when(branchMapping3.getSessionFilter()).thenReturn("$Principal is 'guest'");
        when(branchMapping3.getTopicTreeBranch()).thenReturn("topics/guest");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(SESSION_TREE_BRANCH);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1, branchMapping2, branchMapping3));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"mappingCount\":3");
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_differentBranchPath() {
        // Arrange
        setupExchange();
        setupMocks();

        String differentBranch = "users/sessions";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", differentBranch);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter()).thenReturn("ALL");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics/all");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(differentBranch);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(differentBranch);
                })
                .verifyComplete();

        verify(sessionTrees).getBranchMappingTable(differentBranch);
    }

    @Test
    void testGetBranchMappingTable_complexSessionFilter() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter())
                .thenReturn("$Principal is 'admin' and $ClientIP is '192.168.1.1'");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics/admin/local");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(SESSION_TREE_BRANCH);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("$Principal is 'admin' and $ClientIP is '192.168.1.1'")
                            .contains("topics/admin/local");
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_rootBranch() {
        // Arrange
        setupExchange();
        setupMocks();

        String rootBranch = "";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", rootBranch);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(branchMapping1.getSessionFilter()).thenReturn("ALL");
        when(branchMapping1.getTopicTreeBranch()).thenReturn("topics");

        when(branchMappingTable.getSessionTreeBranch()).thenReturn(rootBranch);
        when(branchMappingTable.getBranchMappings())
                .thenReturn(List.of(branchMapping1));

        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenReturn(CompletableFuture.completedFuture(branchMappingTable));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("retrieved");
                })
                .verifyComplete();

        verify(sessionTrees).getBranchMappingTable(rootBranch);
    }

    @Test
    void testGetBranchMappingTable_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(null);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("No active Diffusion session");
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<BranchMappingTable> neverCompletingFuture = new CompletableFuture<>();
        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("timed out")
                            .contains(SESSION_TREE_BRANCH);
                })
                .verifyComplete();
    }

    @Test
    void testGetBranchMappingTable_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<BranchMappingTable> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied: insufficient privileges"));
        when(sessionTrees.getBranchMappingTable(anyString()))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Permission denied")
                            .contains(SESSION_TREE_BRANCH);
                })
                .verifyComplete();
    }
}