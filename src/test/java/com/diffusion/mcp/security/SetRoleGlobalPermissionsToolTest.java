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
 * Unit tests for SetRoleGlobalPermissionsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class SetRoleGlobalPermissionsToolTest {

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
    private static final String TOOL_NAME = "set_role_global_permissions";

    @BeforeEach
    void setUp() {
        toolSpec = SetRoleGlobalPermissionsTool.create(sessionManager);
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
                .contains("Set the global permissions assigned to a role");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testSetRoleGlobalPermissions_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("permissions", List.of("VIEW_SECURITY", "MODIFY_SECURITY"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("ADMIN"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                            .contains("VIEW_SECURITY")
                            .contains("MODIFY_SECURITY")
                            .contains("permissionCount")
                            .contains("updated");
                })
                .verifyComplete();

        verify(securityControl).updateStore("set global permissions script");
    }

    @Test
    void testSetRoleGlobalPermissions_singlePermission() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("permissions", List.of("REGISTER_HANDLER"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("OPERATOR"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                            .contains("REGISTER_HANDLER")
                            .contains("\"permissionCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_multiplePermissions() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "SUPER_ADMIN");
        arguments.put("permissions", List.of(
                "VIEW_SECURITY", "MODIFY_SECURITY", "REGISTER_HANDLER",
                "AUTHENTICATE", "VIEW_SESSION"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("SUPER_ADMIN"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                            .contains("\"permissionCount\":5");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_emptyPermissions() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "RESTRICTED");
        arguments.put("permissions", List.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("RESTRICTED"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                            .contains("RESTRICTED")
                            .contains("\"permissionCount\":0");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_lowercasePermissionNames() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "DEVELOPER");
        arguments.put("permissions", List.of("view_security", "register_handler"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("DEVELOPER"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                            .contains("view_security")
                            .contains("register_handler");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_invalidPermissionName() {
        // Arrange
        setupExchange();
        // Only mock what's needed - validation fails before using SecurityControl
        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("permissions", List.of("VIEW_SECURITY", "INVALID_PERMISSION"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid global permission name")
                            .contains("Valid permissions include");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("permissions", List.of("VIEW_SECURITY"));

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
    void testSetRoleGlobalPermissions_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("permissions", List.of("VIEW_SECURITY"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("ADMIN"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");

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
    void testSetRoleGlobalPermissions_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("permissions", List.of("VIEW_SECURITY"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("ADMIN"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");

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
                            .contains("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void testSetRoleGlobalPermissions_lowercaseRoleName() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "developer");
        arguments.put("permissions", List.of("REGISTER_HANDLER"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setGlobalPermissions(eq("developer"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set global permissions script");
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
                    assertThat(content.text()).contains("developer");
                })
                .verifyComplete();
    }
}
