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
 * Unit tests for ListTopicMetricCollectorsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListTopicMetricCollectorsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Metrics metrics;

    @Mock
    private TopicMetricCollector collector1;

    @Mock
    private TopicMetricCollector collector2;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;
    private ObjectMapper objectMapper;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "list_topic_metric_collectors";

    @BeforeEach
    void setUp() {
        toolSpec = ListTopicMetricCollectorsTool.create(sessionManager);
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
                .contains("Lists all topic metric collectors");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListTopicMetricCollectors_withCollectors() throws Exception {
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
        when(collector1.getTopicSelector()).thenReturn("?sensors//");
        when(collector1.exportsToPrometheus()).thenReturn(true);
        when(collector1.maximumGroups()).thenReturn(100);
        when(collector1.groupsByTopicType()).thenReturn(true);
        when(collector1.groupsByTopicView()).thenReturn(false);
        when(collector1.groupByPathPrefixParts()).thenReturn(2);

        // Setup collector2
        when(collector2.getName()).thenReturn("collector2");
        when(collector2.getTopicSelector()).thenReturn("games/*/scores");
        when(collector2.exportsToPrometheus()).thenReturn(false);
        when(collector2.maximumGroups()).thenReturn(50);
        when(collector2.groupsByTopicType()).thenReturn(false);
        when(collector2.groupsByTopicView()).thenReturn(true);
        when(collector2.groupByPathPrefixParts()).thenReturn(0);

        List<TopicMetricCollector> collectors = List.of(collector1, collector2);
        when(metrics.listTopicMetricCollectors())
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
                        assertThat(json.get("collectorType").asText()).isEqualTo("topic");
                        assertThat(json.get("count").asInt()).isEqualTo(2);

                        JsonNode collectorsArray = json.get("collectors");
                        assertThat(collectorsArray.isArray()).isTrue();
                        assertThat(collectorsArray.size()).isEqualTo(2);

                        // Verify collector1
                        JsonNode col1 = collectorsArray.get(0);
                        assertThat(col1.get("name").asText()).isEqualTo("collector1");
                        assertThat(col1.get("topicSelector").asText()).isEqualTo("?sensors//");
                        assertThat(col1.get("exportsToPrometheus").asBoolean()).isTrue();
                        assertThat(col1.get("maximumGroups").asInt()).isEqualTo(100);
                        assertThat(col1.get("groupsByTopicType").asBoolean()).isTrue();
                        assertThat(col1.get("groupsByTopicView").asBoolean()).isFalse();
                        assertThat(col1.get("groupByPathPrefixParts").asInt()).isEqualTo(2);

                        // Verify collector2
                        JsonNode col2 = collectorsArray.get(1);
                        assertThat(col2.get("name").asText()).isEqualTo("collector2");
                        assertThat(col2.get("topicSelector").asText()).isEqualTo("games/*/scores");
                        assertThat(col2.get("exportsToPrometheus").asBoolean()).isFalse();
                        assertThat(col2.get("maximumGroups").asInt()).isEqualTo(50);
                        assertThat(col2.get("groupsByTopicType").asBoolean()).isFalse();
                        assertThat(col2.get("groupsByTopicView").asBoolean()).isTrue();
                        assertThat(col2.get("groupByPathPrefixParts").asInt()).isZero();

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListTopicMetricCollectors_empty() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listTopicMetricCollectors())
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
                        assertThat(json.get("collectorType").asText()).isEqualTo("topic");
                        assertThat(json.get("count").asInt()).isZero();
                        assertThat(json.get("message").asText())
                                .isEqualTo("No topic metric collectors configured");
                        assertThat(json.has("collectors")).isFalse();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListTopicMetricCollectors_singleCollector() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        when(collector1.getName()).thenReturn("single-collector");
        when(collector1.getTopicSelector()).thenReturn("?all//");
        when(collector1.exportsToPrometheus()).thenReturn(false);
        when(collector1.maximumGroups()).thenReturn(0);
        when(collector1.groupsByTopicType()).thenReturn(false);
        when(collector1.groupsByTopicView()).thenReturn(false);
        when(collector1.groupByPathPrefixParts()).thenReturn(0);

        List<TopicMetricCollector> collectors = List.of(collector1);
        when(metrics.listTopicMetricCollectors())
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
                        assertThat(json.get("count").asInt()).isEqualTo(1);
                        JsonNode collector = json.get("collectors").get(0);
                        assertThat(collector.get("name").asText()).isEqualTo("single-collector");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListTopicMetricCollectors_allGroupingOptions() throws Exception {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        when(collector1.getName()).thenReturn("all-grouping-collector");
        when(collector1.getTopicSelector()).thenReturn("test/**");
        when(collector1.exportsToPrometheus()).thenReturn(true);
        when(collector1.maximumGroups()).thenReturn(200);
        when(collector1.groupsByTopicType()).thenReturn(true);
        when(collector1.groupsByTopicView()).thenReturn(true);
        when(collector1.groupByPathPrefixParts()).thenReturn(3);

        List<TopicMetricCollector> collectors = List.of(collector1);
        when(metrics.listTopicMetricCollectors())
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
                        JsonNode collector = json.get("collectors").get(0);
                        assertThat(collector.get("groupsByTopicType").asBoolean()).isTrue();
                        assertThat(collector.get("groupsByTopicView").asBoolean()).isTrue();
                        assertThat(collector.get("groupByPathPrefixParts").asInt()).isEqualTo(3);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListTopicMetricCollectors_noActiveSession() {
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
    void testListTopicMetricCollectors_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<List<TopicMetricCollector>> neverCompletingFuture =
                new CompletableFuture<>();
        when(metrics.listTopicMetricCollectors())
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
    void testListTopicMetricCollectors_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<List<TopicMetricCollector>> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("List failed"));
        when(metrics.listTopicMetricCollectors()).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(ListTopicMetricCollectorsTool.TOOL_NAME);
                })
                .verifyComplete();
    }
}