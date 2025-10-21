/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
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
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl.ScriptBuilder;
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
 * Unit tests for SetRolesForAnonymousSessionsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class SetRolesForAnonymousSessionsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SecurityControl securityControl;

    @Mock
    private ScriptBuilder scriptBuilder;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "set_roles_for_anonymous_sessions";

    @BeforeEach
    void setUp() {
        toolSpec = SetRolesForAnonymousSessionsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SecurityControl.class)).thenReturn(securityControl);
        when(securityControl.scriptBuilder()).thenReturn(scriptBuilder);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Set the default roles assigned to anonymous sessions");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testSetRolesForAnonymousSessions_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS", "GUEST"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("ANONYMOUS")
                            .contains("GUEST")
                            .contains("roleCount")
                            .contains("updated");
                })
                .verifyComplete();

        verify(securityControl).updateStore("set anonymous roles script");
    }

    @Test
    void testSetRolesForAnonymousSessions_singleRole() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("PUBLIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("PUBLIC")
                            .contains("\"roleCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolesForAnonymousSessions_multipleRoles() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS", "GUEST", "VIEWER", "PUBLIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("\"roleCount\":4");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolesForAnonymousSessions_emptyRoles() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("\"roleCount\":0");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolesForAnonymousSessions_lowercaseRoleNames() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("anonymous", "guest"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("anonymous")
                            .contains("guest");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolesForAnonymousSessions_roleNamesWithSpecialCharacters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS_USER", "GUEST_2024"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");
        when(securityControl.updateStore(anyString()))
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
                            .contains("ANONYMOUS_USER")
                            .contains("GUEST_2024");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolesForAnonymousSessions_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS"));

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
    void testSetRolesForAnonymousSessions_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");

        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(securityControl.updateStore(anyString()))
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
    void testSetRolesForAnonymousSessions_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roles", List.of("ANONYMOUS"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRolesForAnonymousSessions(anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set anonymous roles script");

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied: insufficient privileges"));
        when(securityControl.updateStore(anyString()))
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
                            .contains("Permission denied");
                })
                .verifyComplete();
    }
}
