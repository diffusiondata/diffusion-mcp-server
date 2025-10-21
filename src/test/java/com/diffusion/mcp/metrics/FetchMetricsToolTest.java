/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pushtechnology.diffusion.client.features.control.Metrics;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricSample;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricSampleCollection;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricsRequest;
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricsResult;
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
 * Unit tests for FetchMetricsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class FetchMetricsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Metrics metrics;

    @Mock
    private MetricsRequest metricsRequest;

    @Mock
    private MetricsResult metricsResult;

    @Mock
    private MetricSampleCollection sampleCollection;

    @Mock
    private MetricSample metricSample;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;
    private ObjectMapper objectMapper;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "fetch_metrics";

    @BeforeEach
    void setUp() {
        toolSpec = FetchMetricsTool.create(sessionManager);
        objectMapper = new ObjectMapper();
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Fetches metrics from the Diffusion server");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testFetchMetrics_withDefaults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        setupMockMetricsResult("server1");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);

                    try {
                        JsonNode json = objectMapper.readTree(content.text());
                        assertThat(json.get("serverCount").asInt()).isEqualTo(1);
                        assertThat(json.get("format").asText()).isEqualTo("summary");
                        assertThat(json.has("servers")).isTrue();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testFetchMetrics_currentServer() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("server", "current");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        when(metricsRequest.currentServer()).thenReturn(metricsRequest);
        setupMockMetricsResult("current-server");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metricsRequest).currentServer();
    }

    @Test
    void testFetchMetrics_specificServer() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("server", "server-xyz");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        when(metricsRequest.server("server-xyz")).thenReturn(metricsRequest);
        setupMockMetricsResult("server-xyz");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metricsRequest).server("server-xyz");
    }

    @Test
    void testFetchMetrics_withNamesFilter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("filter", "metric1,metric2,metric3");
        arguments.put("filterType", "names");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        when(metricsRequest.filter(anySet())).thenReturn(metricsRequest);
        setupMockMetricsResult("server1");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metricsRequest).filter(anySet());
    }

    @Test
    void testFetchMetrics_withRegexFilter() {
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("filter", ".*_count");
        arguments.put("filterType", "regex");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        when(metricsRequest.filter(any(Pattern.class))).thenReturn(metricsRequest);
        setupMockMetricsResult("server1");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metricsRequest).filter(any(Pattern.class));
    }

    @Test
    void testFetchMetrics_detailedFormat() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("format", "detailed");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        setupMockMetricsResultWithSamples("server1");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);

                    try {
                        JsonNode json = objectMapper.readTree(content.text());
                        assertThat(json.get("format").asText()).isEqualTo("detailed");
                        assertThat(json.get("servers").get("server1").has("collections")).isTrue();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testFetchMetrics_summaryFormat() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("format", "summary");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        setupMockMetricsResult("server1");

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);

                    try {
                        JsonNode json = objectMapper.readTree(content.text());
                        assertThat(json.get("format").asText()).isEqualTo("summary");
                        assertThat(json.get("servers").get("server1").has("summary")).isTrue();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testFetchMetrics_multipleServers() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMetricsChain();
        setupMockMetricsResultMultipleServers();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);

                    try {
                        JsonNode json = objectMapper.readTree(content.text());
                        assertThat(json.get("serverCount").asInt()).isEqualTo(2);
                        assertThat(json.get("servers").has("server1")).isTrue();
                        assertThat(json.get("servers").has("server2")).isTrue();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testFetchMetrics_noActiveSession() {
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
    void testFetchMetrics_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.metricsRequest()).thenReturn(metricsRequest);

        CompletableFuture<MetricsResult> neverCompletingFuture = new CompletableFuture<>();
        when(metricsRequest.fetch()).thenReturn(neverCompletingFuture);

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
    void testFetchMetrics_fetchError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.metricsRequest()).thenReturn(metricsRequest);

        CompletableFuture<MetricsResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Fetch failed"));
        when(metricsRequest.fetch()).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(FetchMetricsTool.TOOL_NAME);
                })
                .verifyComplete();
    }

    // Helper methods

    private void setupMetricsChain() {
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.metricsRequest()).thenReturn(metricsRequest);
    }

    private void setupMockMetricsResult(String serverName) {
        when(metricsResult.getServerNames()).thenReturn(Set.of(serverName));
        when(metricsResult.getMetrics(serverName)).thenReturn(List.of(sampleCollection));

        when(sampleCollection.getName()).thenReturn("test_metric");
        when(sampleCollection.getType()).thenReturn(Metrics.MetricType.COUNTER);
        when(sampleCollection.getUnit()).thenReturn("requests");
        when(sampleCollection.getSamples()).thenReturn(List.of());

        when(metricsRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(metricsResult));
    }

    private void setupMockMetricsResultWithSamples(String serverName) {
        when(metricsResult.getServerNames()).thenReturn(Set.of(serverName));
        when(metricsResult.getMetrics(serverName)).thenReturn(List.of(sampleCollection));

        when(sampleCollection.getName()).thenReturn("test_metric");
        when(sampleCollection.getType()).thenReturn(Metrics.MetricType.COUNTER);
        when(sampleCollection.getUnit()).thenReturn("requests");

        when(metricSample.getName()).thenReturn("sample1");
        when(metricSample.getValue()).thenReturn(100.0);
        when(metricSample.getTimestamp()).thenReturn(Optional.of(System.currentTimeMillis()));
        when(metricSample.getLabelNames()).thenReturn(List.of("env"));
        when(metricSample.getLabelValues()).thenReturn(List.of("prod"));

        when(sampleCollection.getSamples()).thenReturn(List.of(metricSample));

        when(metricsRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(metricsResult));
    }

    private void setupMockMetricsResultMultipleServers() {
        when(metricsResult.getServerNames()).thenReturn(Set.of("server1", "server2"));

        MetricSampleCollection collection1 = mock(MetricSampleCollection.class);
        when(collection1.getName()).thenReturn("metric1");
        when(collection1.getType()).thenReturn(Metrics.MetricType.COUNTER);
        when(collection1.getUnit()).thenReturn("count");
        when(collection1.getSamples()).thenReturn(List.of());

        MetricSampleCollection collection2 = mock(MetricSampleCollection.class);
        when(collection2.getName()).thenReturn("metric2");
        when(collection2.getType()).thenReturn(Metrics.MetricType.GAUGE);
        when(collection2.getUnit()).thenReturn("bytes");
        when(collection2.getSamples()).thenReturn(List.of());

        when(metricsResult.getMetrics("server1")).thenReturn(List.of(collection1));
        when(metricsResult.getMetrics("server2")).thenReturn(List.of(collection2));

        when(metricsRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(metricsResult));
    }
}