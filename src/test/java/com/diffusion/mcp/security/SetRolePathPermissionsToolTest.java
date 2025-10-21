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
 * Unit tests for SetRolePathPermissionsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class SetRolePathPermissionsToolTest {

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
    private static final String TOOL_NAME = "set_role_path_permissions";

    @BeforeEach
    void setUp() {
        toolSpec = SetRolePathPermissionsTool.create(sessionManager);
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
                .contains("Set specific path permissions for a role");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testSetRolePathPermissions_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "markets/forex");
        arguments.put("permissions", List.of("READ_TOPIC", "UPDATE_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("TRADER"), eq("markets/forex"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                            .contains("TRADER")
                            .contains("markets/forex")
                            .contains("READ_TOPIC")
                            .contains("UPDATE_TOPIC")
                            .contains("permissionCount")
                            .contains("updated");
                })
                .verifyComplete();

        verify(securityControl).updateStore("set path permissions script");
    }

    @Test
    void testSetRolePathPermissions_singlePermission() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "VIEWER");
        arguments.put("path", "public/data");
        arguments.put("permissions", List.of("READ_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("VIEWER"), eq("public/data"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                            .contains("\"permissionCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_multiplePermissions() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("path", "admin/");
        arguments.put("permissions", List.of(
                "READ_TOPIC", "UPDATE_TOPIC", "SELECT_TOPIC", "MODIFY_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("ADMIN"), eq("admin/"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                            .contains("\"permissionCount\":4");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_emptyPermissions() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "RESTRICTED");
        arguments.put("path", "restricted/area");
        arguments.put("permissions", List.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("RESTRICTED"), eq("restricted/area"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                            .contains("\"permissionCount\":0");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_rootPath() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "SUPER_ADMIN");
        arguments.put("path", "/");
        arguments.put("permissions", List.of("READ_TOPIC", "UPDATE_TOPIC", "MODIFY_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("SUPER_ADMIN"), eq("/"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                    assertThat(content.text()).contains("/");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_deeplyNestedPath() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "org/dept/team/project/data");
        arguments.put("permissions", List.of("READ_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(
                eq("OPERATOR"), eq("org/dept/team/project/data"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                    assertThat(content.text()).contains("org/dept/team/project/data");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_lowercasePermissionNames() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "DEVELOPER");
        arguments.put("path", "dev/testing");
        arguments.put("permissions", List.of("read_topic", "update_topic"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("DEVELOPER"), eq("dev/testing"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");
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
                            .contains("read_topic")
                            .contains("update_topic");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_invalidPermissionName() {
        // Arrange
        setupExchange();
        // Only mock what's needed - validation fails before using SecurityControl
        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "markets/forex");
        arguments.put("permissions", List.of("READ_TOPIC", "INVALID_PERMISSION"));

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
                            .contains("Invalid path permission name")
                            .contains("Valid permissions include");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "markets/forex");
        arguments.put("permissions", List.of("READ_TOPIC"));

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
    void testSetRolePathPermissions_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "markets/forex");
        arguments.put("permissions", List.of("READ_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("TRADER"), eq("markets/forex"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");

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
                           content.text().contains("TRADER") &&
                           content.text().contains("markets/forex");
                })
                .verifyComplete();
    }

    @Test
    void testSetRolePathPermissions_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "markets/forex");
        arguments.put("permissions", List.of("READ_TOPIC"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.setPathPermissions(eq("TRADER"), eq("markets/forex"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("set path permissions script");

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
                            .contains("TRADER");
                })
                .verifyComplete();
    }
}
