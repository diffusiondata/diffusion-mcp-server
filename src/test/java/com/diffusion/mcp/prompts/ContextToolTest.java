/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for ContextTool.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
class ContextToolTest {

    @Mock
    private McpAsyncServerExchange exchange;

    private AsyncToolSpecification toolSpec;

    private static final String TOOL_NAME = "get_context";

    @BeforeEach
    void setUp() {
        toolSpec = ContextTool.create();
    }

    @Test
    void testToolSpecification() {
        assertThat(toolSpec).isNotNull();
        assertThat(toolSpec.tool().name()).isEqualTo(TOOL_NAME);
        assertThat(toolSpec.tool().description())
                .contains("Get Diffusion MCP server context");
        assertThat(toolSpec.tool().inputSchema()).isNotNull();
    }

    @Test
    void testGetContext_introduction() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "introduction");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_topics() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "topics");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_topicsAdvanced() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "topics_advanced");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_topicSelectors() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "topic_selectors");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_sessions() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "sessions");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_metrics() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "metrics");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_topicViews() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "topic_views");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_topicViewsAdvanced() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "topic_views_advanced");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_remoteServers() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "remote_servers");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_security() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "security");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_sessionTrees() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "session_trees");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_defaultsToIntroduction() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        // No type specified

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void testGetContext_invalidTypeFallsBackToIntroduction() {
        // Arrange
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("type", "invalid_type");

        CallToolRequest request = CallToolRequest.builder()
                .name(TOOL_NAME)
                .arguments(arguments)
                .build();

        // Act
        Mono<CallToolResult> result = toolSpec.callHandler()
                .apply(exchange, request);

        // Assert
        StepVerifier.create(result)
                .assertNext(callResult -> {
                    assertThat(callResult.isError()).isFalse();
                    assertThat(callResult.content()).hasSize(1);
                    TextContent content = (TextContent) callResult.content().get(0);
                    assertThat(content.text()).isNotEmpty();
                })
                .verifyComplete();
    }
}
