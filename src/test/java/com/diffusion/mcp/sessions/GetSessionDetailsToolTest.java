/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.pushtechnology.diffusion.client.features.control.clients.ClientControl;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;
import com.pushtechnology.diffusion.client.session.SessionId;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for GetSessionDetailsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetSessionDetailsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private ClientControl clientControl;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_session_details";
    private static final String TARGET_SESSION_ID_STRING = "0000000000000001-0000000000000002"; // Valid format: 16-digit hex server-value
    private static final String INVALID_SESSION_ID = "invalid"; // No hyphen, will fail parsing

    @BeforeEach
    void setUp() {
        toolSpec = GetSessionDetailsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(ClientControl.class)).thenReturn(clientControl);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieves detailed properties for a specific Diffusion session");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetSessionDetails_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");
        sessionProperties.put("$ClientIP", "192.168.1.1");
        sessionProperties.put("$Transport", "WEBSOCKET");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(TARGET_SESSION_ID_STRING)
                            .contains("properties")
                            .contains("propertyCount");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_withSpecificProperties() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of("$Principal", "$ClientIP"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");
        sessionProperties.put("$ClientIP", "192.168.1.1");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(TARGET_SESSION_ID_STRING)
                            .contains("\"propertyCount\":2");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_singleProperty() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of("$Principal"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"propertyCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_emptyPropertiesResult() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"propertyCount\":0");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_multipleProperties() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of("$Principal", "$ClientIP", "$Transport", "$Connector"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");
        sessionProperties.put("$ClientIP", "192.168.1.1");
        sessionProperties.put("$Transport", "WEBSOCKET");
        sessionProperties.put("$Connector", "CLIENT_CONNECTOR");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"propertyCount\":4");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_userDefinedProperties() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of("USER_TIER", "DEPARTMENT"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("USER_TIER", "premium");
        sessionProperties.put("DEPARTMENT", "sales");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"propertyCount\":2");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_invalidSessionIdFormat() {
        // Arrange
        setupExchange();
        // Don't call setupMocks() - we won't reach the point where we need ClientControl
        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        Map<String, Object> arguments = new HashMap<>();
        // Use a string without hyphen separator which will fail parsing
        arguments.put("sessionId", INVALID_SESSION_ID);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid session ID format");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);

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
    void testGetSessionDetails_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<Map<String, String>> neverCompletingFuture = new CompletableFuture<>();
        when(clientControl.getSessionProperties(any(SessionId.class), any()))
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
    void testGetSessionDetails_sessionNotFound() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<Map<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new SessionException("Session not found: " + TARGET_SESSION_ID_STRING));
        when(clientControl.getSessionProperties(any(SessionId.class), any()))
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
                            .contains(GetSessionDetailsTool.TOOL_NAME)
                            .contains(TARGET_SESSION_ID_STRING)
                            .contains("Session not found");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_emptyPropertiesList() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");

        // Should use default properties when empty list provided
        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(TARGET_SESSION_ID_STRING);
                })
                .verifyComplete();
    }

    @Test
    void testGetSessionDetails_mixedFixedAndUserProperties() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sessionId", TARGET_SESSION_ID_STRING);
        arguments.put("properties", List.of("$Principal", "$ClientIP", "USER_TIER", "REGION"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("$Principal", "admin");
        sessionProperties.put("$ClientIP", "192.168.1.1");
        sessionProperties.put("USER_TIER", "premium");
        sessionProperties.put("REGION", "us-east");

        when(clientControl.getSessionProperties(any(SessionId.class), any()))
                .thenReturn(CompletableFuture.completedFuture(sessionProperties));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"propertyCount\":4");
                })
                .verifyComplete();
    }
}