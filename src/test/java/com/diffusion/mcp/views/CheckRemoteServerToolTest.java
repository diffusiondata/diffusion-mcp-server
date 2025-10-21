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
import com.pushtechnology.diffusion.client.features.control.RemoteServers.CheckRemoteServerResult;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.CheckRemoteServerResult.ConnectionState;
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
 * Unit tests for CheckRemoteServerTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class CheckRemoteServerToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private RemoteServers remoteServers;

    @Mock
    private CheckRemoteServerResult checkResult;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "check_remote_server";

    @BeforeEach
    void setUp() {
        toolSpec = CheckRemoteServerTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Checks the current state of a named remote server");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testCheckRemoteServer_CONNECTED() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-remote-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.CONNECTED);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("test-remote-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

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
                            .contains("Remote Server Status")
                            .contains("Name: test-remote-server")
                            .contains("Connection State: CONNECTED")
                            .contains("successfully connected and operational");
                })
                .verifyComplete();

        verify(remoteServers).checkRemoteServer("test-remote-server");
    }

    @Test
    void testCheckRemoteServer_INACTIVE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "inactive-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.INACTIVE);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("inactive-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: INACTIVE")
                            .contains("currently inactive")
                            .contains("SECONDARY_INITIATOR");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_RETRYING_withFailureMessage() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "retrying-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.RETRYING);
        when(checkResult.getFailureMessage()).thenReturn("Connection refused");
        when(remoteServers.checkRemoteServer("retrying-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: RETRYING")
                            .contains("currently retrying")
                            .contains("Failure reason:")
                            .contains("Connection refused");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_RETRYING_withoutFailureMessage() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "retrying-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.RETRYING);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("retrying-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: RETRYING")
                            .contains("currently retrying")
                            .doesNotContain("Failure reason:");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_FAILED_withFailureMessage() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "failed-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.FAILED);
        when(checkResult.getFailureMessage()).thenReturn("Authentication failed");
        when(remoteServers.checkRemoteServer("failed-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: FAILED")
                            .contains("connection has failed")
                            .contains("Failure reason:")
                            .contains("Authentication failed")
                            .contains("retry the connection by running check_remote_server again");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_FAILED_withEmptyFailureMessage() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "failed-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.FAILED);
        when(checkResult.getFailureMessage()).thenReturn("");
        when(remoteServers.checkRemoteServer("failed-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: FAILED")
                            .doesNotContain("Failure reason:");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_MISSING() {
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
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.MISSING);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("nonexistent-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection State: MISSING")
                            .contains("does not exist")
                            .contains("Use list_remote_servers")
                            .doesNotContain("This status reflects the state on the server you are connected to");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_noActiveSession() {
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
    void testCheckRemoteServer_diffusionError() {
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
        when(remoteServers.checkRemoteServer("test-server"))
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
                            .contains(CheckRemoteServerTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "slow-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        CompletableFuture<CheckRemoteServerResult> neverCompletingFuture =
                new CompletableFuture<>();
        when(remoteServers.checkRemoteServer("slow-server"))
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
                            .contains(CheckRemoteServerTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_clusterNote() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "cluster-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.CONNECTED);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("cluster-server"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("This status reflects the state on the server you are connected to")
                            .contains("In a cluster, other servers may have different states");
                })
                .verifyComplete();
    }

    @Test
    void testCheckRemoteServer_withHyphenatedName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "remote-server-prod-01");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(checkResult.getConnectionState()).thenReturn(ConnectionState.CONNECTED);
        when(checkResult.getFailureMessage()).thenReturn(null);
        when(remoteServers.checkRemoteServer("remote-server-prod-01"))
                .thenReturn(CompletableFuture.completedFuture(checkResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: remote-server-prod-01")
                            .contains("Connection State: CONNECTED");
                })
                .verifyComplete();

        verify(remoteServers).checkRemoteServer("remote-server-prod-01");
    }
}