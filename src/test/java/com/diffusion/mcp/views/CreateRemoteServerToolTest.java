/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import com.pushtechnology.diffusion.client.features.control.RemoteServers;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.PrimaryInitiator;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.RemoteServer.ConnectionOption;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryAcceptor;
import com.pushtechnology.diffusion.client.features.control.RemoteServers.SecondaryInitiator;
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
 * Unit tests for CreateRemoteServerTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class CreateRemoteServerToolTest {

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
    private static final String TOOL_NAME = "create_remote_server";

    @BeforeEach
    void setUp() {
        toolSpec = CreateRemoteServerTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates a remote server configuration");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_minimal() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "test-secondary");
        arguments.put("url", "ws://primary.example.com:8080");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("test-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(secondaryInitiator.getConnectionOptions()).thenReturn(null);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

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
                            .contains("Remote Server Created")
                            .contains("Name: test-secondary")
                            .contains("Type: SECONDARY_INITIATOR")
                            .contains("URL: ws://primary.example.com:8080")
                            .contains("Principal: <anonymous>")
                            .contains("successfully created");
                })
                .verifyComplete();

        verify(remoteServers).createRemoteServer(any(RemoteServer.class));
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_withAuthentication() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "auth-secondary");
        arguments.put("url", "wss://primary.example.com:8443");
        arguments.put("principal", "admin");
        arguments.put("password", "password123");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("auth-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("wss://primary.example.com:8443");
        when(secondaryInitiator.getPrincipal()).thenReturn("admin");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(secondaryInitiator.getConnectionOptions()).thenReturn(null);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: auth-secondary")
                            .contains("Principal: admin");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_withConnectionOptions() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "options-secondary");
        arguments.put("url", "ws://primary.example.com:8080");

        Map<String, String> connectionOptions = new HashMap<>();
        connectionOptions.put("RECONNECTION_TIMEOUT", "30000");
        connectionOptions.put("INPUT_BUFFER_SIZE", "65536");
        arguments.put("connectionOptions", connectionOptions);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("options-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);

        Map<ConnectionOption, String> options = new HashMap<>();
        options.put(ConnectionOption.RECONNECTION_TIMEOUT, "30000");
        options.put(ConnectionOption.INPUT_BUFFER_SIZE, "65536");
        when(secondaryInitiator.getConnectionOptions()).thenReturn(options);

        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler().apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Connection Options:")
                            .contains("RECONNECTION_TIMEOUT: 30000")
                            .contains("INPUT_BUFFER_SIZE: 65536");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_withMissingTopicFilter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "filter-secondary");
        arguments.put("url", "ws://primary.example.com:8080");
        arguments.put("missingTopicNotificationFilter", "*.*");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("filter-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn("*.*");
        when(secondaryInitiator.getConnectionOptions()).thenReturn(null);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Missing Topic Notification Filter: *.*");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_missingUrl() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "no-url-secondary");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("URL is required for SECONDARY_INITIATOR");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_minimal() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "test-primary");
        arguments.put("urls", List.of("ws://secondary1.example.com:8080"));
        arguments.put("connector", "test-connector");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(primaryInitiator.getName()).thenReturn("test-primary");
        when(primaryInitiator.getType()).thenReturn(RemoteServer.Type.PRIMARY_INITIATOR);
        when(primaryInitiator.getUrls()).thenReturn(List.of("ws://secondary1.example.com:8080"));
        when(primaryInitiator.getConnector()).thenReturn("test-connector");
        when(primaryInitiator.getRetryDelay()).thenReturn(1000);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(primaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: test-primary")
                            .contains("Type: PRIMARY_INITIATOR")
                            .contains("URLs: ws://secondary1.example.com:8080")
                            .contains("Connector: test-connector")
                            .contains("Retry Delay: 1000 ms");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_multipleUrls() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "multi-primary");
        arguments.put("urls", List.of(
                "ws://secondary1.example.com:8080",
                "ws://secondary2.example.com:8080",
                "ws://secondary3.example.com:8080"
        ));
        arguments.put("connector", "multi-connector");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(primaryInitiator.getName()).thenReturn("multi-primary");
        when(primaryInitiator.getType()).thenReturn(RemoteServer.Type.PRIMARY_INITIATOR);
        when(primaryInitiator.getUrls()).thenReturn(List.of(
                "ws://secondary1.example.com:8080",
                "ws://secondary2.example.com:8080",
                "ws://secondary3.example.com:8080"
        ));
        when(primaryInitiator.getConnector()).thenReturn("multi-connector");
        when(primaryInitiator.getRetryDelay()).thenReturn(1000);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(primaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("ws://secondary1.example.com:8080")
                            .contains("ws://secondary2.example.com:8080")
                            .contains("ws://secondary3.example.com:8080");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_withRetryDelay() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "retry-primary");
        arguments.put("urls", List.of("ws://secondary.example.com:8080"));
        arguments.put("connector", "retry-connector");
        arguments.put("retryDelay", 5000);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(primaryInitiator.getName()).thenReturn("retry-primary");
        when(primaryInitiator.getType()).thenReturn(RemoteServer.Type.PRIMARY_INITIATOR);
        when(primaryInitiator.getUrls()).thenReturn(List.of("ws://secondary.example.com:8080"));
        when(primaryInitiator.getConnector()).thenReturn("retry-connector");
        when(primaryInitiator.getRetryDelay()).thenReturn(5000);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(primaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Retry Delay: 5000 ms");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_missingUrls() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "no-urls-primary");
        arguments.put("connector", "test-connector");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("URLs list is required for PRIMARY_INITIATOR");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_emptyUrls() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "empty-urls-primary");
        arguments.put("urls", List.of());
        arguments.put("connector", "test-connector");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("URLs list is required for PRIMARY_INITIATOR");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_PRIMARY_INITIATOR_missingConnector() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "PRIMARY_INITIATOR");
        arguments.put("name", "no-connector-primary");
        arguments.put("urls", List.of("ws://secondary.example.com:8080"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("Connector is required for PRIMARY_INITIATOR");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_ACCEPTOR_minimal() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_ACCEPTOR");
        arguments.put("name", "test-acceptor");
        arguments.put("primaryHostName", "primary.example.com");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryAcceptor.getName()).thenReturn("test-acceptor");
        when(secondaryAcceptor.getType()).thenReturn(RemoteServer.Type.SECONDARY_ACCEPTOR);
        when(secondaryAcceptor.getPrimaryHostName()).thenReturn("primary.example.com");
        when(secondaryAcceptor.getPrincipal()).thenReturn(null);
        when(secondaryAcceptor.getMissingTopicNotificationFilter()).thenReturn(null);
        when(secondaryAcceptor.getConnectionOptions()).thenReturn(null);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryAcceptor));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: test-acceptor")
                            .contains("Type: SECONDARY_ACCEPTOR")
                            .contains("Primary Host Name: primary.example.com")
                            .contains("Principal: <anonymous>");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_ACCEPTOR_withEmptyPrincipal() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_ACCEPTOR");
        arguments.put("name", "empty-principal-acceptor");
        arguments.put("primaryHostName", "primary.example.com");
        arguments.put("principal", "");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryAcceptor.getName()).thenReturn("empty-principal-acceptor");
        when(secondaryAcceptor.getType()).thenReturn(RemoteServer.Type.SECONDARY_ACCEPTOR);
        when(secondaryAcceptor.getPrimaryHostName()).thenReturn("primary.example.com");
        when(secondaryAcceptor.getPrincipal()).thenReturn("");
        when(secondaryAcceptor.getMissingTopicNotificationFilter()).thenReturn(null);
        when(secondaryAcceptor.getConnectionOptions()).thenReturn(null);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryAcceptor));

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
    void testCreateRemoteServer_SECONDARY_ACCEPTOR_missingPrimaryHostName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_ACCEPTOR");
        arguments.put("name", "no-host-acceptor");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("primaryHostName is required for SECONDARY_ACCEPTOR");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_invalidType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "INVALID_TYPE");
        arguments.put("name", "invalid-server");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid parameters")
                            .contains("Invalid remote server type");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "test-server");
        arguments.put("url", "ws://primary.example.com:8080");

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
    void testCreateRemoteServer_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "error-server");
        arguments.put("url", "ws://primary.example.com:8080");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new SessionException("Remote server already exists")));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(CreateRemoteServerTool.TOOL_NAME)
                            .contains("Remote server already exists");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "timeout-server");
        arguments.put("url", "ws://primary.example.com:8080");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);

        CompletableFuture<RemoteServer> neverCompletingFuture = new CompletableFuture<>();
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
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
                            .contains(CreateRemoteServerTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_withInvalidConnectionOption() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "invalid-option-secondary");
        arguments.put("url", "ws://primary.example.com:8080");

        Map<String, String> connectionOptions = new HashMap<>();
        connectionOptions.put("RECONNECTION_TIMEOUT", "30000");
        connectionOptions.put("INVALID_OPTION", "value");
        arguments.put("connectionOptions", connectionOptions);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("invalid-option-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);

        // Only valid option should be included
        Map<ConnectionOption, String> options = new HashMap<>();
        options.put(ConnectionOption.RECONNECTION_TIMEOUT, "30000");
        when(secondaryInitiator.getConnectionOptions()).thenReturn(options);

        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("RECONNECTION_TIMEOUT: 30000")
                            .doesNotContain("INVALID_OPTION");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_INITIATOR_fullConfiguration() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "full-config-secondary");
        arguments.put("url", "wss://primary.example.com:8443");
        arguments.put("principal", "service-user");
        arguments.put("password", "secret123");
        arguments.put("missingTopicNotificationFilter", "sensors/>.*");

        Map<String, String> connectionOptions = new HashMap<>();
        connectionOptions.put("RECONNECTION_TIMEOUT", "60000");
        arguments.put("connectionOptions", connectionOptions);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("full-config-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("wss://primary.example.com:8443");
        when(secondaryInitiator.getPrincipal()).thenReturn("service-user");
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn("sensors/>.*");

        Map<ConnectionOption, String> options = new HashMap<>();
        options.put(ConnectionOption.RECONNECTION_TIMEOUT, "60000");
        when(secondaryInitiator.getConnectionOptions()).thenReturn(options);

        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: full-config-secondary")
                            .contains("Type: SECONDARY_INITIATOR")
                            .contains("URL: wss://primary.example.com:8443")
                            .contains("Principal: service-user")
                            .contains("Missing Topic Notification Filter: sensors/>.*")
                            .contains("Connection Options:")
                            .contains("RECONNECTION_TIMEOUT: 60000")
                            .contains("successfully created");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_SECONDARY_ACCEPTOR_fullConfiguration() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_ACCEPTOR");
        arguments.put("name", "full-acceptor");
        arguments.put("primaryHostName", "primary.prod.example.com");
        arguments.put("principal", "acceptor-user");
        arguments.put("password", "acceptor-pass");
        arguments.put("missingTopicNotificationFilter", "*/>events");

        Map<String, String> connectionOptions = new HashMap<>();
        connectionOptions.put("INPUT_BUFFER_SIZE", "131072");
        arguments.put("connectionOptions", connectionOptions);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryAcceptor.getName()).thenReturn("full-acceptor");
        when(secondaryAcceptor.getType()).thenReturn(RemoteServer.Type.SECONDARY_ACCEPTOR);
        when(secondaryAcceptor.getPrimaryHostName()).thenReturn("primary.prod.example.com");
        when(secondaryAcceptor.getPrincipal()).thenReturn("acceptor-user");
        when(secondaryAcceptor.getMissingTopicNotificationFilter()).thenReturn("*/>events");

        Map<ConnectionOption, String> options = new HashMap<>();
        options.put(ConnectionOption.INPUT_BUFFER_SIZE, "131072");
        when(secondaryAcceptor.getConnectionOptions()).thenReturn(options);

        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryAcceptor));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Name: full-acceptor")
                            .contains("Type: SECONDARY_ACCEPTOR")
                            .contains("Primary Host Name: primary.prod.example.com")
                            .contains("Principal: acceptor-user")
                            .contains("Missing Topic Notification Filter: */>events")
                            .contains("INPUT_BUFFER_SIZE: 131072");
                })
                .verifyComplete();
    }

    @Test
    void testCreateRemoteServer_emptyConnectionOptions() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "SECONDARY_INITIATOR");
        arguments.put("name", "empty-options-secondary");
        arguments.put("url", "ws://primary.example.com:8080");
        arguments.put("connectionOptions", new HashMap<String, String>());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(RemoteServers.class)).thenReturn(remoteServers);
        when(secondaryInitiator.getName()).thenReturn("empty-options-secondary");
        when(secondaryInitiator.getType()).thenReturn(RemoteServer.Type.SECONDARY_INITIATOR);
        when(secondaryInitiator.getUrl()).thenReturn("ws://primary.example.com:8080");
        when(secondaryInitiator.getPrincipal()).thenReturn(null);
        when(secondaryInitiator.getMissingTopicNotificationFilter()).thenReturn(null);
        when(secondaryInitiator.getConnectionOptions()).thenReturn(new HashMap<>());
        when(remoteServers.createRemoteServer(any(RemoteServer.class)))
                .thenReturn(CompletableFuture.completedFuture(secondaryInitiator));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .doesNotContain("Connection Options:");
                })
                .verifyComplete();
    }
}