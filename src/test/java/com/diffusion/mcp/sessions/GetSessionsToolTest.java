/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl;
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl.SessionFetchRequest;
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl.SessionFetchResult;
import com.pushtechnology.diffusion.client.features.control.impl.InternalClientControl.SessionFetchResult.SessionResult;
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
 * Unit tests for GetSessionsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetSessionsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private InternalClientControl internalClientControl;

    @Mock
    private SessionFetchRequest sessionFetchRequest;

    @Mock
    private SessionFetchResult sessionFetchResult;

    @Mock
    private SessionResult sessionResult1;

    @Mock
    private SessionResult sessionResult2;

    @Mock
    private SessionResult sessionResult3;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_sessions";
    private static final String SESSION_ID_1 = "0000000000000001-0000000000000001";
    private static final String SESSION_ID_2 = "0000000000000002-0000000000000002";
    private static final String SESSION_ID_3 = "0000000000000003-0000000000000003";

    @BeforeEach
    void setUp() {
        toolSpec = GetSessionsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(InternalClientControl.class)).thenReturn(internalClientControl);
        when(internalClientControl.sessionFetchRequest()).thenReturn(sessionFetchRequest);
        when(sessionFetchRequest.withProperties(anyString())).thenReturn(sessionFetchRequest);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieves a list of all currently connected session IDs");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetSessions_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> props1 = Map.of(Session.SESSION_ID, SESSION_ID_1);
        Map<String, String> props2 = Map.of(Session.SESSION_ID, SESSION_ID_2);
        Map<String, String> props3 = Map.of(Session.SESSION_ID, SESSION_ID_3);

        when(sessionResult1.properties()).thenReturn(props1);
        when(sessionResult2.properties()).thenReturn(props2);
        when(sessionResult3.properties()).thenReturn(props3);

        when(sessionFetchResult.sessions()).thenReturn(List.of(sessionResult1, sessionResult2, sessionResult3));
        when(sessionFetchResult.size()).thenReturn(3);
        when(sessionFetchResult.totalSelected()).thenReturn(3);
        when(sessionFetchResult.hasMore()).thenReturn(false);

        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_ID_1)
                            .contains(SESSION_ID_2)
                            .contains(SESSION_ID_3);
                })
                .verifyComplete();

        verify(sessionFetchRequest).fetch();
    }

    @Test
    void testGetSessions_withFilter() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("filter", "$Principal is 'admin'");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> props1 = Map.of(Session.SESSION_ID, SESSION_ID_1);

        when(sessionResult1.properties()).thenReturn(props1);
        when(sessionFetchResult.sessions()).thenReturn(List.of(sessionResult1));
        when(sessionFetchResult.size()).thenReturn(1);
        when(sessionFetchResult.totalSelected()).thenReturn(1);
        when(sessionFetchResult.hasMore()).thenReturn(false);

        when(sessionFetchRequest.filter(anyString())).thenReturn(sessionFetchRequest);
        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_ID_1);
                })
                .verifyComplete();

        verify(sessionFetchRequest).filter("$Principal is 'admin'");
        verify(sessionFetchRequest).fetch();
    }

    @Test
    void testGetSessions_emptySessions() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionFetchResult.sessions()).thenReturn(List.of());
        when(sessionFetchResult.size()).thenReturn(0);
        when(sessionFetchResult.totalSelected()).thenReturn(0);
        when(sessionFetchResult.hasMore()).thenReturn(false);

        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isEqualTo("[]");
                })
                .verifyComplete();
    }

    @Test
    void testGetSessions_singleSession() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> props1 = Map.of(Session.SESSION_ID, SESSION_ID_1);

        when(sessionResult1.properties()).thenReturn(props1);
        when(sessionFetchResult.sessions()).thenReturn(List.of(sessionResult1));
        when(sessionFetchResult.size()).thenReturn(1);
        when(sessionFetchResult.totalSelected()).thenReturn(1);
        when(sessionFetchResult.hasMore()).thenReturn(false);

        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_ID_1);
                })
                .verifyComplete();
    }

    @Test
    void testGetSessions_hasMoreResults() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> props1 = Map.of(Session.SESSION_ID, SESSION_ID_1);

        when(sessionResult1.properties()).thenReturn(props1);
        when(sessionFetchResult.sessions()).thenReturn(List.of(sessionResult1));
        when(sessionFetchResult.size()).thenReturn(1);
        when(sessionFetchResult.totalSelected()).thenReturn(100);
        when(sessionFetchResult.hasMore()).thenReturn(true);

        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_ID_1);
                })
                .verifyComplete();
    }

    @Test
    void testGetSessions_complexFilter() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("filter", "$ClientIP is '192.168.1.1' and $Transport is 'WEBSOCKET'");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> props1 = Map.of(Session.SESSION_ID, SESSION_ID_1);
        Map<String, String> props2 = Map.of(Session.SESSION_ID, SESSION_ID_2);

        when(sessionResult1.properties()).thenReturn(props1);
        when(sessionResult2.properties()).thenReturn(props2);
        when(sessionFetchResult.sessions()).thenReturn(List.of(sessionResult1, sessionResult2));
        when(sessionFetchResult.size()).thenReturn(2);
        when(sessionFetchResult.totalSelected()).thenReturn(2);
        when(sessionFetchResult.hasMore()).thenReturn(false);

        when(sessionFetchRequest.filter(anyString())).thenReturn(sessionFetchRequest);
        when(sessionFetchRequest.fetch())
                .thenReturn(CompletableFuture.completedFuture(sessionFetchResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(SESSION_ID_1)
                            .contains(SESSION_ID_2);
                })
                .verifyComplete();
    }

    @Test
    void testGetSessions_noActiveSession() {
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
    void testGetSessions_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<SessionFetchResult> neverCompletingFuture = new CompletableFuture<>();
        when(sessionFetchRequest.fetch())
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
    void testGetSessions_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        CompletableFuture<SessionFetchResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied: insufficient privileges"));
        when(sessionFetchRequest.fetch())
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
                            .contains(GetSessionsTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }
}