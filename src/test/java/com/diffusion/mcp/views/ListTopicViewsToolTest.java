/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 * Unit tests for ListTopicViewsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ListTopicViewsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Topics topics;

    @Mock
    private TopicView topicView1;

    @Mock
    private TopicView topicView2;

    @Mock
    private TopicView topicView3;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "list_topic_views";

    @BeforeEach
    void setUp() {
        toolSpec = ListTopicViewsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Lists all topic views");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testListTopicViews_empty() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

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
                            .contains("\"views\":[]")
                            .contains("\"count\":0")
                            .contains("No topic views found");
                })
                .verifyComplete();

        verify(topics).listTopicViews();
    }

    @Test
    void testListTopicViews_singleView() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView1.getName()).thenReturn("my-view");
        when(topicView1.getSpecification()).thenReturn("map ?sensors// to dashboard/<path(1)>");
        when(topicView1.getRoles()).thenReturn(Set.of("VIEWER"));
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(List.of(topicView1)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"count\":1")
                            .contains("\"name\":\"my-view\"")
                            .contains("\"specification\":\"map ?sensors// to dashboard/<path(1)>\"")
                            .contains("VIEWER");
                })
                .verifyComplete();
    }

    @Test
    void testListTopicViews_multipleViews() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);

        when(topicView1.getName()).thenReturn("view-1");
        when(topicView1.getSpecification()).thenReturn("map ?sensors// to s/<path(1)>");
        when(topicView1.getRoles()).thenReturn(Set.of("ADMIN"));

        when(topicView2.getName()).thenReturn("view-2");
        when(topicView2.getSpecification()).thenReturn("map ?data// to d/<path(1)>");
        when(topicView2.getRoles()).thenReturn(Set.of("USER", "VIEWER"));

        when(topicView3.getName()).thenReturn("view-3");
        when(topicView3.getSpecification()).thenReturn("map ?test// to t/<path(1)>");
        when(topicView3.getRoles()).thenReturn(Set.of());

        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(topicView1, topicView2, topicView3)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"count\":3")
                            .contains("\"name\":\"view-1\"")
                            .contains("\"name\":\"view-2\"")
                            .contains("\"name\":\"view-3\"")
                            .contains("ADMIN")
                            .contains("USER")
                            .contains("VIEWER");
                })
                .verifyComplete();
    }

    @Test
    void testListTopicViews_withEmptyRoles() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView1.getName()).thenReturn("no-roles-view");
        when(topicView1.getSpecification()).thenReturn("map ?data// to output/<path(1)>");
        when(topicView1.getRoles()).thenReturn(Set.of());
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(List.of(topicView1)));

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
    void testListTopicViews_complexSpecifications() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView1.getName()).thenReturn("complex-view");
        when(topicView1.getSpecification())
                .thenReturn("map ?data/sensors/ to processed/<path(1)>/<scalar(double:temperature)>");
        when(topicView1.getRoles()).thenReturn(Set.of("OPERATOR"));
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(List.of(topicView1)));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("processed/<path(1)>/<scalar(double:temperature)>");
                })
                .verifyComplete();
    }

    @Test
    void testListTopicViews_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

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
    void testListTopicViews_permissionDenied() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.listTopicViews())
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
                            .contains(ListTopicViewsTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testListTopicViews_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);

        CompletableFuture<List<TopicView>> neverCompletingFuture = new CompletableFuture<>();
        when(topics.listTopicViews())
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
                            .contains(ListTopicViewsTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testListTopicViews_multipleRolesPerView() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView1.getName()).thenReturn("multi-role-view");
        when(topicView1.getSpecification()).thenReturn("map ?secure// to secure/<path(1)>");
        when(topicView1.getRoles()).thenReturn(Set.of("ADMIN", "OPERATOR", "VIEWER", "AUDITOR"));
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(List.of(topicView1)));

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
    void testListTopicViews_viewsWithSpecialCharacters() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topicView1.getName()).thenReturn("special-view-123");
        when(topicView1.getSpecification()).thenReturn("map ?test// to result/<path(1)>");
        when(topicView1.getRoles()).thenReturn(Set.of());
        when(topics.listTopicViews())
                .thenReturn(CompletableFuture.completedFuture(List.of(topicView1)));

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
}
