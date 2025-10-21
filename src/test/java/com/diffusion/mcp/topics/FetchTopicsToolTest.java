/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static com.pushtechnology.diffusion.client.Diffusion.topicSelectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
import com.pushtechnology.diffusion.client.session.SessionException;
import com.pushtechnology.diffusion.client.topics.details.TopicType;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for FetchTopicsTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class FetchTopicsToolTest {

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
    private FetchResult<Void> fetchResult;

    @Mock
    private FetchResult<Object> fetchResultWithValues;

    @Mock
    private TopicResult<Void> topicResult;

    @Mock
    private TopicResult<Object> topicResultWithValue;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "fetch_topics";

    @BeforeEach
    void setUp() {
        toolSpec = FetchTopicsTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Fetches topics from the connected Diffusion server");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testFetchTopics_minimalArguments_withoutValues() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

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
                            .contains("Diffusion Topics Fetch Results")
                            .contains("Selector: ?.*//")
                            .contains("Requested: 5000 topics")
                            .contains("Values included: false")
                            .contains("Found: 2 topics");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_withCustomSelector() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "?sensors//");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?sensors//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Selector: ?sensors//");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_withValues() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("values", true);
        arguments.put("number", 10);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Object>> results = createMockTopicResultsWithValues();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(10)).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResultWithValues));
        when(fetchResultWithValues.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Requested: 10 topics")
                            .contains("Values included: true")
                            .contains("Value:");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_withAfter() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("after", "test/topic1");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.after("test/topic1")).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("After: test/topic1");
                })
                .verifyComplete();

        verify(fetchRequest).after("test/topic1");
    }

    @Test
    void testFetchTopics_withDepth() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("depth", 2);
        arguments.put("values", true); // Should be ignored when depth is set

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.limitDeepBranches(2, 1)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Max depth: 2")
                            .contains("Values included: false"); // Values ignored with depth
                })
                .verifyComplete();

        verify(fetchRequest).limitDeepBranches(2, 1);
    }

    @Test
    void testFetchTopics_emptyResults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> emptyResults = new ArrayList<>();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(emptyResults);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Found: 0 topics")
                            .contains("No topics found matching the criteria");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_fullBatch_paginationInfo() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("number", 2); // Same as result size for full batch

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(2)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Retrieved full batch of 2 topics")
                            .contains("To get the next batch");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_noActiveSession() {
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
    void testFetchTopics_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);

        CompletableFuture<FetchResult<Void>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Invalid selector"));
        when(fetchRequest.fetch(topicSelectors().parse("?.*//"))).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(FetchTopicsTool.TOOL_NAME)
                            .contains("Invalid selector");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);

        CompletableFuture<FetchResult<Void>> neverCompletingFuture = new CompletableFuture<>();
        when(fetchRequest.fetch(topicSelectors().parse("?.*//"))).thenReturn(neverCompletingFuture);

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
    void testFetchTopics_withValues_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("values", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(100)).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);

        CompletableFuture<FetchResult<Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new PermissionsException("Permission denied"));
        when(fetchRequestWithValues.fetch(topicSelectors().parse("?.*//"))).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(FetchTopicsTool.TOOL_NAME)
                            .contains("Permission denied");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_customNumberWithValues() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("values", true);
        arguments.put("number", 50);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Object>> results = createMockTopicResultsWithValues();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(50)).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResultWithValues));
        when(fetchResultWithValues.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Requested: 50 topics");
                })
                .verifyComplete();

        verify(fetchRequest).first(50);
    }

    @Test
    void testFetchTopics_partialBatch_paginationInfo() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("number", 10); // More than result size for partial batch

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults(); // Only 2 results

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(10)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Retrieved 2 topics (less than requested 10)")
                            .contains("likely reached end");
                })
                .verifyComplete();
    }

    @Test
    void testFetchTopics_withSizes() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sizes", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResultsWithSizes();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.withSizes()).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Sizes included: true")
                            .contains("Value size:");
                })
                .verifyComplete();

        verify(fetchRequest).withSizes();
    }

    @Test
    void testFetchTopics_withSizes_timeSeriesTopic() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sizes", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTimeSeriesTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.withSizes()).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Sizes included: true")
                            .contains("Last event size: 128 bytes")
                            .contains("Event count: 10")
                            .contains("Total size: 1280 bytes");
                })
                .verifyComplete();

        verify(fetchRequest).withSizes();
    }

    @Test
    void testFetchTopics_withSizesAndValues() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sizes", true);
        arguments.put("values", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Object>> results = createMockTopicResultsWithValuesAndSizes();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(100)).thenReturn(fetchRequest);
        when(fetchRequest.withSizes()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResultWithValues));
        when(fetchResultWithValues.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Sizes included: true")
                            .contains("Values included: true")
                            .contains("Value:")
                            .contains("Value size:");
                })
                .verifyComplete();

        verify(fetchRequest).withSizes();
    }

    @Test
    void testFetchTopics_withUnpublishedDelayedTopics() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("unpublishedDelayedTopics", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.withUnpublishedDelayedTopics()).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Unpublished delayed topics included: true");
                })
                .verifyComplete();

        verify(fetchRequest).withUnpublishedDelayedTopics();
    }

    @Test
    void testFetchTopics_withAllOptions() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicSelector", "?test//");
        arguments.put("number", 50);
        arguments.put("values", true);
        arguments.put("after", "test/topic1");
        arguments.put("sizes", true);
        arguments.put("unpublishedDelayedTopics", true);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Object>> results = createMockTopicResultsWithValuesAndSizes();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(50)).thenReturn(fetchRequest);
        when(fetchRequest.after("test/topic1")).thenReturn(fetchRequest);
        when(fetchRequest.withSizes()).thenReturn(fetchRequest);
        when(fetchRequest.withUnpublishedDelayedTopics()).thenReturn(fetchRequest);
        when(fetchRequest.withValues(Object.class)).thenReturn(fetchRequestWithValues);
        when(fetchRequestWithValues.fetch(topicSelectors().parse("?test//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResultWithValues));
        when(fetchResultWithValues.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Selector: ?test//")
                            .contains("After: test/topic1")
                            .contains("Requested: 50 topics")
                            .contains("Values included: true")
                            .contains("Sizes included: true")
                            .contains("Unpublished delayed topics included: true");
                })
                .verifyComplete();

        verify(fetchRequest).after("test/topic1");
        verify(fetchRequest).withSizes();
        verify(fetchRequest).withUnpublishedDelayedTopics();
    }

    @Test
    void testFetchTopics_sizesDefaultToFalse() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Sizes included: false")
                            .doesNotContain("Value size:");
                })
                .verifyComplete();

        verify(fetchRequest, org.mockito.Mockito.never()).withSizes();
    }

    @Test
    void testFetchTopics_unpublishedDelayedTopicsDefaultToFalse() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        List<TopicResult<Void>> results = createMockTopicResults();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(Topics.class)).thenReturn(topics);
        when(topics.fetchRequest()).thenReturn(fetchRequest);
        when(fetchRequest.first(5000)).thenReturn(fetchRequest);
        when(fetchRequest.fetch(topicSelectors().parse("?.*//")))
                .thenReturn(CompletableFuture.completedFuture(fetchResult));
        when(fetchResult.results()).thenReturn(results);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Unpublished delayed topics included: false");
                })
                .verifyComplete();

        verify(fetchRequest, org.mockito.Mockito.never()).withUnpublishedDelayedTopics();
    }

    // Helper methods
    private List<TopicResult<Void>> createMockTopicResults() {
        List<TopicResult<Void>> results = new ArrayList<>();

        TopicResult<Void> result1 = createMockTopicResult("test/topic1", TopicType.STRING);
        TopicResult<Void> result2 = createMockTopicResult("test/topic2", TopicType.JSON);

        results.add(result1);
        results.add(result2);

        return results;
    }

    private TopicResult<Void> createMockTopicResult(String path, TopicType type) {
        TopicResult<Void> result = org.mockito.Mockito.mock(TopicResult.class);
        when(result.path()).thenReturn(path);
        when(result.type()).thenReturn(type);
        return result;
    }

    private List<TopicResult<Void>> createMockTopicResultsWithSizes() {
        List<TopicResult<Void>> results = new ArrayList<>();

        TopicResult<Void> result1 = createMockTopicResultWithSize("test/topic1", TopicType.STRING, 256);
        TopicResult<Void> result2 = createMockTopicResultWithSize("test/topic2", TopicType.JSON, 512);

        results.add(result1);
        results.add(result2);

        return results;
    }

    private TopicResult<Void> createMockTopicResultWithSize(String path, TopicType type, int valueSize) {
        TopicResult<Void> result = org.mockito.Mockito.mock(TopicResult.class);
        when(result.path()).thenReturn(path);
        when(result.type()).thenReturn(type);
        when(result.valueSize()).thenReturn(valueSize);
        return result;
    }

    private List<TopicResult<Void>> createMockTimeSeriesTopicResults() {
        List<TopicResult<Void>> results = new ArrayList<>();

        TopicResult<Void> result = org.mockito.Mockito.mock(TopicResult.class);
        when(result.path()).thenReturn("test/timeseries");
        when(result.type()).thenReturn(TopicType.TIME_SERIES);
        when(result.valueSize()).thenReturn(128);
        when(result.valueCount()).thenReturn(10);
        when(result.valueTotalSize()).thenReturn(1280L);

        results.add(result);

        return results;
    }

    private List<TopicResult<Object>> createMockTopicResultsWithValues() {
        List<TopicResult<Object>> results = new ArrayList<>();

        TopicResult<Object> result1 = createMockTopicResultWithValue("test/topic1", TopicType.STRING, "value1");
        TopicResult<Object> result2 = createMockTopicResultWithValue("test/topic2", TopicType.INT64, 42L);

        results.add(result1);
        results.add(result2);

        return results;
    }

    private TopicResult<Object> createMockTopicResultWithValue(String path, TopicType type, Object value) {
        TopicResult<Object> result = org.mockito.Mockito.mock(TopicResult.class);
        when(result.path()).thenReturn(path);
        when(result.type()).thenReturn(type);
        when(result.value()).thenReturn(value);
        return result;
    }

    private List<TopicResult<Object>> createMockTopicResultsWithValuesAndSizes() {
        List<TopicResult<Object>> results = new ArrayList<>();

        TopicResult<Object> result1 = createMockTopicResultWithValueAndSize(
                "test/topic1", TopicType.STRING, "value1", 256);
        TopicResult<Object> result2 = createMockTopicResultWithValueAndSize(
                "test/topic2", TopicType.INT64, 42L, 8);

        results.add(result1);
        results.add(result2);

        return results;
    }

    private TopicResult<Object> createMockTopicResultWithValueAndSize(
            String path, TopicType type, Object value, int valueSize) {
        TopicResult<Object> result = org.mockito.Mockito.mock(TopicResult.class);
        when(result.path()).thenReturn(path);
        when(result.type()).thenReturn(type);
        when(result.value()).thenReturn(value);
        when(result.valueSize()).thenReturn(valueSize);
        return result;
    }
}