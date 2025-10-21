/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
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
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.PrimaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryAcceptor;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryInitiator;
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
 * Unit tests for ListRemoteServersTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListRemoteServersToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private RemoteServers remoteServers;

    @Mock
    private SecondaryInitiator secondaryInitiator;

    @Mock
    private PrimaryInitiator primaryInitiator;

    @Mock
    private SecondaryAcceptor secondaryAcceptor;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "list_remote_servers";

    @BeforeEach
    void setUp() {
        toolSpec = ListRemoteServersTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Lists all remote server configurations");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListRemoteServers_empty() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.listRemoteServers())
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
                    assertThat(content.text())
                            .contains("Remote Servers")
                            .contains("Total: 0")
                            .contains("No remote servers configured");
                })
                .verifyComplete();

        verify(remoteServers).listRemoteServers();
    }

    @Test
    void testListRemoteServers_singleSecondaryInitiator() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("test-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn("admin");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(secondaryInitiator)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Total: 1")
                            .contains("1. test-secondary")
                            .contains("Type: SECONDARY_INITIATOR")
                            .contains("URL: ws://primary.example.com:8080")
                            .contains("Principal: admin");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_secondaryInitiatorAnonymous() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("anon-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(secondaryInitiator)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Principal: <anonymous>");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_secondaryInitiatorWithFilter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("filtered-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn("user");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn("sensors/>.*");
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(secondaryInitiator)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Missing Topic Filter: sensors/>.*");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_singlePrimaryInitiator() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(primaryInitiator.getName()).thenReturn("test-primary");
        when(primaryInitiator.getType()).thenReturn(RemoteServer.Type.PRIMARY_INITIATOR);
        when(primaryInitiator.getUrls()).thenReturn(List.of(
                "ws://secondary1.example.com:8080",
                "ws://secondary2.example.com:8080"
        ));
        when(primaryInitiator.getConnector()).thenReturn("test-connector");
        when(primaryInitiator.getRetryDelay()).thenReturn(5000);
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(primaryInitiator)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Total: 1")
                            .contains("1. test-primary")
                            .contains("Type: PRIMARY_INITIATOR")
                            .contains("URLs: ws://secondary1.example.com:8080, ws://secondary2.example.com:8080")
                            .contains("Connector: test-connector")
                            .contains("Retry Delay: 5000 ms");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_singleSecondaryAcceptor() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryAcceptor.getName()).thenReturn("test-acceptor");
        when(secondaryAcceptor.getType()).thenReturn(RemoteServer.Type.SECONDARY_ACCEPTOR);
        when(secondaryAcceptor.getPrimaryHostName()).thenReturn("primary.example.com");
        when(secondaryAcceptor.getPrincipal()).thenReturn("acceptor-user");
        when(secondaryAcceptor.getMissingTopicNotificationFilter()).thenReturn("*.*");
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(secondaryAcceptor)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Total: 1")
                            .contains("1. test-acceptor")
                            .contains("Type: SECONDARY_ACCEPTOR")
                            .contains("Primary Host Name: primary.example.com")
                            .contains("Principal: acceptor-user")
                            .contains("Missing Topic Filter: *.*");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_multipleServers() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        // Setup secondary initiator
        when(secondaryInitiator.getName()).thenReturn("secondary-1");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn("user1");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);

        // Setup primary initiator
        when(primaryInitiator.getName()).thenReturn("primary-1");
        when(primaryInitiator.getType()).thenReturn(RemoteServer.Type.PRIMARY_INITIATOR);
        when(primaryInitiator.getUrls()).thenReturn(List.of("ws://secondary.example.com:8080"));
        when(primaryInitiator.getConnector()).thenReturn("connector-1");
        when(primaryInitiator.getRetryDelay()).thenReturn(1000);

        // Setup secondary acceptor
        when(secondaryAcceptor.getName()).thenReturn("acceptor-1");
        when(secondaryAcceptor.getType()).thenReturn(RemoteServer.Type.SECONDARY_ACCEPTOR);
        when(secondaryAcceptor.getPrimaryHostName()).thenReturn("primary.example.com");
        when(secondaryAcceptor.getPrincipal()).thenReturn("");
        when(secondaryAcceptor.getMissingTopicNotificationFilter()).thenReturn(null);

        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(secondaryInitiator, primaryInitiator, secondaryAcceptor)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Total: 3")
                            .contains("1. secondary-1")
                            .contains("2. primary-1")
                            .contains("3. acceptor-1")
                            .contains("Type: SECONDARY_INITIATOR")
                            .contains("Type: PRIMARY_INITIATOR")
                            .contains("Type: SECONDARY_ACCEPTOR");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_noActiveSession() {
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
    void testListRemoteServers_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.listRemoteServers())
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
                            .contains(ListRemoteServersTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        CompletableFuture<List<RemoteServer>> neverCompletingFuture = new CompletableFuture<>();
        when(remoteServers.listRemoteServers())
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
                            .contains(ListRemoteServersTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testListRemoteServers_emptyPrincipal() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("empty-principal");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn("");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(remoteServers.listRemoteServers())
                .thenReturn(CompletableFuture.completedFuture(List.of(secondaryInitiator)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Principal: <anonymous>");
                })
                .verifyComplete();
    }
}