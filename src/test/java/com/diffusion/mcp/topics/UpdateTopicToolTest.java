/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pushtechnology.diffusion.client.features.TopicUpdate;
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
 * Unit tests for UpdateTopicTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class UpdateTopicToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private TopicUpdate topicUpdate;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "update_topic";

    @BeforeEach
    void setUp() {
        toolSpec = UpdateTopicTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Updates an existing topic");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testUpdateTopic_STRING() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/string");
        arguments.put("type", "STRING");
        arguments.put("value", "Hello World");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set("test/string", String.class, "Hello World"))
                .thenReturn(CompletableFuture.completedFuture(null));

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
                            .contains("\"path\":\"test/string\"")
                            .contains("\"type\":\"STRING\"")
                            .contains("\"updated\":true");
                })
                .verifyComplete();

        verify(topicUpdate).set("test/string", String.class, "Hello World");
    }

    @Test
    void testUpdateTopic_JSON() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/json");
        arguments.put("type", "JSON");
        arguments.put("value", "{\"key\":\"value\"}");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("test/json"), any(), any()))
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
                            .contains("\"type\":\"JSON\"")
                            .contains("\"updated\":true");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_DOUBLE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/double");
        arguments.put("type", "DOUBLE");
        arguments.put("value", "123.45");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("test/double"), eq(Double.class), any(Double.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("\"type\":\"DOUBLE\"");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_INT64() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/int64");
        arguments.put("type", "INT64");
        arguments.put("value", "9876543210");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("test/int64"), eq(Long.class), any(Long.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("\"type\":\"INT64\"");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_BINARY() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/binary");
        arguments.put("type", "BINARY");
        arguments.put("value", "binary data");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("test/binary"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("\"type\":\"BINARY\"");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_TIME_SERIES_string() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        arguments.put("value", "event data");
        arguments.put("eventType", "string");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set("test/timeseries", String.class, "event data"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("\"type\":\"TIME_SERIES\"");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_TIME_SERIES_json() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        arguments.put("value", "{\"event\":\"data\"}");
        arguments.put("eventType", "json");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("test/timeseries"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_TIME_SERIES_missingEventType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        arguments.put("value", "event data");
        // Missing eventType

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("eventType is required for TIME_SERIES topics");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "STRING");
        arguments.put("value", "test");

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
    void testUpdateTopic_invalidTopicType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "INVALID");
        arguments.put("value", "test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid topic type")
                            .contains("INVALID");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_invalidValue_DOUBLE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/double");
        arguments.put("type", "DOUBLE");
        arguments.put("value", "not-a-number");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error converting value");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_invalidValue_INT64() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/int64");
        arguments.put("type", "INT64");
        arguments.put("value", "not-a-number");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error converting value");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_invalidValue_JSON() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/json");
        arguments.put("type", "JSON");
        arguments.put("value", "{invalid json}");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Error converting value");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "STRING");
        arguments.put("value", "test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);

        when(topicUpdate.set("test/topic", String.class, "test"))
            .thenReturn(CompletableFuture.failedFuture(new SessionException("Topic not found")));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert - Now expects an error result, not a thrown exception
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(UpdateTopicTool.TOOL_NAME)
                            .contains("test/topic")
                            .contains("Topic not found");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "STRING");
        arguments.put("value", "test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);

        CompletableFuture<Void> neverCompletingFuture = new CompletableFuture<>();
        when(topicUpdate.set("test/topic", String.class, "test"))
            .thenAnswer(invocation -> neverCompletingFuture);

        // Act & Assert with virtual time - Now expects an error result
        StepVerifier.withVirtualTime(() -> toolSpec.callHandler()
                        .apply(exchange, request))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(11))
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(UpdateTopicTool.TOOL_NAME)
                            .contains("timed out after 10 seconds");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_caseInsensitiveType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "string"); // lowercase
        arguments.put("value", "test");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set("test/topic", String.class, "test"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("\"type\":\"STRING\"");
                })
                .verifyComplete();
    }

    @Test
    void testUpdateTopic_nestedPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "sensors/temperature/room1");
        arguments.put("type", "DOUBLE");
        arguments.put("value", "22.5");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.set(eq("sensors/temperature/room1"), eq(Double.class), any(Double.class)))
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
                            .contains("\"path\":\"sensors/temperature/room1\"");
                })
                .verifyComplete();
    }
}
