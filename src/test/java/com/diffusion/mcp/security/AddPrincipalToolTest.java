/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.ScriptBuilder;
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
 * Unit tests for AddPrincipalTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class AddPrincipalToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SystemAuthenticationControl authControl;

    @Mock
    private ScriptBuilder scriptBuilder;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "add_principal";
    private static final String PRINCIPAL_NAME = "alice";
    private static final String PASSWORD = "secret123";
    private static final List<String> ROLES = List.of("ADMIN", "TRADER");

    @BeforeEach
    void setUp() {
        toolSpec = AddPrincipalTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SystemAuthenticationControl.class)).thenReturn(authControl);
        when(authControl.scriptBuilder()).thenReturn(scriptBuilder);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Add a new principal to the system authentication store");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testAddPrincipal_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", PRINCIPAL_NAME);
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq(PRINCIPAL_NAME), eq(PASSWORD), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");
        when(authControl.updateStore(anyString()))
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
                            .contains(PRINCIPAL_NAME)
                            .contains("ADMIN")
                            .contains("TRADER")
                            .contains("added");
                })
                .verifyComplete();

        verify(authControl).updateStore("add principal script");
    }

    @Test
    void testAddPrincipal_withLockingPrincipal() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", PRINCIPAL_NAME);
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);
        arguments.put("lockingPrincipal", "admin");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq(PRINCIPAL_NAME), eq(PASSWORD), anySet(), eq("admin")))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal with lock script");
        when(authControl.updateStore(anyString()))
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
                            .contains(PRINCIPAL_NAME)
                            .contains("admin")
                            .contains("lockingPrincipal");
                })
                .verifyComplete();

        verify(authControl).updateStore("add principal with lock script");
    }

    @Test
    void testAddPrincipal_withMultipleRoles() {
        // Arrange
        setupExchange();
        setupMocks();

        List<String> multipleRoles = List.of("ADMIN", "TRADER", "OPERATOR", "VIEWER");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", "bob");
        arguments.put("password", "pass456");
        arguments.put("roles", multipleRoles);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq("bob"), eq("pass456"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");
        when(authControl.updateStore(anyString()))
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
                            .contains("bob")
                            .contains("ADMIN")
                            .contains("TRADER")
                            .contains("OPERATOR")
                            .contains("VIEWER");
                })
                .verifyComplete();
    }

    @Test
    void testAddPrincipal_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", PRINCIPAL_NAME);
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);

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
    void testAddPrincipal_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", PRINCIPAL_NAME);
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq(PRINCIPAL_NAME), eq(PASSWORD), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");

        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(authControl.updateStore(anyString()))
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
    void testAddPrincipal_alreadyExists() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", PRINCIPAL_NAME);
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq(PRINCIPAL_NAME), eq(PASSWORD), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new SessionException("Principal already exists"));
        when(authControl.updateStore(anyString()))
                .thenAnswer(invocation -> failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Principal already exists");
                })
                .verifyComplete();
    }

    @Test
    void testAddPrincipal_emptyRolesList() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", "minimal_user");
        arguments.put("password", "pass");
        arguments.put("roles", List.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq("minimal_user"), eq("pass"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");
        when(authControl.updateStore(anyString()))
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
                            .contains("minimal_user")
                            .contains("added");
                })
                .verifyComplete();
    }

    @Test
    void testAddPrincipal_specialCharactersInName() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("principalName", "service_account-123");
        arguments.put("password", PASSWORD);
        arguments.put("roles", ROLES);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.addPrincipal(eq("service_account-123"), eq(PASSWORD), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("add principal script");
        when(authControl.updateStore(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("service_account-123");
                })
                .verifyComplete();
    }
}