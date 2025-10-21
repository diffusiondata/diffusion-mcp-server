/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import com.pushtechnology.diffusion.client.features.control.Metrics.MetricAlert;
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
 * Unit tests for ListMetricAlertsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListMetricAlertsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Metrics metrics;

    @Mock
    private McpAsyncServerExchange exchange;

    @Mock
    private MetricAlert metricAlert1;

    @Mock
    private MetricAlert metricAlert2;

    @Mock
    private MetricAlert metricAlert3;

    private AsyncToolSpecification toolSpec;
    private ObjectMapper objectMapper;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "list_metric_alerts";

    @BeforeEach
    void setUp() {
        toolSpec = ListMetricAlertsTool.create(sessionManager);
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
                .contains("Lists all metric alerts");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListMetricAlerts_empty() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);

                    TextContent content = (TextContent) callResult.content().get(0);
                    try {
                        JsonNode json = objectMapper.readTree(content.text());
                        assertThat(json.get("count").asInt()).isZero();
                        assertThat(json.get("message").asText())
                                .isEqualTo("No metric alerts configured");
                        assertThat(json.has("alerts")).isFalse();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();

        verify(metrics).listMetricAlerts();
    }

    @Test
    void testListMetricAlerts_singleAlert() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(metricAlert1.getName()).thenReturn("load-alert");
        when(metricAlert1.getSpecification()).thenReturn(
                "select os_system_load_average into topic metrics/load where value > 5");
        when(metricAlert1.getPrincipal()).thenReturn("admin");

        List<MetricAlert> alerts = List.of(metricAlert1);

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(alerts));

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
                        assertThat(json.has("message")).isFalse();
                        assertThat(json.has("alerts")).isTrue();

                        JsonNode alertsArray = json.get("alerts");
                        assertThat(alertsArray.isArray()).isTrue();
                        assertThat(alertsArray.size()).isEqualTo(1);

                        JsonNode alert = alertsArray.get(0);
                        assertThat(alert.get("name").asText()).isEqualTo("load-alert");
                        assertThat(alert.get("specification").asText())
                                .contains("os_system_load_average");
                        assertThat(alert.get("principal").asText()).isEqualTo("admin");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();

        verify(metrics).listMetricAlerts();
    }

    @Test
    void testListMetricAlerts_multipleAlerts() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(metricAlert1.getName()).thenReturn("load-alert");
        when(metricAlert1.getSpecification()).thenReturn(
                "select os_system_load_average into topic metrics/load where value > 5");
        when(metricAlert1.getPrincipal()).thenReturn("admin");

        when(metricAlert2.getName()).thenReturn("memory-alert");
        when(metricAlert2.getSpecification()).thenReturn(
                "select jvm_memory_used into topic metrics/memory where value > 1000000000");
        when(metricAlert2.getPrincipal()).thenReturn("operator");

        when(metricAlert3.getName()).thenReturn("cpu-alert");
        when(metricAlert3.getSpecification()).thenReturn(
                "select process_cpu_usage into topic metrics/cpu where value > 0.8");
        when(metricAlert3.getPrincipal()).thenReturn("admin");

        List<MetricAlert> alerts = List.of(metricAlert1, metricAlert2, metricAlert3);

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(alerts));

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
                        assertThat(json.get("count").asInt()).isEqualTo(3);
                        assertThat(json.has("alerts")).isTrue();

                        JsonNode alertsArray = json.get("alerts");
                        assertThat(alertsArray.size()).isEqualTo(3);

                        // Verify first alert
                        JsonNode alert1 = alertsArray.get(0);
                        assertThat(alert1.get("name").asText()).isEqualTo("load-alert");
                        assertThat(alert1.get("principal").asText()).isEqualTo("admin");

                        // Verify second alert
                        JsonNode alert2 = alertsArray.get(1);
                        assertThat(alert2.get("name").asText()).isEqualTo("memory-alert");
                        assertThat(alert2.get("principal").asText()).isEqualTo("operator");

                        // Verify third alert
                        JsonNode alert3 = alertsArray.get(2);
                        assertThat(alert3.get("name").asText()).isEqualTo("cpu-alert");
                        assertThat(alert3.get("principal").asText()).isEqualTo("admin");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();

        verify(metrics).listMetricAlerts();
    }

    @Test
    void testListMetricAlerts_withComplexSpecifications() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(metricAlert1.getName()).thenReturn("complex-alert");
        when(metricAlert1.getSpecification()).thenReturn(
                "select jvm_memory_used from_server prod-1 " +
                        "into topic alerts/<server>/memory " +
                        "with_properties { TIDY_ON_UNSUBSCRIBE: true } " +
                        "where value > 1000000000 " +
                        "disable_until value < 500000000");
        when(metricAlert1.getPrincipal()).thenReturn("system");

        List<MetricAlert> alerts = List.of(metricAlert1);

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(alerts));

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

                        JsonNode alert = json.get("alerts").get(0);
                        assertThat(alert.get("name").asText()).isEqualTo("complex-alert");
                        assertThat(alert.get("specification").asText())
                                .contains("from_server")
                                .contains("with_properties")
                                .contains("disable_until");
                        assertThat(alert.get("principal").asText()).isEqualTo("system");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();

        verify(metrics).listMetricAlerts();
    }

    @Test
    void testListMetricAlerts_noActiveSession() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
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
    void testListMetricAlerts_diffusionError() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<List<MetricAlert>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Diffusion error"));
        when(metrics.listMetricAlerts()).thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(ListMetricAlertsTool.TOOL_NAME);
                })
                .verifyComplete();
    }

    @Test
    void testListMetricAlerts_timeout() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Create a future that never completes (simulates timeout)
        CompletableFuture<List<MetricAlert>> neverCompletingFuture = new CompletableFuture<>();
        when(metrics.listMetricAlerts()).thenAnswer(invocation -> neverCompletingFuture);

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
    void testListMetricAlerts_withDifferentPrincipals() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(metricAlert1.getName()).thenReturn("admin-alert");
        when(metricAlert1.getSpecification()).thenReturn("select metric1 into topic test1");
        when(metricAlert1.getPrincipal()).thenReturn("admin");

        when(metricAlert2.getName()).thenReturn("user-alert");
        when(metricAlert2.getSpecification()).thenReturn("select metric2 into topic test2");
        when(metricAlert2.getPrincipal()).thenReturn("user123");

        when(metricAlert3.getName()).thenReturn("system-alert");
        when(metricAlert3.getSpecification()).thenReturn("select metric3 into topic test3");
        when(metricAlert3.getPrincipal()).thenReturn("SYSTEM");

        List<MetricAlert> alerts = List.of(metricAlert1, metricAlert2, metricAlert3);

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(alerts));

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
                        JsonNode alertsArray = json.get("alerts");

                        assertThat(alertsArray.get(0).get("principal").asText()).isEqualTo("admin");
                        assertThat(alertsArray.get(1).get("principal").asText()).isEqualTo("user123");
                        assertThat(alertsArray.get(2).get("principal").asText()).isEqualTo("SYSTEM");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListMetricAlerts_largeNumberOfAlerts() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        List<MetricAlert> alerts = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            MetricAlert alert = org.mockito.Mockito.mock(MetricAlert.class);
            when(alert.getName()).thenReturn("alert-" + i);
            when(alert.getSpecification()).thenReturn("select metric" + i + " into topic test" + i);
            when(alert.getPrincipal()).thenReturn("principal-" + (i % 5));
            alerts.add(alert);
        }

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(alerts));

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
                        assertThat(json.get("count").asInt()).isEqualTo(50);
                        assertThat(json.get("alerts").size()).isEqualTo(50);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void testListMetricAlerts_emptyArgumentsMap() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(metrics).listMetricAlerts();
    }

    @Test
    void testListMetricAlerts_nullArgumentsHandled() throws Exception {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(null)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.listMetricAlerts())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();
    }
}