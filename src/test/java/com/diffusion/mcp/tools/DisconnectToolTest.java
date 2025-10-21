/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for DisconnectTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class DisconnectToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String MCP_SESSION_ID = "mcp-session-123";
    private static final String TOOL_NAME = "disconnect";

    @BeforeEach
    void setUp() {
        toolSpec = DisconnectTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(MCP_SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Disconnects from the current Diffusion server");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testDisconnect_withActiveSession() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID)).thenReturn(session);

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
                            .contains("Successfully disconnected from Diffusion server");
                })
                .verifyComplete();

        verify(sessionManager).disconnect(MCP_SESSION_ID);
    }

    @Test
    void testDisconnect_withNoActiveSession() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID)).thenReturn(null);

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
                            .contains("No active Diffusion session to disconnect");
                })
                .verifyComplete();

        verify(sessionManager).disconnect(MCP_SESSION_ID);
    }

    @Test
    void testDisconnect_withException() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID))
                .thenThrow(new RuntimeException("Session close failed"));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error disconnecting from Diffusion server")
                            .contains("Session close failed");
                })
                .verifyComplete();
    }

    @Test
    void testDisconnect_withNullExceptionMessage() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID))
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
                            .contains("Error disconnecting from Diffusion server")
                            .contains("null");
                })
                .verifyComplete();
    }

    @Test
    void testDisconnect_multipleCalls() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        // First call returns session (successful disconnect)
        // Second call returns null (no session to disconnect)
        when(sessionManager.disconnect(MCP_SESSION_ID))
                .thenReturn(session)
                .thenReturn(null);

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request);

        // Assert - First call succeeds
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Successfully disconnected");
                })
                .verifyComplete();

        // Assert - Second call reports no session
        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("No active Diffusion session to disconnect");
                })
                .verifyComplete();

        verify(sessionManager, org.mockito.Mockito.times(2)).disconnect(MCP_SESSION_ID);
    }

    @Test
    void testDisconnect_withEmptyArguments() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should work fine with empty arguments
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Successfully disconnected");
                })
                .verifyComplete();
    }

    @Test
    void testDisconnect_withNullArguments() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(null)
                .build();

        when(sessionManager.disconnect(MCP_SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should handle null arguments gracefully
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Successfully disconnected");
                })
                .verifyComplete();
    }

    @Test
    void testDisconnect_successAfterPreviousError() {
        // Arrange
        setupExchange();
        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        // First call throws exception, second call succeeds
        when(sessionManager.disconnect(MCP_SESSION_ID))
                .thenThrow(new RuntimeException("Temporary error"))
                .thenReturn(session);

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request);

        // Assert - First call fails
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Temporary error");
                })
                .verifyComplete();

        // Assert - Second call succeeds
        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Successfully disconnected");
                })
                .verifyComplete();

        verify(sessionManager, org.mockito.Mockito.times(2)).disconnect(MCP_SESSION_ID);
    }
}

