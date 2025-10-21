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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.TopicCreationResult;
import com.pushtechnology.diffusion.client.features.TopicUpdate;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl;
import com.pushtechnology.diffusion.client.features.control.topics.TopicControl.AddTopicResult;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionException;
import com.pushtechnology.diffusion.client.topics.details.TopicSpecification;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for AddTopicTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class AddTopicToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private TopicControl topicControl;

    @Mock
    private TopicUpdate topicUpdate;

    @Mock
    private AddTopicResult addTopicResult;

    @Mock
    private TopicCreationResult creationResult;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "add_topic";

    @BeforeEach
    void setUp() {
        toolSpec = AddTopicTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Creates a new topic");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testAddTopic_minimalArguments_JSON() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

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
                            .contains("\"path\":\"test/topic\"")
                            .contains("\"type\":\"JSON\"")
                            .contains("\"created\":true");
                })
                .verifyComplete();

        verify(topicControl).addTopic(eq("test/topic"), any(TopicSpecification.class));
    }

    @Test
    void testAddTopic_withExplicitType_STRING() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/string");
        arguments.put("type", "STRING");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/string"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"type\":\"STRING\"");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_withInitialValue_STRING() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "STRING");
        arguments.put("initialValue", "Hello World");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.addAndSet(
                eq("test/topic"),
                any(TopicSpecification.class),
                eq(String.class),
                eq("Hello World")))
                .thenReturn(CompletableFuture.completedFuture(creationResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("\"path\":\"test/topic\"")
                            .contains("\"type\":\"STRING\"")
                            .contains("\"created\":true");
                })
                .verifyComplete();

        verify(topicUpdate).addAndSet(
                eq("test/topic"),
                any(TopicSpecification.class),
                eq(String.class),
                eq("Hello World"));
    }

    @Test
    void testAddTopic_withInitialValue_JSON() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "JSON");
        arguments.put("initialValue", "{\"name\":\"test\",\"value\":123}");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.addAndSet(
                eq("test/topic"),
                any(TopicSpecification.class),
                any(),
                any()))
                .thenReturn(CompletableFuture.completedFuture(creationResult));

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
                            .contains("\"created\":true");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_withInitialValue_DOUBLE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "DOUBLE");
        arguments.put("initialValue", "123.45");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.addAndSet(
                eq("test/topic"),
                any(TopicSpecification.class),
                eq(Double.class),
                any(Double.class)))
                .thenReturn(CompletableFuture.completedFuture(creationResult));

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
    void testAddTopic_withInitialValue_INT64() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "INT64");
        arguments.put("initialValue", "9876543210");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicUpdate.class)).thenReturn(topicUpdate);
        when(topicUpdate.addAndSet(
                eq("test/topic"),
                any(TopicSpecification.class),
                eq(Long.class),
                any(Long.class)))
                .thenReturn(CompletableFuture.completedFuture(creationResult));

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
    void testAddTopic_withAllProperties() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "STRING");
        arguments.put("compression", "high");
        arguments.put("conflation", "off");
        arguments.put("dontRetainValue", true);
        arguments.put("owner", "admin");
        arguments.put("persistent", false);
        arguments.put("priority", "high");
        arguments.put("publishValuesOnly", true);
        arguments.put("tidyOnUnsubscribe", true);
        arguments.put("validateValues", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(topicControl).addTopic(eq("test/topic"), any(TopicSpecification.class));
    }

    @Test
    void testAddTopic_withRemovalPolicy_simpleTime() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("removal", "time after 10m");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

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
    void testAddTopic_withRemovalPolicy_compoundExpression() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("removal", "when no updates for 2h or time after 3h");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

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
    void testAddTopic_TIME_SERIES_withEventValueType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        arguments.put("timeSeriesEventValueType", "string");
        arguments.put("timeSeriesRetainedRange", "limit 100");
        arguments.put("timeSeriesSubscriptionRange", "last 10");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/timeseries"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

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
    void testAddTopic_TIME_SERIES_missingEventValueType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        // Missing timeSeriesEventValueType

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
                            .contains("Invalid specification")
                            .contains("Event value type must be specified");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_TIME_SERIES_withInitialValue() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/timeseries");
        arguments.put("type", "TIME_SERIES");
        arguments.put("timeSeriesEventValueType", "string");
        arguments.put("initialValue", "test");

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
                            .contains("Error converting initial value")
                            .contains("Initial values are not supported for TIME_SERIES");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_noActiveSession() {
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
    void testAddTopic_invalidTopicType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "INVALID_TYPE");

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
                            .contains("INVALID_TYPE");
                })
                .verifyComplete();
    }

    @ParameterizedTest(name = "testAddTopic_invalidInitialValue type={0}")
    @ValueSource(strings = { "JSON", "INT64", "DOUBLE" })
    void testAddTopic_invalidInitialValue(String type) {

        setupExchange();

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", type);
        arguments.put("initialValue", "{invalid json}"); // same bad value for all

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler().apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Error converting initial value");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);

        CompletableFuture<AddTopicResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Topic already exists"));
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(AddTopicTool.TOOL_NAME)
                            .contains("Topic already exists");
                })
                .verifyComplete();
    }

    @Test
    void testAddTopic_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);

        CompletableFuture<AddTopicResult> neverCompletingFuture = new CompletableFuture<>();
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(neverCompletingFuture);

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
    void testAddTopic_caseInsensitiveType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "test/topic");
        arguments.put("type", "string"); // lowercase

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TopicControl.class)).thenReturn(topicControl);
        when(topicControl.addTopic(eq("test/topic"), any(TopicSpecification.class)))
                .thenReturn(CompletableFuture.completedFuture(addTopicResult));

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
}