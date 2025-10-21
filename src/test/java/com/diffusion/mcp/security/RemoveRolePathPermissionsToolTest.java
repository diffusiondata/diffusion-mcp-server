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
 * Unit tests for RemoveRolePathPermissionsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class RemoveRolePathPermissionsToolTest {

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
    private static final String TOOL_NAME = "remove_role_path_permissions";

    @BeforeEach
    void setUp() {
        toolSpec = RemoveRolePathPermissionsTool.create(sessionManager);
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
                .contains("Remove specific path permission assignments");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testRemoveRolePathPermissions_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "trading/stocks");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("OPERATOR", "trading/stocks"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
                            .contains("trading/stocks")
                            .contains("removed");
                })
                .verifyComplete();

        verify(securityControl).updateStore("remove path permissions script");
    }

    @Test
    void testRemoveRolePathPermissions_differentRoleAndPath() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "CLIENT");
        arguments.put("path", "public/data");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("CLIENT", "public/data"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
                            .contains("CLIENT")
                            .contains("public/data");
                })
                .verifyComplete();

        verify(scriptBuilder).removePathPermissions("CLIENT", "public/data");
    }

    @Test
    void testRemoveRolePathPermissions_rootPath() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "ADMIN");
        arguments.put("path", "/");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("ADMIN", "/"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
    void testRemoveRolePathPermissions_deeplyNestedPath() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "VIEWER");
        arguments.put("path", "org/dept/team/project/data");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("VIEWER", "org/dept/team/project/data"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
    void testRemoveRolePathPermissions_pathWithTrailingSlash() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "TRADER");
        arguments.put("path", "market/stocks/");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("TRADER", "market/stocks/"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
                    assertThat(content.text()).contains("market/stocks/");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRolePathPermissions_lowercaseRoleName() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "developer");
        arguments.put("path", "projects/code");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("developer", "projects/code"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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

    @Test
    void testRemoveRolePathPermissions_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "trading/stocks");

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
    void testRemoveRolePathPermissions_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "trading/stocks");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("OPERATOR", "trading/stocks"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");

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
                           content.text().contains("OPERATOR") &&
                           content.text().contains("trading/stocks");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRolePathPermissions_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "trading/stocks");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("OPERATOR", "trading/stocks"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");

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
                            .contains("OPERATOR")
                            .contains("trading/stocks");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRolePathPermissions_roleWithSpecialCharacters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "SUPER_ADMIN_2024");
        arguments.put("path", "secure/data");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("SUPER_ADMIN_2024", "secure/data"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
                    assertThat(content.text()).contains("SUPER_ADMIN_2024");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveRolePathPermissions_pathWithSpecialCharacters() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("roleName", "OPERATOR");
        arguments.put("path", "data/user-123/items_2024");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.removePathPermissions("OPERATOR", "data/user-123/items_2024"))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("remove path permissions script");
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
                    assertThat(content.text()).contains("data/user-123/items_2024");
                })
                .verifyComplete();
    }
}
