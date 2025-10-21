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
 * Unit tests for TrustClientProposedPropertyTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class TrustClientProposedPropertyToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SystemAuthenticationControl systemAuthenticationControl;

    @Mock
    private ScriptBuilder scriptBuilder;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "trust_client_proposed_property";

    @BeforeEach
    void setUp() {
        toolSpec = TrustClientProposedPropertyTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SystemAuthenticationControl.class))
                .thenReturn(systemAuthenticationControl);
        when(systemAuthenticationControl.scriptBuilder()).thenReturn(scriptBuilder);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Configures the system authentication to trust a client proposed session property");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testTrustClientProposedProperty_success() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER_TIER");
        arguments.put("allowedValues", List.of("premium", "standard", "basic"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("USER_TIER"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("USER_TIER")
                            .contains("premium")
                            .contains("standard")
                            .contains("basic")
                            .contains("valueCount")
                            .contains("trusted");
                })
                .verifyComplete();

        verify(systemAuthenticationControl).updateStore("trust property script");
    }

    @Test
    void testTrustClientProposedProperty_singleValue() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "DEPARTMENT");
        arguments.put("allowedValues", List.of("sales"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("DEPARTMENT"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("DEPARTMENT")
                            .contains("sales")
                            .contains("\"valueCount\":1");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_multipleValues() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "REGION");
        arguments.put("allowedValues", List.of("north", "south", "east", "west", "central"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("REGION"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("\"valueCount\":5");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_valuesWithWhitespace() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "STATUS");
        arguments.put("allowedValues", List.of("  active  ", " inactive ", "pending"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("STATUS"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    // Values should be trimmed
                    assertThat(content.text())
                            .contains("active")
                            .contains("inactive")
                            .contains("pending");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_duplicateValues() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "TIER");
        arguments.put("allowedValues", List.of("gold", "silver", "gold", "bronze"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("TIER"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    // Should have 3 unique values after Set deduplication
                    assertThat(content.text())
                            .contains("\"valueCount\":3");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_propertyNameWithUnderscores() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER_ACCESS_LEVEL");
        arguments.put("allowedValues", List.of("admin", "user"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("USER_ACCESS_LEVEL"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("USER_ACCESS_LEVEL");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_numericValues() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "PRIORITY");
        arguments.put("allowedValues", List.of("1", "2", "3", "4", "5"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("PRIORITY"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("PRIORITY")
                            .contains("\"valueCount\":5");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER_TIER");
        arguments.put("allowedValues", List.of("premium", "standard"));

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
    void testTrustClientProposedProperty_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER_TIER");
        arguments.put("allowedValues", List.of("premium"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("USER_TIER"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");

        CompletableFuture<Object> neverCompletingFuture = new CompletableFuture<>();
        when(systemAuthenticationControl.updateStore(anyString()))
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
                           content.text().contains("USER_TIER");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER_TIER");
        arguments.put("allowedValues", List.of("premium"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("USER_TIER"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");

        CompletableFuture<Object> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new PermissionsException("Permission denied: insufficient privileges"));
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("USER_TIER");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_specialCharactersInPropertyName() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "USER-TYPE_2024");
        arguments.put("allowedValues", List.of("premium", "standard"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("USER-TYPE_2024"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("USER-TYPE_2024");
                })
                .verifyComplete();
    }

    @Test
    void testTrustClientProposedProperty_mixedCaseValues() {
        // Arrange
        setupExchange();
        setupMocks();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("propertyName", "Environment");
        arguments.put("allowedValues", List.of("Production", "Staging", "Development"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(scriptBuilder.trustClientProposedPropertyIn(eq("Environment"), anySet()))
                .thenReturn(scriptBuilder);
        when(scriptBuilder.script()).thenReturn("trust property script");
        when(systemAuthenticationControl.updateStore(anyString()))
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
                            .contains("Production")
                            .contains("Staging")
                            .contains("Development");
                })
                .verifyComplete();
    }
}