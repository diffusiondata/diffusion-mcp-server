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
 * Unit tests for CreateTopicViewTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class CreateTopicViewToolTest {

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
    private static final String TOOL_NAME = "create_topic_view";

    @BeforeEach
    void setUp() {
        toolSpec = CreateTopicViewTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates a new named topic view");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testCreateTopicView_simple() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "my-view");
        arguments.put("specification", "map ?sensors// to dashboard/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("my-view");
        when(topicView.getSpecification()).thenReturn("map ?sensors// to dashboard/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView("my-view", "map ?sensors// to dashboard/<path(1)>"))
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
                            .contains("\"status\":\"created\"");
                })
                .verifyComplete();

        verify(topics).createTopicView("my-view", "map ?sensors// to dashboard/<path(1)>");
    }

    @Test
    void testCreateTopicView_withRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "admin-view");
        arguments.put("specification", "map ?admin// to admin/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("admin-view");
        when(topicView.getSpecification()).thenReturn("map ?admin// to admin/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of("ADMINISTRATOR", "OPERATOR"));
        when(topics.createTopicView("admin-view", "map ?admin// to admin/<path(1)>"))
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
    void testCreateTopicView_complexSpecification() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "complex-view");
        arguments.put("specification",
                "map ?data/sensors/ to processed/<path(1)>/<scalar(double:temperature)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("complex-view");
        when(topicView.getSpecification())
                .thenReturn("map ?data/sensors/ to processed/<path(1)>/<scalar(double:temperature)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView("complex-view",
                "map ?data/sensors/ to processed/<path(1)>/<scalar(double:temperature)>"))
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
    void testCreateTopicView_withSpecialCharacters() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "special-view-123");
        arguments.put("specification", "map ?test// to result/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("special-view-123");
        when(topicView.getSpecification()).thenReturn("map ?test// to result/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView("special-view-123", "map ?test// to result/<path(1)>"))
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
    void testCreateTopicView_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "test-view");
        arguments.put("specification", "map ?sensors// to dashboard/<path(1)>");

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
    void testCreateTopicView_permissionDenied() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "restricted-view");
        arguments.put("specification", "map ?admin// to admin/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.createTopicView("restricted-view", "map ?admin// to admin/<path(1)>"))
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
                            .contains(CreateTopicViewTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testCreateTopicView_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "timeout-view");
        arguments.put("specification", "map ?sensors// to dashboard/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);

        CompletableFuture<TopicView> neverCompletingFuture = new CompletableFuture<>();
        when(topics.createTopicView("timeout-view", "map ?sensors// to dashboard/<path(1)>"))
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
                            .contains(CreateTopicViewTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testCreateTopicView_replacesExisting() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "existing-view");
        arguments.put("specification", "map ?new// to updated/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("existing-view");
        when(topicView.getSpecification()).thenReturn("map ?new// to updated/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView("existing-view", "map ?new// to updated/<path(1)>"))
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
                            .contains("\"name\":\"existing-view\"")
                            .contains("\"status\":\"created\"");
                })
                .verifyComplete();
    }

    @Test
    void testCreateTopicView_emptyRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "no-roles-view");
        arguments.put("specification", "map ?data// to output/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("no-roles-view");
        when(topicView.getSpecification()).thenReturn("map ?data// to output/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView("no-roles-view", "map ?data// to output/<path(1)>"))
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
    void testCreateTopicView_multipleRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "multi-role-view");
        arguments.put("specification", "map ?secure// to secure/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn("multi-role-view");
        when(topicView.getSpecification()).thenReturn("map ?secure// to secure/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of("ADMIN", "OPERATOR", "VIEWER"));
        when(topics.createTopicView("multi-role-view", "map ?secure// to secure/<path(1)>"))
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
                            .contains("VIEWER");
                })
                .verifyComplete();
    }

    @Test
    void testCreateTopicView_longName() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        String longName = "very-long-topic-view-name-with-many-characters-and-hyphens";
        arguments.put("name", longName);
        arguments.put("specification", "map ?test// to test/<path(1)>");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView.getName()).thenReturn(longName);
        when(topicView.getSpecification()).thenReturn("map ?test// to test/<path(1)>");
        when(topicView.getRoles()).thenReturn(Set.of());
        when(topics.createTopicView(longName, "map ?test// to test/<path(1)>"))
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
}