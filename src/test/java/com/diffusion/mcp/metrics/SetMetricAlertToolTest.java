/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
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
 * Unit tests for SetMetricAlertTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class SetMetricAlertToolTest {

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
    private static final String TOOL_NAME = "set_metric_alert";

    @BeforeEach
    void setUp() {
        toolSpec = SetMetricAlertTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates or updates a metric alert");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testSetMetricAlert_simpleSpecification() {
        // Arrange
        setupExchange();
        String alertName = "load-alert";
        String specification = "select os_system_load_average " +
                "into topic metrics/<server>/load " +
                "where value > 5";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains("Successfully set metric alert")
                            .contains("Name: " + alertName)
                            .contains("Specification: " + specification)
                            .contains("The alert is now active");
                })
                .verifyComplete();

        verify(metrics).setMetricAlert(alertName, specification);
    }

    @Test
    void testSetMetricAlert_complexSpecification() {
        // Arrange
        setupExchange();
        String alertName = "memory-alert";
        String specification = "select jvm_memory_used " +
                "from_server production-1 " +
                "into topic alerts/memory/<server> " +
                "with_properties { TIDY_ON_UNSUBSCRIBE: true } " +
                "where value > 1000000000 " +
                "disable_until value < 500000000";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains("Name: " + alertName)
                            .contains("Specification: " + specification);
                })
                .verifyComplete();

        verify(metrics).setMetricAlert(alertName, specification);
    }

    @Test
    void testSetMetricAlert_withDimensionalData() {
        // Arrange
        setupExchange();
        String alertName = "dimensional-alert";
        String specification = "select custom_metric " +
                "into topic metrics/<server>/custom " +
                "where dimensions = {name: 'foo'} and value > 100";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains("Successfully set metric alert");
                })
                .verifyComplete();

        verify(metrics).setMetricAlert(alertName, specification);
    }

    @Test
    void testSetMetricAlert_updateExisting() {
        // Arrange
        setupExchange();
        String alertName = "existing-alert";
        String specification = "select os_system_load_average " +
                "into topic metrics/load " +
                "where value > 10";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains("Successfully set metric alert");
                })
                .verifyComplete();

        verify(metrics).setMetricAlert(alertName, specification);
    }

    @Test
    void testSetMetricAlert_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-alert");
        arguments.put("specification", "select metric into topic test");

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
    void testSetMetricAlert_missingName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("specification", "select metric into topic test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call - use nullable to match null arguments
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("name cannot be null"));
        when(metrics.setMetricAlert(nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should return an error
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_missingSpecification() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-alert");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call - use nullable to match null arguments
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("specification cannot be null"));
        when(metrics.setMetricAlert(nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should return an error
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_emptyName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "");
        arguments.put("specification", "select metric into topic test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call with empty name
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("name cannot be empty"));
        when(metrics.setMetricAlert(eq(""), anyString()))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should return an error
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_emptySpecification() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-alert");
        arguments.put("specification", "");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call with empty specification
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("specification cannot be empty"));
        when(metrics.setMetricAlert("test-alert", ""))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should return an error
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_invalidSpecificationSyntax() {
        // Arrange
        setupExchange();
        String alertName = "bad-alert";
        String specification = "invalid syntax here";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Invalid specification syntax"));
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains(SetMetricAlertTool.TOOL_NAME)
                            .contains(alertName);
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_diffusionError() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";
        String specification = "select metric into topic test";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Diffusion error"));
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains(SetMetricAlertTool.TOOL_NAME)
                            .contains(alertName);
                })
                .verifyComplete();
    }

    @Test
    void testSetMetricAlert_timeout() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";
        String specification = "select metric into topic test";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Create a future that never completes (simulates timeout)
        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(metrics.setMetricAlert(alertName, specification))
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
    void testSetMetricAlert_multipleCallsSucceed() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";
        String specification = "select metric into topic test";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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

        verify(metrics, times(2)).setMetricAlert(alertName, specification);
    }

    @Test
    void testSetMetricAlert_longSpecification() {
        // Arrange
        setupExchange();
        String alertName = "complex-alert";
        String specification = "select jvm_memory_used " +
                "from_server production-server-1 " +
                "into topic metrics/memory/<server>/<timestamp> " +
                "with_properties { " +
                "TIDY_ON_UNSUBSCRIBE: true, " +
                "REMOVAL: 'when no session has \"$Principal is 'admin'\" for 5m' " +
                "} " +
                "where value > 1000000000 and dimensions = {type: 'heap'} " +
                "disable_until value < 500000000 and dimensions = {type: 'heap'}";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);
        arguments.put("specification", specification);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.setMetricAlert(alertName, specification))
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
                            .contains("Successfully set metric alert")
                            .contains("Name: " + alertName);
                })
                .verifyComplete();

        verify(metrics).setMetricAlert(alertName, specification);
    }
}