/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.diffusion.mcp.tools.SessionManager;
import com.pushtechnology.diffusion.client.features.TimeSeries;
import com.pushtechnology.diffusion.client.features.TimeSeries.Event;
import com.pushtechnology.diffusion.client.features.TimeSeries.QueryResult;
import com.pushtechnology.diffusion.client.features.TimeSeries.RangeQuery;
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
 * Unit tests for TimeSeriesValueRangeQueryTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class TimeSeriesValueRangeQueryToolTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Session session;

    @Mock
    private TimeSeries timeSeries;

    @SuppressWarnings("rawtypes")
    @Mock
    private RangeQuery rangeQuery;

    @SuppressWarnings("rawtypes")
    @Mock
    private QueryResult queryResult;

    @SuppressWarnings("rawtypes")
    @Mock
    private Event event;

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String SESSION_ID = "test-session-id";
    private static final String TOOL_NAME = "time_series_value_range_query";

    @BeforeEach
    void setUp() {
        toolSpec = TimeSeriesValueRangeQueryTool.create(sessionManager);
    }

    private void setupExchange() {
        when(exchange.sessionId()).thenReturn(SESSION_ID);
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Queries a time series topic");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testTimeSeriesQuery_minimalArguments_STRING() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

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
                            .contains("Time Series Query Results")
                            .contains("Topic: timeseries/test")
                            .contains("Event Value Type: STRING")
                            .contains("Selected Events: 2");
                })
                .verifyComplete();
    }

    @Test
    void testTimeSeriesQuery_withFromSequence() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("fromSequence", 10);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.from(10L)).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).from(10L);
    }

    @Test
    void testTimeSeriesQuery_withFromTimestamp() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("fromTimestamp", "2024-01-01T00:00:00Z");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.from(any(Instant.class))).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).from(any(Instant.class));
    }

    @Test
    void testTimeSeriesQuery_withFromLast() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("fromLast", 5);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromLast(5L)).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).fromLast(5L);
    }

    @Test
    void testTimeSeriesQuery_withToSequence() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("toSequence", 100);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.to(100L)).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).to(100L);
    }

    @Test
    void testTimeSeriesQuery_withToTimestamp() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("toTimestamp", "2024-12-31T23:59:59Z");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.to(any(Instant.class))).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).to(any(Instant.class));
    }

    @Test
    void testTimeSeriesQuery_withNext() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("next", 10);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.next(10L)).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).next(10L);
    }

    @Test
    void testTimeSeriesQuery_withMaxResults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("maxResults", 50);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(50)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                })
                .verifyComplete();

        verify(rangeQuery).limit(50);
    }

    @Test
    void testTimeSeriesQuery_INT64() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "INT64");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Event Value Type: INT64");
                })
                .verifyComplete();
    }

    @Test
    void testTimeSeriesQuery_DOUBLE() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "DOUBLE");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        setupMocksForQuery("timeseries/test");
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).contains("Event Value Type: DOUBLE");
                })
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTimeSeriesQuery_emptyResults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/empty");
        arguments.put("eventValueType", "STRING");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TimeSeries.class)).thenReturn(timeSeries);
        when(timeSeries.rangeQuery()).thenReturn(rangeQuery);
        when(rangeQuery.forValues()).thenReturn(rangeQuery);
        when(rangeQuery.as(any())).thenReturn(rangeQuery);
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);
        when(rangeQuery.selectFrom("timeseries/empty"))
                .thenReturn(CompletableFuture.completedFuture(queryResult));
        when(queryResult.selectedCount()).thenReturn(0L);
        when(queryResult.isComplete()).thenReturn(true);
        when(queryResult.stream()).thenReturn(Stream.empty());

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Selected Events: 0")
                            .contains("No events found in the specified range");
                })
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTimeSeriesQuery_incompleteResults() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");
        arguments.put("maxResults", 1);

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TimeSeries.class)).thenReturn(timeSeries);
        when(timeSeries.rangeQuery()).thenReturn(rangeQuery);
        when(rangeQuery.forValues()).thenReturn(rangeQuery);
        when(rangeQuery.as(any())).thenReturn(rangeQuery);
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(1)).thenReturn(rangeQuery);
        when(rangeQuery.selectFrom("timeseries/test"))
                .thenReturn(CompletableFuture.completedFuture(queryResult));
        when(queryResult.selectedCount()).thenReturn(10L); // More than returned
        when(queryResult.isComplete()).thenReturn(false);
        when(queryResult.stream()).thenReturn(Stream.of(event));

        when(event.sequence()).thenReturn(1L);
        when(event.timestamp()).thenReturn(System.currentTimeMillis());
        when(event.author()).thenReturn("user1");
        when(event.value()).thenReturn("value1");
        when(event.isEditEvent()).thenReturn(false);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Complete: No")
                            .contains("... and 9 more events not shown")
                            .contains("Increase maxResults parameter");
                })
                .verifyComplete();
    }

    @Test
    void testTimeSeriesQuery_noActiveSession() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");

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
    void testTimeSeriesQuery_invalidEventValueType() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "INVALID");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        // Exception thrown when building query, so no other mocks needed

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains("Invalid query parameters")
                            .contains("Unsupported event value type");
                })
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTimeSeriesQuery_diffusionError() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TimeSeries.class)).thenReturn(timeSeries);
        when(timeSeries.rangeQuery()).thenReturn(rangeQuery);
        when(rangeQuery.forValues()).thenReturn(rangeQuery);
        when(rangeQuery.as(any())).thenReturn(rangeQuery);
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        CompletableFuture<QueryResult<Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new SessionException("Topic not found"));
        when(rangeQuery.selectFrom("timeseries/test")).thenReturn(failedFuture);

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isTrue();
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text())
                            .contains(TimeSeriesValueRangeQueryTool.TOOL_NAME)
                            .contains("Topic not found");
                })
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTimeSeriesQuery_timeout() {
        // Arrange
        setupExchange();
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("topicPath", "timeseries/test");
        arguments.put("eventValueType", "STRING");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TimeSeries.class)).thenReturn(timeSeries);
        when(timeSeries.rangeQuery()).thenReturn(rangeQuery);
        when(rangeQuery.forValues()).thenReturn(rangeQuery);
        when(rangeQuery.as(any())).thenReturn(rangeQuery);
        when(rangeQuery.fromStart()).thenReturn(rangeQuery);
        when(rangeQuery.limit(100)).thenReturn(rangeQuery);

        CompletableFuture<QueryResult<Object>> neverCompletingFuture = new CompletableFuture<>();
        when(rangeQuery.selectFrom("timeseries/test")).thenReturn(neverCompletingFuture);

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

    // Helper methods
    @SuppressWarnings("unchecked")
    private void setupMocksForQuery(String topicPath) {
        when(sessionManager.get(SESSION_ID)).thenReturn(session);
        when(session.feature(TimeSeries.class)).thenReturn(timeSeries);
        when(timeSeries.rangeQuery()).thenReturn(rangeQuery);
        when(rangeQuery.forValues()).thenReturn(rangeQuery);
        when(rangeQuery.as(any())).thenReturn(rangeQuery);
        // Don't stub fromStart() or limit() here - let tests override as needed
        when(rangeQuery.selectFrom(topicPath))
                .thenReturn(CompletableFuture.completedFuture(queryResult));

        when(queryResult.selectedCount()).thenReturn(2L);
        when(queryResult.isComplete()).thenReturn(true);
        when(queryResult.stream()).thenReturn(Stream.of(event, event));

        when(event.sequence()).thenReturn(1L);
        when(event.timestamp()).thenReturn(System.currentTimeMillis());
        when(event.author()).thenReturn("user1");
        when(event.value()).thenReturn("test value");
        when(event.isEditEvent()).thenReturn(false);
    }
}
