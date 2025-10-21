/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.BranchMappingTable;
import com.pushtechnology.diffusion.client.features.control.topics.SessionTrees.InvalidBranchMappingException;
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
 * Unit tests for PutBranchMappingTableTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class PutBranchMappingTableToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SessionTrees sessionTrees;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "put_branch_mapping_table";
    private static final String SESSION_TREE_BRANCH = "market/prices";

    @BeforeEach
    void setUp() {
        toolSpec = PutBranchMappingTableTool.create(sessionManager);
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
                .contains("Creates or replaces a session tree branch mapping table");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testPutBranchMappingTable_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "$Principal is 'admin'", "topicTreeBranch", "topics/admin"),
                Map.of("sessionFilter", "$Principal is 'user'", "topicTreeBranch", "topics/user")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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
                            .contains("created")
                            .contains("$Principal is 'admin'")
                            .contains("topics/admin")
                            .contains("$Principal is 'user'")
                            .contains("topics/user")
                            .contains("\"mappingCount\":2");
                })
                .verifyComplete();

        verify(sessionTrees).putBranchMappingTable(any(BranchMappingTable.class));
    }

    @Test
    void testPutBranchMappingTable_singleMapping() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "topics/default")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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
                            .contains("ALL")
                            .contains("topics/default");
                })
                .verifyComplete();
    }

    @Test
    void testPutBranchMappingTable_multipleMappings() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "USER_TIER is '1'", "topicTreeBranch", "backend/tier1"),
                Map.of("sessionFilter", "USER_TIER is '2'", "topicTreeBranch", "backend/tier2"),
                Map.of("sessionFilter", "USER_TIER is '3'", "topicTreeBranch", "backend/tier3")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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
    void testPutBranchMappingTable_withWhitespace() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "  $Principal is 'admin'  ", "topicTreeBranch", "  topics/admin  ")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("$Principal is 'admin'")
                            .contains("topics/admin");
                })
                .verifyComplete();
    }

    @Test
    void testPutBranchMappingTable_complexSessionFilters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "$Principal is 'admin' and $ClientIP is '192.168.1.1'",
                       "topicTreeBranch", "topics/admin/local"),
                Map.of("sessionFilter", "$Country is 'DE' or $Country is 'FR'",
                       "topicTreeBranch", "topics/europe")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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
                            .contains("topics/admin/local")
                            .contains("$Country is 'DE' or $Country is 'FR'")
                            .contains("topics/europe");
                })
                .verifyComplete();
    }

    @Test
    void testPutBranchMappingTable_differentBranchPath() {
        // Arrange
        setupExchange();
        setupMocks();

        String differentBranch = "users/sessions";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", differentBranch);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "backend/sessions")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

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

        verify(sessionTrees).putBranchMappingTable(any(BranchMappingTable.class));
    }

    @Test
    void testPutBranchMappingTable_rootBranch() {
        // Arrange
        setupExchange();
        setupMocks();

        String rootBranch = "";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", rootBranch);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "topics")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("created");
                })
                .verifyComplete();
    }

    @Test
    void testPutBranchMappingTable_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "topics/default")
        ));

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
    void testPutBranchMappingTable_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "topics/default")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<Void> neverCompletingFuture = new CompletableFuture<>();
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
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
    void testPutBranchMappingTable_invalidMapping() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "INVALID FILTER", "topicTreeBranch", "topics/default")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new InvalidBranchMappingException("Invalid session filter syntax"));
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
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
                            .contains("Invalid")
                            .contains(SESSION_TREE_BRANCH);
                })
                .verifyComplete();
    }

    @Test
    void testPutBranchMappingTable_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", SESSION_TREE_BRANCH);
        arguments.put("branchMappings", List.of(
                Map.of("sessionFilter", "ALL", "topicTreeBranch", "topics/default")
        ));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied: insufficient privileges"));
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
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
