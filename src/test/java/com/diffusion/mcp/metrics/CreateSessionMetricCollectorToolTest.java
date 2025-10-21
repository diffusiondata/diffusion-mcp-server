/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.SessionMetricCollector;
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
 * Unit tests for CreateSessionMetricCollectorTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class CreateSessionMetricCollectorToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Metrics metrics;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "create_session_metric_collector";

    @BeforeEach
    void setUp() {
        toolSpec = CreateSessionMetricCollectorTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates a new session metric collector");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testCreateSessionMetricCollector_withMinimalArguments() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains("Successfully created session metric collector")
                            .contains("Name: test-collector")
                            .contains("Session Filter: all")
                            .contains("Exports to Prometheus: false")
                            .contains("Removes Metrics with No Matches: false")
                            .contains("The collector is now active");

                    // Maximum Groups shows either "unlimited" or the default max value
                    assertThat(content.text())
                            .containsAnyOf("Maximum Groups: unlimited",
                                          "Maximum Groups: 2147483647");
                })
                .verifyComplete();

        verify(metrics).putSessionMetricCollector(any(SessionMetricCollector.class));
    }

    @Test
    void testCreateSessionMetricCollector_withAllArguments() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "advanced-collector");
        arguments.put("sessionFilter", "$Principal is \"user1\"");
        arguments.put("groupByProperties", "$Principal,$ClientIP");
        arguments.put("exportToPrometheus", true);
        arguments.put("maximumGroups", 100);
        arguments.put("removeMetricsWithNoMatches", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains("Name: advanced-collector")
                            .contains("Session Filter: $Principal is \"user1\"")
                            .contains("Exports to Prometheus: true")
                            .contains("Maximum Groups: 100")
                            .contains("Removes Metrics with No Matches: true")
                            .contains("Group By Properties: $Principal, $ClientIP");
                })
                .verifyComplete();

        verify(metrics).putSessionMetricCollector(any(SessionMetricCollector.class));
    }

    @Test
    void testCreateSessionMetricCollector_withExportToPrometheusFalse() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("exportToPrometheus", false);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Exports to Prometheus: false");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withRemoveMetricsWithNoMatchesFalse() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("removeMetricsWithNoMatches", false);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Removes Metrics with No Matches: false");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withMaximumGroupsZero() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("maximumGroups", 0);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        // No need to mock metrics feature - error occurs during building

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Zero is invalid, should return an error result
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid arguments:")
                            .contains("maximumGroups must be positive");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withMaximumGroupsOne() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("maximumGroups", 1);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Maximum Groups: 1");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withNegativeMaximumGroups() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("maximumGroups", -1);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        // No need to mock metrics feature - error occurs during building

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Negative is invalid, should return an error result
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid arguments:")
                            .contains("maximumGroups must be positive");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withEmptyGroupByProperties() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("groupByProperties", "  ,  ,  "); // Empty after trimming

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    // Should not contain Group By Properties line
                    assertThat(content.text()).doesNotContain("Group By Properties:");
                })
                .verifyComplete();

        verify(metrics).putSessionMetricCollector(any(SessionMetricCollector.class));
    }

    @Test
    void testCreateSessionMetricCollector_withNullGroupByProperties() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("groupByProperties", null);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metrics).putSessionMetricCollector(any(SessionMetricCollector.class));
    }

    @Test
    void testCreateSessionMetricCollector_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");

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
    void testCreateSessionMetricCollector_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Diffusion error"));
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains(CreateSessionMetricCollectorTool.TOOL_NAME)
                            .contains("test-collector");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Create a future that never completes (simulates timeout)
        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .expectNextMatches(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    return content.text().contains("timed out");
                })
                .verifyComplete();
    }

    @Test
    void testGroupByProperties_trimAndFilter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("groupByProperties", " $Principal , , $ClientIP ,  ");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains("Group By Properties: $Principal, $ClientIP");
                })
                .verifyComplete();

        verify(metrics).putSessionMetricCollector(any(SessionMetricCollector.class));
    }

    @Test
    void testGroupByProperties_singleProperty() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("groupByProperties", "$Principal");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains("Group By Properties: $Principal");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withComplexSessionFilter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "complex-filter-collector");
        arguments.put("sessionFilter", "$Principal is \"admin\" and $ClientType is \"JAVA\"");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
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
                            .contains("Name: complex-filter-collector")
                            .contains("Session Filter: $Principal is \"admin\" and $ClientType is \"JAVA\"");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_withLargeMaximumGroups() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("sessionFilter", "all");
        arguments.put("maximumGroups", 10000);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Maximum Groups: 10000");
                })
                .verifyComplete();
    }

    @Test
    void testCreateSessionMetricCollector_multipleCallsSucceed() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector-1");
        arguments.put("sessionFilter", "all");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putSessionMetricCollector(any(SessionMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act - make multiple calls
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request);

        // Assert
        StepVerifier.create(result1)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        verify(metrics, times(2)).putSessionMetricCollector(any(SessionMetricCollector.class));
    }
}