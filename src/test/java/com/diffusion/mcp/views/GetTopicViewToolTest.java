/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.control.topics.views.TopicView;
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
 * Unit tests for GetTopicViewTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class GetTopicViewToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Topics topics;

    @Mock
    private TopicView topicView;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "get_topic_view";

    @BeforeEach
    void setUp() {
        toolSpec = GetTopicViewTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Retrieves details of a named topic view");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetTopicView_exists() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "my-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?sensors// to dashboard/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.getTopicView("my-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);

                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"my-view\"")
                            .contains("\"specification\":\"map ?sensors// to dashboard/<path(1)>\"")
                            .contains("\"exists\":true");
                })
                .verifyComplete();

        verify(topics).getTopicView("my-view");
    }

    @Test
    void testGetTopicView_withRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "admin-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?admin// to admin/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of("ADMINISTRATOR", "OPERATOR"));
        when(topics.getTopicView("admin-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"admin-view\"")
                            .contains("\"roles\":")
                            .contains("ADMINISTRATOR")
                            .contains("OPERATOR");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_withEmptyRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "no-roles-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?data// to output/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.getTopicView("no-roles-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"roles\":[]");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_complexSpecification() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "complex-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification())
                .thenReturn("map ?data/sensors/ to processed/<path(1)>/<scalar(double:temperature)>");
        when(topicView.getRoles()).thenReturn(Set.of("VIEWER"));
        when(topics.getTopicView("complex-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"complex-view\"")
                            .contains("processed/<path(1)>/<scalar(double:temperature)>");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_notFound() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "nonexistent-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.getTopicView("nonexistent-view"))
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
                            .contains("Topic view 'nonexistent-view' not found");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-view");

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
    void testGetTopicView_permissionDenied() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "restricted-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.getTopicView("restricted-view"))
                .thenReturn(CompletableFuture.failedFuture(
                        new PermissionsException("Permission denied: insufficient privileges")));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(GetTopicViewTool.TOOL_NAME)
                            .contains("Permission denied")
                            .contains("restricted-view");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "timeout-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);

        CompletableFuture<TopicView> neverCompletingFuture = new CompletableFuture<>();
        when(topics.getTopicView("timeout-view"))
                .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(GetTopicViewTool.TOOL_NAME)
                            .contains("timed out after 10 seconds")
                            .contains("timeout-view");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_withSpecialCharacters() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "special-view-123");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?test// to result/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.getTopicView("special-view-123"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"special-view-123\"");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_longName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        String longName = "very-long-topic-view-name-with-many-characters-and-hyphens";
        arguments.put("name", longName);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?test// to test/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.getTopicView(longName))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"" + longName + "\"");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_multipleRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "multi-role-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?secure// to secure/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of("ADMIN", "OPERATOR", "VIEWER", "AUDITOR"));
        when(topics.getTopicView("multi-role-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"roles\":")
                            .contains("ADMIN")
                            .contains("OPERATOR")
                            .contains("VIEWER")
                            .contains("AUDITOR");
                })
                .verifyComplete();
    }

    @Test
    void testGetTopicView_withHyphenatedName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "production-sensor-view");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getSpecification()).thenReturn("map ?production/sensors// to sensors/<path(2)>");
        when(topicView.getRoles()).thenReturn(Set.of("PRODUCTION"));
        when(topics.getTopicView("production-sensor-view"))
                .thenReturn(CompletableFuture.completedFuture(topicView));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"name\":\"production-sensor-view\"")
                            .contains("PRODUCTION");
                })
                .verifyComplete();

        verify(topics).getTopicView("production-sensor-view");
    }
}