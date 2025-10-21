/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

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
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.TopicMetricCollector;
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
 * Unit tests for CreateTopicMetricCollectorTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class CreateTopicMetricCollectorToolTest {

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
    private static final String TOOL_NAME = "create_topic_metric_collector";

    @BeforeEach
    void setUp() {
        toolSpec = CreateTopicMetricCollectorTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates a new topic metric collector");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testCreateTopicMetricCollector_withMinimalArguments() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("topicSelector", "?sensors//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
                            .contains("Successfully created topic metric collector")
                            .contains("Name: test-collector")
                            .contains("Topic Selector: ?sensors//");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }

    @Test
    void testCreateTopicMetricCollector_withAllArguments() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "advanced-collector");
        arguments.put("topicSelector", ">games/*/scores");
        arguments.put("exportToPrometheus", true);
        arguments.put("maximumGroups", 50);
        arguments.put("groupByTopicType", true);
        arguments.put("groupByTopicView", true);
        arguments.put("groupByPathPrefixParts", 2);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
                            .contains("Topic Selector: >games/*/scores")
                            .contains("Exports to Prometheus: true")
                            .contains("Maximum Groups: 50")
                            .contains("Groups by Topic Type: true")
                            .contains("Groups by Topic View: true")
                            .contains("Groups by Path Prefix Parts: 2");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }

    @Test
    void testCreateTopicMetricCollector_withUnlimitedGroups() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "unlimited-collector");
        arguments.put("topicSelector", "?all//");
        // No maximumGroups specified = defaults to Integer.MAX_VALUE

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();

                    TextContent content = (TextContent) callResult.content().get(0);
                    // When not specified, Diffusion defaults to Integer.MAX_VALUE
                    assertThat(content.text())
                            .contains("Maximum Groups: 2147483647");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }

    @Test
    void testCreateTopicMetricCollector_withOnlyGroupByPathPrefix() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "path-grouped-collector");
        arguments.put("topicSelector", "sensors/**");
        arguments.put("groupByPathPrefixParts", 3);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
                            .contains("Groups by Path Prefix Parts: 3");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }

    @Test
    void testCreateTopicMetricCollector_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("topicSelector", "?sensors//");

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
    void testCreateTopicMetricCollector_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("topicSelector", "?sensors//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Diffusion error"));
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
                            .contains(CreateTopicMetricCollectorTool.TOOL_NAME)
                            .contains("test-collector");
                })
                .verifyComplete();
    }

    @Test
    void testCreateTopicMetricCollector_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-collector");
        arguments.put("topicSelector", "?sensors//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
    void testCreateTopicMetricCollector_withBooleanFalseValues() {
        // Arrange - test that false values don't trigger the options
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "false-bools-collector");
        arguments.put("topicSelector", "?test//");
        arguments.put("exportToPrometheus", false);
        arguments.put("groupByTopicType", false);
        arguments.put("groupByTopicView", false);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
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
                            .contains("Exports to Prometheus: false")
                            .contains("Groups by Topic Type: false")
                            .contains("Groups by Topic View: false");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }

    @Test
    void testCreateTopicMetricCollector_withZeroPathPrefixParts() {
        // Arrange - test that 0 path prefix parts doesn't appear in output
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "zero-prefix-collector");
        arguments.put("topicSelector", "?test//");
        arguments.put("groupByPathPrefixParts", 0);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.putTopicMetricCollector(any(TopicMetricCollector.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();

                    TextContent content = (TextContent) callResult.content().get(0);
                    // Should not contain path prefix line when value is 0
                    assertThat(content.text())
                            .doesNotContain("Groups by Path Prefix Parts");
                })
                .verifyComplete();

        verify(metrics).putTopicMetricCollector(any(TopicMetricCollector.class));
    }
}