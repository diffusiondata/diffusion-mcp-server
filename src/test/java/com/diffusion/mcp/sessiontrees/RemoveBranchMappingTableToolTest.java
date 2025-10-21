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
import com.pushtechnology.diffusion.client.session.PermissionsException;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for RemoveBranchMappingTableTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class RemoveBranchMappingTableToolTest {

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
    private static final String TOOL_NAME = "remove_branch_mapping_table";

    @BeforeEach
    void setUp() {
        toolSpec = RemoveBranchMappingTableTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Removes a session tree branch mapping table");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testRemoveBranchMappingTable_success() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "market/prices");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);

                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"sessionTreeBranch\":\"market/prices\"")
                            .contains("\"mappingCount\":0")
                            .contains("\"branchMappings\":[]")
                            .contains("\"status\":\"removed\"");
                })
                .verifyComplete();

        verify(sessionTrees).putBranchMappingTable(any(BranchMappingTable.class));
    }

    @Test
    void testRemoveBranchMappingTable_withRootPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
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
                            .contains("\"sessionTreeBranch\":\"\"")
                            .contains("\"status\":\"removed\"");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_withNestedPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "data/financial/stocks/us");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
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
                            .contains("\"sessionTreeBranch\":\"data/financial/stocks/us\"")
                            .contains("\"status\":\"removed\"");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "market/prices");

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
    void testRemoveBranchMappingTable_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "market/prices");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Server error"));
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
                            .contains("Server error")
                            .contains("market/prices");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_permissionDenied() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "restricted/branch");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied for operation"));
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
                            .contains(RemoveBranchMappingTableTool.TOOL_NAME)
                            .contains("Permission denied")
                            .contains("restricted/branch");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "market/prices");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);

        // Create a future that never completes (simulates timeout)
        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .expectNextMatches(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    return content.text().contains("timed out after 10 seconds") &&
                           content.text().contains("market/prices");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_withSpecialCharactersInPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionTreeBranch", "market/special-chars_123");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
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
                            .contains("\"sessionTreeBranch\":\"market/special-chars_123\"")
                            .contains("\"status\":\"removed\"");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveBranchMappingTable_multipleCalls() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("sessionTreeBranch", "market/prices");

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("sessionTreeBranch", "market/volumes");

        CallToolRequest request1 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments1)
                .build();

        CallToolRequest request2 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments2)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SessionTrees.class)).thenReturn(sessionTrees);
        when(sessionTrees.putBranchMappingTable(any(BranchMappingTable.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request1);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request2);

        // Assert
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("market/prices");
                })
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("market/volumes");
                })
                .verifyComplete();

        verify(sessionTrees, org.mockito.Mockito.times(2))
                .putBranchMappingTable(any(BranchMappingTable.class));
    }
}