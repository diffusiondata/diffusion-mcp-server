/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.pushtechnology.diffusion.client.Diffusion.topicSelectors;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl.TopicRemovalResult;
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
 * Unit tests for RemoveTopicsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class RemoveTopicsToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private TopicControl topicControl;

    @Mock
    private TopicRemovalResult removalResult;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "remove_topics";

    @BeforeEach
    void setUp() {
        toolSpec = RemoveTopicsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Removes topics matching the specified topic selector");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testRemoveTopics_singleTopic() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("test/topic")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(1);

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
                            .contains("\"topicSelector\":\">test/topic\"")
                            .contains("\"removedCount\":1");
                })
                .verifyComplete();

        verify(topicControl).removeTopics(topicSelectors().parse("test/topic"));
    }

    @Test
    void testRemoveTopics_multipleTopics() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "?test//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("?test//")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(25);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"topicSelector\":\"?test//\"")
                            .contains("\"removedCount\":25");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_noTopicsFound() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "nonexistent/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("nonexistent/topic")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(0);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"removedCount\":0");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_withDescendantQualifier() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "branch//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("branch//")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(10);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"topicSelector\":\">branch//\"")
                            .contains("\"removedCount\":10");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_withWildcardSelector() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "?sensors/.*/temperature");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("?sensors/.*/temperature")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(5);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"removedCount\":5");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_nestedPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "a/b/c/d/e");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("a/b/c/d/e")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(1);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"topicSelector\":\">a/b/c/d/e\"");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "test/topic");

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
    void testRemoveTopics_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);

        CompletableFuture<TopicRemovalResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new PermissionsException("Permission denied"));
        when(topicControl.removeTopics(topicSelectors().parse("test/topic"))).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(RemoveTopicsTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);

        CompletableFuture<TopicRemovalResult> neverCompletingFuture = new CompletableFuture<>();
        when(topicControl.removeTopics(topicSelectors().parse("test/topic"))).thenReturn(neverCompletingFuture);

        // Act & Assert with virtual time
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .expectNextMatches(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    return content.text().contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_largeCount() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "?.*//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(10000);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"removedCount\":10000");
                })
                .verifyComplete();
    }

    @Test
    void testRemoveTopics_multipleCalls() {
        // Arrange
        setupExchange();

        Map<String, Object> arguments1 = new HashMap<>();
        arguments1.put("topicSelector", "test/topic1");

        Map<String, Object> arguments2 = new HashMap<>();
        arguments2.put("topicSelector", "test/topic2");

        CallToolRequest request1 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments1)
                .build();

        CallToolRequest request2 = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments2)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.removeTopics(topicSelectors().parse("test/topic1")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(topicControl.removeTopics(topicSelectors().parse("test/topic2")))
                .thenReturn(CompletableFuture.completedFuture(removalResult));
        when(removalResult.getRemovedCount()).thenReturn(1);

        // Act
        Mono<CallToolResult> result1 = toolSpec.callHandler().apply(exchange, request1);
        Mono<CallToolResult> result2 = toolSpec.callHandler().apply(exchange, request2);

        // Assert
        StepVerifier.create(result1)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("test/topic1");
                })
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("test/topic2");
                })
                .verifyComplete();

        verify(topicControl).removeTopics(topicSelectors().parse("test/topic1"));
        verify(topicControl).removeTopics(topicSelectors().parse("test/topic2"));
    }
}
