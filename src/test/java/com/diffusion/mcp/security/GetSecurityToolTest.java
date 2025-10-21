/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl.Role;
import com.pushtechnology.diffusion.client.features.control.clients.SecurityControl.SecurityConfiguration;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;
import com.pushtechnology.diffusion.client.types.GlobalPermission;
import com.pushtechnology.diffusion.client.types.PathPermission;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for GetSecurityTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetSecurityToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SecurityControl securityControl;

    @Mock
    private SecurityConfiguration securityConfig;

    @Mock
    private Role role1;

    @Mock
    private Role role2;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_security";

    @BeforeEach
    void setUp() {
        toolSpec = GetSecurityTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SecurityControl.class)).thenReturn(securityControl);
    }

    private void setupBasicSecurityConfig() {
        when(securityConfig.getRolesForAnonymousSessions()).thenReturn(Set.of("ANONYMOUS"));
        when(securityConfig.getRolesForNamedSessions()).thenReturn(Set.of("CLIENT"));
        when(securityConfig.getRoles()).thenReturn(List.of(role1, role2));
        when(securityConfig.getIsolatedPaths()).thenReturn(Set.of());

        // Setup role1
        when(role1.getName()).thenReturn("ADMIN");
        when(role1.getGlobalPermissions()).thenReturn(Set.of(
                GlobalPermission.REGISTER_HANDLER,
                GlobalPermission.AUTHENTICATE));
        when(role1.getDefaultPathPermissions()).thenReturn(Set.of(
                PathPermission.READ_TOPIC,
                PathPermission.UPDATE_TOPIC));
        when(role1.getPathPermissions()).thenReturn(Map.of());
        when(role1.getIncludedRoles()).thenReturn(Set.of());
        when(role1.getLockingPrincipal()).thenReturn(Optional.empty());

        // Setup role2
        when(role2.getName()).thenReturn("CLIENT");
        when(role2.getGlobalPermissions()).thenReturn(Set.of());
        when(role2.getDefaultPathPermissions()).thenReturn(Set.of(
                PathPermission.READ_TOPIC));
        when(role2.getPathPermissions()).thenReturn(Map.of());
        when(role2.getIncludedRoles()).thenReturn(Set.of());
        when(role2.getLockingPrincipal()).thenReturn(Optional.empty());
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieve the current security store configuration");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetSecurity_success() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

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
                            .contains("CLIENT")
                            .contains("ANONYMOUS")
                            .contains("rolesForAnonymousSessions")
                            .contains("rolesForNamedSessions")
                            .contains("roles")
                            .contains("isolatedPaths");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withIsolatedPaths() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        when(securityConfig.getIsolatedPaths()).thenReturn(
                Set.of("trading/stocks", "admin/config"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("trading/stocks")
                            .contains("admin/config");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withPathPermissions() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        Map<String, Set<PathPermission>> pathPerms = Map.of(
                "trading/*", Set.of(PathPermission.READ_TOPIC, PathPermission.UPDATE_TOPIC),
                "public/*", Set.of(PathPermission.READ_TOPIC)
        );
        when(role1.getPathPermissions()).thenReturn(pathPerms);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("trading/*")
                            .contains("public/*")
                            .contains("pathPermissions");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withIncludedRoles() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        when(role1.getIncludedRoles()).thenReturn(Set.of("BASE_ROLE", "COMMON_ROLE"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("BASE_ROLE")
                            .contains("COMMON_ROLE")
                            .contains("includedRoles");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withLockingPrincipal() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        when(role1.getLockingPrincipal()).thenReturn(Optional.of("admin_principal"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("admin_principal")
                            .contains("lockingPrincipal");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withEmptyConfiguration() {
        // Arrange
        setupExchange();
        setupMocks();

        when(securityConfig.getRolesForAnonymousSessions()).thenReturn(Set.of());
        when(securityConfig.getRolesForNamedSessions()).thenReturn(Set.of());
        when(securityConfig.getRoles()).thenReturn(List.of());
        when(securityConfig.getIsolatedPaths()).thenReturn(Set.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("rolesForAnonymousSessions")
                            .contains("rolesForNamedSessions")
                            .contains("roles")
                            .contains("isolatedPaths");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_withMultipleGlobalPermissions() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicSecurityConfig();

        when(role1.getGlobalPermissions()).thenReturn(Set.of(
                GlobalPermission.REGISTER_HANDLER,
                GlobalPermission.AUTHENTICATE,
                GlobalPermission.VIEW_SERVER,
                GlobalPermission.VIEW_SECURITY,
                GlobalPermission.MODIFY_SECURITY));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(securityControl.getSecurity())
                .thenReturn(CompletableFuture.completedFuture(securityConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("REGISTER_HANDLER")
                            .contains("AUTHENTICATE")
                            .contains("VIEW_SERVER")
                            .contains("VIEW_SECURITY")
                            .contains("MODIFY_SECURITY");
                })
                .verifyComplete();
    }

    @Test
    void testGetSecurity_noActiveSession() {
        // Arrange
        setupExchange();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
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
    void testGetSecurity_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        CompletableFuture<SecurityConfiguration> neverCompletingFuture =
                new CompletableFuture<>();
        when(securityControl.getSecurity())
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
    void testGetSecurity_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        CompletableFuture<SecurityConfiguration> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new SessionException("Permission denied: insufficient privileges"));
        when(securityControl.getSecurity())
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