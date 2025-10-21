/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.session.PermissionsException;
import com.pushtechnology.diffusion.client.session.Session;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for RemoveRemoteServerTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class RemoveRemoteServerToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private RemoteServers remoteServers;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "remove_remote_server";

    @BeforeEach
    void setUp() {
        toolSpec = RemoveRemoteServerTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Removes a remote server configuration");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testRemoveRemoteServer_success() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("test-server"))
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
                            .contains("Remote Server Removed")
                            .contains("Name: test-server")
                            .contains("successfully removed")
                            .contains("topic views depending on this remote server will be disabled");
                })
                .verifyComplete();

        verify(remoteServers).removeRemoteServer("test-server");
    }

    @Test
    void testRemoveRemoteServer_nonExistentServer() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "nonexistent-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("nonexistent-server"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Should succeed even if server doesn't exist
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: nonexistent-server")
                            .contains("successfully removed");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRemoteServer_withHyphenatedName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "production-remote-server-01");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("production-remote-server-01"))
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
                            .contains("Name: production-remote-server-01");
                })
                .verifyComplete();

        verify(remoteServers).removeRemoteServer("production-remote-server-01");
    }

    @Test
    void testRemoveRemoteServer_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-server");

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
    void testRemoveRemoteServer_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "error-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("error-server"))
                .thenReturn(CompletableFuture.failedFuture(
                        new PermissionsException("Permission denied")));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(RemoveRemoteServerTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRemoteServer_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "timeout-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        CompletableFuture<Void> neverCompletingFuture = new CompletableFuture<>();
        when(remoteServers.removeRemoteServer("timeout-server"))
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(RemoveRemoteServerTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRemoteServer_multipleServersSequentially() {
        // Arrange
        setupExchange();
        String[] serverNames = {"server-1", "server-2", "server-3"};

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        for (String serverName : serverNames) {
            when(remoteServers.removeRemoteServer(serverName))
                    .thenReturn(CompletableFuture.completedFuture(null));
        }

        // Act & Assert - Test each server removal
        for (String serverName : serverNames) {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("name", serverName);

            CallToolRequest request = CallToolRequest.builder()
                    .name(TOOL_NAME)
                    .arguments(arguments)
                    .build();

            Mono<CallToolResult> result = toolSpec.callHandler()
                    .apply(exchange, request);

            StepVerifier.create(result)
                    .assertNext(callResult -> {
                        assertThat(callResult.isError()).isFalse();
                        TextContent content = (TextContent) callResult.content().get(0);
                        assertThat(content.text())
                                .contains("Name: " + serverName)
                                .contains("successfully removed");
                    })
                    .verifyComplete();

            verify(remoteServers).removeRemoteServer(serverName);
        }
    }

    @Test
    void testRemoveRemoteServer_withUnderscores() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test_remote_server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("test_remote_server"))
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
                            .contains("Name: test_remote_server");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRemoteServer_numericName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "server-123");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.removeRemoteServer("server-123"))
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
                            .contains("Name: server-123");
                })
                .verifyComplete();
    }
}
