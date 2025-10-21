/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionId;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for ConnectTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ConnectToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SessionId sessionId;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String MCP_SESSION_ID = "mcp-session-123";
    private static final String DIFFUSION_SESSION_ID = "diffusion-session-456";
    private static final String TOOL_NAME = "connect";

    @BeforeEach
    void setUp() {
        toolSpec = ConnectTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(MCP_SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Connects to a Diffusion server");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testConnect_withMinimalArguments() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("admin"),
                eq("password"),
                eq("ws://localhost:8080"),
                isNull()))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

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
                            .contains("Successfully connected to Diffusion server")
                            .contains("ws://localhost:8080")
                            .contains(DIFFUSION_SESSION_ID);
                })
                .verifyComplete();

        verify(sessionManager).connect(
                eq(MCP_SESSION_ID),
                eq("admin"),
                eq("password"),
                eq("ws://localhost:8080"),
                isNull());
    }

    @Test
    void testConnect_withSessionProperties() {
        // Arrange
        setupExchange();
        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("USER_TIER", "premium");
        sessionProperties.put("DEPARTMENT", "finance");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "wss://secure.example.com:443");
        arguments.put("principal", "user1");
        arguments.put("password", "secret123");
        arguments.put("sessionProperties", sessionProperties);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                MCP_SESSION_ID,
                "user1",
                "secret123",
                "wss://secure.example.com:443",
                sessionProperties))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();

                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Successfully connected")
                            .contains("wss://secure.example.com:443")
                            .contains("Session properties:")
                            .contains("USER_TIER")
                            .contains("premium")
                            .contains("DEPARTMENT")
                            .contains("finance");
                })
                .verifyComplete();

        verify(sessionManager).connect(
                MCP_SESSION_ID,
                "user1",
                "secret123",
                "wss://secure.example.com:443",
                sessionProperties);
    }

    @Test
    void testConnect_withEmptySessionProperties() {
        // Arrange
        setupExchange();
        Map<String, String> emptyProperties = new HashMap<>();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");
        arguments.put("sessionProperties", emptyProperties);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                MCP_SESSION_ID,
                "admin",
                "password",
                "ws://localhost:8080",
                emptyProperties))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    // Should not contain session properties message when empty
                    assertThat(content.text())
                            .contains("Successfully connected")
                            .doesNotContain("Session properties:");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_withSingleSessionProperty() {
        // Arrange
        setupExchange();
        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("REGION", "us-east-1");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");
        arguments.put("sessionProperties", sessionProperties);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                MCP_SESSION_ID,
                "admin",
                "password",
                "ws://localhost:8080",
                sessionProperties))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Session properties:")
                            .contains("REGION=us-east-1");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_connectionFailure() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://invalid-host:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("admin"),
                eq("password"),
                eq("ws://invalid-host:8080"),
                isNull()))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error connecting to Diffusion server")
                            .contains("Connection refused");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_authenticationFailure() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "baduser");
        arguments.put("password", "wrongpassword");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("baduser"),
                eq("wrongpassword"),
                eq("ws://localhost:8080"),
                isNull()))
                .thenThrow(new RuntimeException("Authentication failed"));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error connecting to Diffusion server")
                            .contains("Authentication failed");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_withDifferentProtocols() {
        // Arrange
        setupExchange();

        // Test WebSocket Secure (wss)
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "wss://secure.example.com:443");
        arguments.put("principal", "admin");
        arguments.put("password", "password");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("admin"),
                eq("password"),
                eq("wss://secure.example.com:443"),
                isNull()))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("wss://secure.example.com:443");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_withSpecialCharactersInCredentials() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "user@domain.com");
        arguments.put("password", "P@ssw0rd!#$");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("user@domain.com"),
                eq("P@ssw0rd!#$"),
                eq("ws://localhost:8080"),
                isNull()))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(sessionManager).connect(
                eq(MCP_SESSION_ID),
                eq("user@domain.com"),
                eq("P@ssw0rd!#$"),
                eq("ws://localhost:8080"),
                isNull());
    }

    @Test
    void testConnect_withNullExceptionMessage() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("admin"),
                eq("password"),
                eq("ws://localhost:8080"),
                isNull()))
                .thenThrow(new RuntimeException());

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error connecting to Diffusion server")
                            .contains("null");
                })
                .verifyComplete();
    }

    @Test
    void testConnect_multipleCalls() {
        // Arrange
        setupExchange();

        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("url", "ws://localhost:8080");
        arguments1.put("principal", "user1");
        arguments1.put("password", "pass1");

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("url", "ws://localhost:8081");
        arguments2.put("principal", "user2");
        arguments2.put("password", "pass2");

        CallToolRequest request1 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments1)
                .build();

        CallToolRequest request2 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments2)
                .build();

        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("user1"),
                eq("pass1"),
                eq("ws://localhost:8080"),
                isNull()))
                .thenReturn(session);
        when(sessionManager.connect(
                eq(MCP_SESSION_ID),
                eq("user2"),
                eq("pass2"),
                eq("ws://localhost:8081"),
                isNull()))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request1);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request2);

        // Assert
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("ws://localhost:8080");
                })
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("ws://localhost:8081");
                })
                .verifyComplete();

        verify(sessionManager).connect(
                eq(MCP_SESSION_ID),
                eq("user1"),
                eq("pass1"),
                eq("ws://localhost:8080"),
                isNull());
        verify(sessionManager).connect(
                eq(MCP_SESSION_ID),
                eq("user2"),
                eq("pass2"),
                eq("ws://localhost:8081"),
                isNull());
    }

    @Test
    void testConnect_withMultipleSessionProperties() {
        // Arrange
        setupExchange();
        Map<String, String> sessionProperties = new HashMap<>();
        sessionProperties.put("TIER", "gold");
        sessionProperties.put("REGION", "us-west");
        sessionProperties.put("ENVIRONMENT", "production");
        sessionProperties.put("CLIENT_VERSION", "2.0.1");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("url", "ws://localhost:8080");
        arguments.put("principal", "admin");
        arguments.put("password", "password");
        arguments.put("sessionProperties", sessionProperties);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.connect(
                any(String.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any()))
                .thenReturn(session);
        when(session.getSessionId()).thenReturn(sessionId);
        when(sessionId.toString()).thenReturn(DIFFUSION_SESSION_ID);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Session properties:")
                            .contains("TIER")
                            .contains("REGION")
                            .contains("ENVIRONMENT")
                            .contains("CLIENT_VERSION");
                })
                .verifyComplete();
    }
}
