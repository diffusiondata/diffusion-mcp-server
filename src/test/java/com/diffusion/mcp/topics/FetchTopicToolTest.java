/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
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
import com.pushtechnology.diffusion.client.features.Topics;
import com.pushtechnology.diffusion.client.features.Topics.FetchRequest;
import com.pushtechnology.diffusion.client.features.Topics.FetchResult;
import com.pushtechnology.diffusion.client.features.Topics.FetchResult.TopicResult;
import com.pushtechnology.diffusion.client.session.PermissionsException;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;
import com.pushtechnology.diffusion.client.topics.details.TopicType;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for FetchTopicTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class FetchTopicToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private Topics topics;

    @Mock
    private FetchRequest<Void> fetchRequest;

    @Mock
    private FetchRequest<Object> fetchRequestWithValues;

    @Mock
    private FetchResult<Object> fetchResult;

    @Mock
    private TopicResult<Object> topicResult;

    @Mock
    private TopicSpecification specification;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "fetch_topic";

    @BeforeEach
    void setUp() {
        toolSpec = FetchTopicTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Fetches the string value");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testFetchTopic_STRING() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/string");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("test/string", "Hello World", TopicType.STRING);

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
                            .contains("\"value\":\"Hello World\"")
                            .contains("\"type\":\"STRING\"")
                            .contains("\"properties\":");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_JSON() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/json");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Mock JSON value
        com.pushtechnology.diffusion.datatype.json.JSON jsonValue =
                org.mockito.Mockito.mock(com.pushtechnology.diffusion.datatype.json.JSON.class);
        when(jsonValue.toJsonString()).thenReturn("{\"key\":\"value\"}");

        setupMocksForSuccessfulFetch("test/json", jsonValue, TopicType.JSON);

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
                            .contains("{\\\"key\\\":\\\"value\\\"}");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_DOUBLE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/double");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("test/double", 123.45, TopicType.DOUBLE);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"type\":\"DOUBLE\"")
                            .contains("\"value\":\"123.45\"");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_INT64() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/int64");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("test/int64", 9876543210L, TopicType.INT64);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"type\":\"INT64\"")
                            .contains("\"value\":\"9876543210\"");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_BINARY() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/binary");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        byte[] binaryData = new byte[]{1, 2, 3, 4};
        setupMocksForSuccessfulFetch("test/binary", binaryData, TopicType.BINARY);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"type\":\"BINARY\"");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_withProperties() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        Map<String, String> properties = new HashMap<>();
        properties.put("COMPRESSION", "high");
        properties.put("PRIORITY", "default");

        setupMocksForSuccessfulFetch("test/topic", "value", TopicType.STRING, properties);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"properties\":")
                            .contains("COMPRESSION")
                            .contains("high");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_nullValue() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/null");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("test/null", null, TopicType.STRING);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"value\":\"<null>\"");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_topicNotFound() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "nonexistent/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.withProperties()).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch("nonexistent/topic"))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(new ArrayList<>());

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Topic 'nonexistent/topic' not found");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_multipleResults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "?ambiguous//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.withProperties()).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch("?ambiguous//"))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));

        // Create multiple results
        List<TopicResult<Object>> multipleResults = new ArrayList<>();
        multipleResults.add(topicResult);
        multipleResults.add(topicResult);
        when(fetchResult.results()).thenReturn(multipleResults);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("resolved to more than one value");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

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
    void testFetchTopic_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.withProperties()).thenReturn(fetchRequestWithValues);

        CompletableFuture<FetchResult<Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new PermissionsException("Permission denied"));
        when(fetchRequestWithValues.fetch("test/topic")).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(FetchTopicTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopic_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.withProperties()).thenReturn(fetchRequestWithValues);

        CompletableFuture<FetchResult<Object>> neverCompletingFuture = new CompletableFuture<>();
        when(fetchRequestWithValues.fetch("test/topic")).thenReturn(neverCompletingFuture);

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
    void testFetchTopic_withNestedPath() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "sensors/temperature/room1");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("sensors/temperature/room1", "22.5", TopicType.STRING);

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

    @Test
    void testFetchTopic_emptyProperties() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForSuccessfulFetch("test/topic", "value", TopicType.STRING, new HashMap<>());

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"properties\":{}");
                })
                .verifyComplete();
    }

    // Helper methods
    private void setupMocksForSuccessfulFetch(String path, Object value, TopicType type) {
        setupMocksForSuccessfulFetch(path, value, type, new HashMap<>());
    }

    private void setupMocksForSuccessfulFetch(
            String path,
            Object value,
            TopicType type,
            Map<String, String> properties) {

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.withProperties()).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch(path))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));

        List<TopicResult<Object>> results = new ArrayList<>();
        results.add(topicResult);
        when(fetchResult.results()).thenReturn(results);

        when(topicResult.value()).thenReturn(value);
        when(topicResult.specification()).thenReturn(specification);
        when(specification.getType()).thenReturn(type);
        when(specification.getProperties()).thenReturn(properties);
    }
}
