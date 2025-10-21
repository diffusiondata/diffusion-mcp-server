/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessiontrees;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Unit tests for ListSessionTreeBranchesTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListSessionTreeBranchesToolTest {

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
    private static final String TOOL_NAME = "list_session_tree_branches";

    @BeforeEach
    void setUp() {
        toolSpec = ListSessionTreeBranchesTool.create(sessionManager);
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
                .contains("Lists all session tree branches that have branch mapping tables configured");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListSessionTreeBranches_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of("market/prices", "users/sessions", "system/monitoring");

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("sessionTreeBranches")
                            .contains("branchCount")
                            .contains("listed")
                            .contains("market/prices")
                            .contains("users/sessions")
                            .contains("system/monitoring")
                            .contains("\"branchCount\":3");
                })
                .verifyComplete();

        verify(sessionTrees).listSessionTreeBranchesWithMappings();
    }

    @Test
    void testListSessionTreeBranches_singleBranch() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of("market/prices");

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("market/prices")
                            .contains("\"branchCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_emptyList() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of();

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"branchCount\":0")
                            .contains("listed");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_manyBranches() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of(
                "a/b/c",
                "d/e/f",
                "g/h/i",
                "j/k/l",
                "m/n/o"
        );

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"branchCount\":5");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_nestedPaths() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of(
                "root",
                "root/level1",
                "root/level1/level2",
                "root/level1/level2/level3"
        );

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("root")
                            .contains("root/level1")
                            .contains("root/level1/level2")
                            .contains("root/level1/level2/level3")
                            .contains("\"branchCount\":4");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_rootBranch() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of("");

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"branchCount\":1")
                            .contains("listed");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_branchesWithSpecialCharacters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<String> branches = List.of(
                "market/prices-2024",
                "users/sessions_active",
                "system/monitoring.metrics"
        );

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("market/prices-2024")
                            .contains("users/sessions_active")
                            .contains("system/monitoring.metrics");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

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
    void testListSessionTreeBranches_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<List<String>> neverCompletingFuture = new CompletableFuture<>();
        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("timed out");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_genericError() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<List<String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new SessionException("Connection error"));
        when(sessionTrees.listSessionTreeBranchesWithMappings())
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
                            .contains(ListSessionTreeBranchesTool.TOOL_NAME)
                            .contains("Connection error");
                })
                .verifyComplete();
    }

    @Test
    void testListSessionTreeBranches_permissionRestriction() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // User only has permission to see limited branches
        List<String> branches = List.of("public/data");

        when(sessionTrees.listSessionTreeBranchesWithMappings())
                .thenReturn(CompletableFuture.completedFuture(branches));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("public/data")
                            .contains("\"branchCount\":1");
                })
                .verifyComplete();
    }
}
