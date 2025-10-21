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
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.AnonymousConnectionAction;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation.MatchesSessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SessionPropertyValidation.ValuesSessionPropertyValidation;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SystemAuthenticationConfiguration;
import com.pushtechnology.diffusion.client.features.control.clients.SystemAuthenticationControl.SystemPrincipal;
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
 * Unit tests for GetSystemAuthenticationTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetSystemAuthenticationToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private SystemAuthenticationControl authControl;

    @Mock
    private SystemAuthenticationConfiguration authConfig;

    @Mock
    private SystemPrincipal principal1;

    @Mock
    private SystemPrincipal principal2;

    @Mock
    private ValuesSessionPropertyValidation valuesValidation;

    @Mock
    private MatchesSessionPropertyValidation matchesValidation;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_system_authentication";

    @BeforeEach
    void setUp() {
        toolSpec = GetSystemAuthenticationTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    private void setupMocks() {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(SystemAuthenticationControl.class)).thenReturn(authControl);
    }

    private void setupBasicAuthConfig() {
        when(authConfig.getPrincipals()).thenReturn(List.of(principal1, principal2));
        when(authConfig.getAnonymousAction()).thenReturn(AnonymousConnectionAction.ALLOW);
        when(authConfig.getRolesForAnonymousSessions()).thenReturn(Set.of("ANONYMOUS"));
        when(authConfig.getTrustedClientProposedProperties()).thenReturn(Map.of());

        // Setup principal1
        when(principal1.getName()).thenReturn("admin");
        when(principal1.getAssignedRoles()).thenReturn(Set.of("ADMIN", "OPERATOR"));
        when(principal1.getLockingPrincipal()).thenReturn(Optional.empty());

        // Setup principal2
        when(principal2.getName()).thenReturn("client");
        when(principal2.getAssignedRoles()).thenReturn(Set.of("CLIENT"));
        when(principal2.getLockingPrincipal()).thenReturn(Optional.empty());
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieve the current system authentication store configuration");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetSystemAuthentication_success() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("admin")
                            .contains("client")
                            .contains("ADMIN")
                            .contains("OPERATOR")
                            .contains("CLIENT")
                            .contains("ANONYMOUS")
                            .contains("principals")
                            .contains("anonymousAction")
                            .contains("rolesForAnonymousSessions")
                            .contains("trustedClientProposedProperties");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_withLockedPrincipal() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(principal1.getLockingPrincipal()).thenReturn(Optional.of("super_admin"));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("super_admin")
                            .contains("lockingPrincipal");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_anonymousActionDeny() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(authConfig.getAnonymousAction()).thenReturn(AnonymousConnectionAction.DENY);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("DENY");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_anonymousActionAbstain() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(authConfig.getAnonymousAction()).thenReturn(AnonymousConnectionAction.ABSTAIN);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("ABSTAIN");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_withValuesValidation() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(valuesValidation.getValues()).thenReturn(Set.of("value1", "value2", "value3"));
        when(authConfig.getTrustedClientProposedProperties())
                .thenReturn(Map.of("department", valuesValidation));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("department")
                            .contains("values")
                            .contains("value1")
                            .contains("value2")
                            .contains("value3");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_withMatchesValidation() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(matchesValidation.getRegex()).thenReturn("^[A-Z]{3}-\\d{4}$");
        when(authConfig.getTrustedClientProposedProperties())
                .thenReturn(Map.of("employeeId", matchesValidation));

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("employeeId")
                            .contains("regex")
                            .contains("^[A-Z]{3}-\\\\d{4}$");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_withMultipleTrustedProperties() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(valuesValidation.getValues()).thenReturn(Set.of("US", "UK", "CA"));
        when(matchesValidation.getRegex()).thenReturn("^EMP-\\d+$");

        Map<String, SessionPropertyValidation> trustedProps = Map.of(
                "country", valuesValidation,
                "employeeId", matchesValidation
        );
        when(authConfig.getTrustedClientProposedProperties()).thenReturn(trustedProps);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("country")
                            .contains("employeeId")
                            .contains("US")
                            .contains("UK")
                            .contains("CA")
                            .contains("^EMP-\\\\d+$");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_noPrincipals() {
        // Arrange
        setupExchange();
        setupMocks();

        when(authConfig.getPrincipals()).thenReturn(List.of());
        when(authConfig.getAnonymousAction()).thenReturn(AnonymousConnectionAction.ALLOW);
        when(authConfig.getRolesForAnonymousSessions()).thenReturn(Set.of());
        when(authConfig.getTrustedClientProposedProperties()).thenReturn(Map.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("principals")
                            .contains("[]");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_principalWithNoRoles() {
        // Arrange
        setupExchange();
        setupMocks();
        setupBasicAuthConfig();

        when(principal2.getAssignedRoles()).thenReturn(Set.of());

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        when(authControl.getSystemAuthentication())
                .thenReturn(CompletableFuture.completedFuture(authConfig));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("client");
                })
                .verifyComplete();
    }

    @Test
    void testGetSystemAuthentication_noActiveSession() {
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
    void testGetSystemAuthentication_timeout() {
        // Arrange
        setupExchange();
        setupMocks();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        CompletableFuture<SystemAuthenticationConfiguration> neverCompletingFuture =
                new CompletableFuture<>();
        when(authControl.getSystemAuthentication())
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
    void testGetSystemAuthentication_permissionDenied() {
        // Arrange
        setupExchange();
        setupMocks();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(new HashMap<>())
                .build();

        CompletableFuture<SystemAuthenticationConfiguration> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(
                new SessionException("Permission denied: insufficient privileges"));
        when(authControl.getSystemAuthentication())
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