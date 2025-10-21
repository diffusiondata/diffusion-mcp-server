/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
 * Unit tests for LockRoleToPrincipalTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class LockRoleToPrincipalToolTest {

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
    private static final String TOOL_NAME = "lock_role_to_principal";

    @BeforeEach
    void setUp() {
        toolSpec = LockRoleToPrincipalTool.create(sessionManager);
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
                .contains("Lock a role in the security store to a specific principal");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testLockRoleToPrincipal_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("principalName", "super_admin");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("ADMIN", "super_admin"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");
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
                            .contains("ADMIN")
                            .contains("super_admin")
                            .contains("locked");
                })
                .verifyComplete();

        verify(securityControl).updateStore("lock role script");
    }

    @Test
    void testLockRoleToPrincipal_differentRoleAndPrincipal() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("principalName", "admin_user");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("OPERATOR", "admin_user"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");
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
                            .contains("OPERATOR")
                            .contains("admin_user");
                })
                .verifyComplete();

        verify(scriptBuilder).setRoleLockedByPrincipal("OPERATOR", "admin_user");
    }

    @Test
    void testLockRoleToPrincipal_lowercaseNames() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "developer");
        arguments.put("principalName", "lead_developer");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("developer", "lead_developer"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");
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
                            .contains("developer")
                            .contains("lead_developer");
                })
                .verifyComplete();
    }

    @Test
    void testLockRoleToPrincipal_namesWithSpecialCharacters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "SUPER_ADMIN_2024");
        arguments.put("principalName", "system-admin_01");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal(
                "SUPER_ADMIN_2024", "system-admin_01"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");
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
                            .contains("SUPER_ADMIN_2024")
                            .contains("system-admin_01");
                })
                .verifyComplete();
    }

    @Test
    void testLockRoleToPrincipal_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("principalName", "super_admin");

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
    void testLockRoleToPrincipal_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("principalName", "super_admin");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("ADMIN", "super_admin"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");

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
                    return content.text().contains("timed out") &&
                           content.text().contains("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void testLockRoleToPrincipal_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("principalName", "super_admin");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("ADMIN", "super_admin"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");

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
                            .contains("Permission denied")
                            .contains("ADMIN")
                            .contains("super_admin");
                })
                .verifyComplete();
    }

    @Test
    void testLockRoleToPrincipal_samePrincipalAsRole() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("principalName", "ADMIN");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setRoleLockedByPrincipal("ADMIN", "ADMIN"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("lock role script");
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
                    assertThat(content.text()).contains("ADMIN");
                })
                .verifyComplete();
    }
}