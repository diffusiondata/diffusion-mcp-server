/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Unit tests for ListSessionMetricCollectorsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListSessionMetricCollectorsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Metrics metrics;

    @Mock
    private SessionMetricCollector collector1;

    @Mock
    private SessionMetricCollector collector2;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;
    private ObjectMapper objectMapper;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "list_session_metric_collectors";

    @BeforeEach
    void setUp() {
        toolSpec = ListSessionMetricCollectorsTool.create(sessionManager);
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
                .contains("Lists all session metric collectors");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListSessionMetricCollectors_withCollectors() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Setup collector1
        when(collector1.getName()).thenReturn("collector1");
        when(collector1.getSessionFilter()).thenReturn("all");
        when(collector1.exportsToPrometheus()).thenReturn(true);
        when(collector1.maximumGroups()).thenReturn(100);
        when(collector1.removesMetricsWithNoMatches()).thenReturn(false);
        when(collector1.getGroupByProperties()).thenReturn(List.of("$Principal"));

        // Setup collector2
        when(collector2.getName()).thenReturn("collector2");
        when(collector2.getSessionFilter()).thenReturn("$Principal is \"user1\"");
        when(collector2.exportsToPrometheus()).thenReturn(false);
        when(collector2.maximumGroups()).thenReturn(50);
        when(collector2.removesMetricsWithNoMatches()).thenReturn(true);
        when(collector2.getGroupByProperties()).thenReturn(List.of());

        List<SessionMetricCollector> collectors = List.of(collector1, collector2);
        when(metrics.listSessionMetricCollectors())
                .thenReturn(CompletableFuture.completedFuture(collectors));

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
                        assertThat(json.get("collectorType").asText()).isEqualTo("session");
                        assertThat(json.get("count").asInt()).isEqualTo(2);

                        JsonNode collectorsArray = json.get("collectors");
                        assertThat(collectorsArray.isArray()).isTrue();
                        assertThat(collectorsArray.size()).isEqualTo(2);

                        // Verify collector1
                        JsonNode col1 = collectorsArray.get(0);
                        assertThat(col1.get("name").asText()).isEqualTo("collector1");
                        assertThat(col1.get("sessionFilter").asText()).isEqualTo("all");
                        assertThat(col1.get("exportsToPrometheus").asBoolean()).isTrue();
                        assertThat(col1.get("maximumGroups").asInt()).isEqualTo(100);
                        assertThat(col1.get("removesMetricsWithNoMatches").asBoolean()).isFalse();
                        assertThat(col1.get("groupByProperties").get(0).asText()).isEqualTo("$Principal");

                        // Verify collector2
                        JsonNode col2 = collectorsArray.get(1);
                        assertThat(col2.get("name").asText()).isEqualTo("collector2");
                        assertThat(col2.get("sessionFilter").asText()).isEqualTo("$Principal is \"user1\"");
                        assertThat(col2.get("exportsToPrometheus").asBoolean()).isFalse();
                        assertThat(col2.get("maximumGroups").asInt()).isEqualTo(50);
                        assertThat(col2.get("removesMetricsWithNoMatches").asBoolean()).isTrue();
                        assertThat(col2.has("groupByProperties")).isFalse(); // Empty list not included

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListSessionMetricCollectors_empty() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listSessionMetricCollectors())
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

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
                        assertThat(json.get("collectorType").asText()).isEqualTo("session");
                        assertThat(json.get("count").asInt()).isZero();
                        assertThat(json.get("message").asText())
                                .isEqualTo("No session metric collectors configured");
                        assertThat(json.has("collectors")).isFalse();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListSessionMetricCollectors_withMultipleGroupByProperties() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        when(collector1.getName()).thenReturn("multi-group-collector");
        when(collector1.getSessionFilter()).thenReturn("all");
        when(collector1.exportsToPrometheus()).thenReturn(false);
        when(collector1.maximumGroups()).thenReturn(0);
        when(collector1.removesMetricsWithNoMatches()).thenReturn(false);
        when(collector1.getGroupByProperties())
                .thenReturn(List.of("$Principal", "$ClientIP", "$SessionId"));

        List<SessionMetricCollector> collectors = List.of(collector1);
        when(metrics.listSessionMetricCollectors())
                .thenReturn(CompletableFuture.completedFuture(collectors));

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
                        JsonNode properties = json.get("collectors").get(0).get("groupByProperties");
                        assertThat(properties.isArray()).isTrue();
                        assertThat(properties.size()).isEqualTo(3);
                        assertThat(properties.get(0).asText()).isEqualTo("$Principal");
                        assertThat(properties.get(1).asText()).isEqualTo("$ClientIP");
                        assertThat(properties.get(2).asText()).isEqualTo("$SessionId");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListSessionMetricCollectors_noActiveSession() {
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
    void testListSessionMetricCollectors_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<List<SessionMetricCollector>> neverCompletingFuture =
                new CompletableFuture<>();
        when(metrics.listSessionMetricCollectors())
                .thenReturn(neverCompletingFuture);

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
    void testListSessionMetricCollectors_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<List<SessionMetricCollector>> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("List failed"));
        when(metrics.listSessionMetricCollectors()).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(ListSessionMetricCollectorsTool.TOOL_NAME);
                })
                .verifyComplete();
    }
}