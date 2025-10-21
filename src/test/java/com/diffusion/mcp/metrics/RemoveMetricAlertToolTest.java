/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
 * Unit tests for RemoveMetricAlertTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class RemoveMetricAlertToolTest {

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
    private static final String TOOL_NAME = "remove_metric_alert";

    @BeforeEach
    void setUp() {
        toolSpec = RemoveMetricAlertTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Removes a metric alert");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testRemoveMetricAlert_success() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
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
                            .contains("Successfully removed metric alert: " + alertName)
                            .contains("The alert has been removed and will no longer trigger");
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_nonExistentAlert() {
        // Arrange
        setupExchange();
        String alertName = "non-existent-alert";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should succeed even if alert doesn't exist
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Successfully removed metric alert: " + alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_multipleAlerts() {
        // Arrange
        setupExchange();
        String alertName1 = "alert-1";
        String alertName2 = "alert-2";

        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("name", alertName1);

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("name", alertName2);

        CallToolRequest request1 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments1)
                .build();

        CallToolRequest request2 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments2)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName1))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(metrics.removeMetricAlert(alertName2))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request1);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request2);

        // Assert
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName1);
                })
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName2);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName1);
        verify(metrics).removeMetricAlert(alertName2);
    }

    @Test
    void testRemoveMetricAlert_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-alert");

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
    void testRemoveMetricAlert_missingName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        // name not provided

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call - use nullable to match null arguments
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("name cannot be null"));
        when(metrics.removeMetricAlert(nullable(String.class)))
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
    void testRemoveMetricAlert_emptyName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call with empty name
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("name cannot be empty"));
        when(metrics.removeMetricAlert(""))
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
    void testRemoveMetricAlert_nullName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", null);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Mock the call - use nullable to match null arguments
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalArgumentException("name cannot be null"));
        when(metrics.removeMetricAlert(nullable(String.class)))
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
    void testRemoveMetricAlert_diffusionError() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Diffusion error"));
        when(metrics.removeMetricAlert(alertName))
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
                            .contains(RemoveMetricAlertTool.TOOL_NAME)
                            .contains(alertName);
                })
                .verifyComplete();
    }

    @Test
    void testRemoveMetricAlert_timeout() {
        // Arrange
        setupExchange();
        String alertName = "test-alert";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Create a future that never completes (simulates timeout)
        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(metrics.removeMetricAlert(alertName))
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
    void testRemoveMetricAlert_withSpecialCharacters() {
        // Arrange
        setupExchange();
        String alertName = "alert-with-special-chars-!@#$%";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_longName() {
        // Arrange
        setupExchange();
        String alertName = "very-long-alert-name-".repeat(10) + "end";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Successfully removed");
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_multipleCallsSameAlert() {
        // Arrange
        setupExchange();
        String alertName = "same-alert";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act - make multiple calls to remove the same alert
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request);

        // Assert - Both should succeed
        StepVerifier.create(result1)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        verify(metrics, times(2)).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_numericName() {
        // Arrange
        setupExchange();
        String alertName = "12345";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_withWhitespace() {
        // Arrange
        setupExchange();
        String alertName = "  alert-with-spaces  ";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        // Use anyString() to match regardless of whether whitespace is trimmed
        when(metrics.removeMetricAlert(anyString()))
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

        // Verify that the method was called
        verify(metrics).removeMetricAlert(anyString());
    }

    @Test
    void testRemoveMetricAlert_caseSensitiveName() {
        // Arrange
        setupExchange();
        String alertName1 = "TestAlert";
        String alertName2 = "testalert";

        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("name", alertName1);

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("name", alertName2);

        CallToolRequest request1 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments1)
                .build();

        CallToolRequest request2 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments2)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName1))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(metrics.removeMetricAlert(alertName2))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request1);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request2);

        // Assert - Different case names should be treated as different alerts
        StepVerifier.create(result1)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName1);
        verify(metrics).removeMetricAlert(alertName2);
    }

    @Test
    void testRemoveMetricAlert_withUnderscores() {
        // Arrange
        setupExchange();
        String alertName = "my_metric_alert_123";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_withDashes() {
        // Arrange
        setupExchange();
        String alertName = "my-metric-alert-123";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_singleCharacterName() {
        // Arrange
        setupExchange();
        String alertName = "a";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains(alertName);
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_withUnicodeCharacters() {
        // Arrange
        setupExchange();
        String alertName = "alert-με-ελληνικά-🚨";

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", alertName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);
        when(metrics.removeMetricAlert(alertName))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Successfully removed");
                })
                .verifyComplete();

        verify(metrics).removeMetricAlert(alertName);
    }

    @Test
    void testRemoveMetricAlert_rapidSuccessiveCalls() {
        // Arrange
        setupExchange();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Metrics.class)).thenReturn(metrics);

        // Act & Assert - Make 5 rapid successive calls
        for (int i = 0; i < 5; i++) {
            String alertName = "alert-" + i;

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("name", alertName);

            CallToolRequest request = CallToolRequest.builder()
                    .name(TOOL_NAME)
                    .arguments(arguments)
                    .build();

            when(metrics.removeMetricAlert(alertName))
                    .thenReturn(CompletableFuture.completedFuture(null));

            Mono<CallToolResult> result = toolSpec.callHandler().apply(exchange, request);

            StepVerifier.create(result)
                    .assertNext(callResult -> assertThat(callResult.isError()).isFalse())
                    .verifyComplete();

            verify(metrics).removeMetricAlert(alertName);
        }
    }
}