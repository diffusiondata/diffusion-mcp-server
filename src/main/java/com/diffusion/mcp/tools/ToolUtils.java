/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;

import com.pushtechnology.diffusion.client.session.SessionException;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import reactor.core.publisher.Mono;

/**
 * General utilities for use within tools.
 *
 * @author DiffusionData Limited
 */
public final class ToolUtils {

    /**
     * Schema for tools that require no input.
     */
    public static final JsonSchema EMPTY_INPUT_SCHEMA =
        new JsonSchema(
            "object",
            Map.of(), // Empty properties map
            null, // No required fields
            false, // additionalProperties: false
            null, // No $defs
            null // No definitions
        );

    /**
     * 10 Second duration used for timeouts.
     */
    public static final Duration TEN_SECONDS = Duration.ofSeconds(10);

    private ToolUtils() {
        // Utility class.
    }

    /**
     * Returns a success tool result as a Mono.
     */
    public static Mono<CallToolResult> monoToolResult(
        String message,
        Object... args) {
        return Mono.just(toolResult(message, args));
    }

    /**
     * Returns a success tool result.
     */
    public static CallToolResult toolResult(String message, Object... args) {
        return callToolResult(message, false, args);
    }

    /**
     * Returns a success tool result from a pre-built {@link ToolResponse}.
     */
    public static CallToolResult toolResult(ToolResponse result) {
        return new CallToolResult(result.toString(), false);
    }

    /**
     * Returns a Mono error for the given operation.
     * <p>
     * Also LOGs the exception as an error, with a stack trace if it is not a
     * Diffusion {@link SessionException}.
     * <p>
     * For tools without parameters the operation can simply be the tool name.
     * <p>
     * For tools with parameters the operation can be built using one of the
     * {@link #toolOperation} methods.
     */
    public static Mono<CallToolResult> monoToolException(
        String operation,
        Throwable ex,
        Logger log) {

        if (isExpectedException(ex)) {
            // Expected user/LLM errors - just log the message
            log.warn("{} failed: {}", operation, ex.getMessage());
        }
        else {
            // Unexpected system errors - log with stack trace
            log.error("{} failed unexpectedly", operation, ex);
        }

        return Mono.just(toolError("Error %s: %s", operation, ex.getMessage()));
    }

    private static boolean isExpectedException(Throwable ex) {
        return ex instanceof SessionException ||
            ex instanceof IllegalArgumentException ||
            ex instanceof NullPointerException ||
            (ex.getCause() instanceof IllegalArgumentException) ||
            (ex.getCause() instanceof NullPointerException);
    }

    /**
     * Returns a Mono error with the given message and argument substitution.
     */
    public static Mono<CallToolResult> monoToolError(
        String message,
        Object... args) {
        return Mono.just(toolError(message, args));
    }

    /**
     * Returns a tool result error with the given message and argument
     * substitution.
     */
    public static CallToolResult toolError(String message, Object... args) {
        return callToolResult(message, true, args);
    }

    /**
     * Returns a Mono tool error indicating that there is no active Diffusion
     * session.
     */
    public static Mono<CallToolResult> noActiveSession() {
        return Mono.just(toolError(
            "Error: No active Diffusion session. Please connect first using %s",
            ConnectTool.TOOL_NAME));
    }

    /**
     * Returns a session exception that represents a timeout of 10 seconds for
     * ease of handling in tools.
     */
    public static SessionException timex() {
        return timex(10);
    }

    /**
     * Returns a session exception that represents a timeout for ease of
     * handling in tools.
     */
    public static SessionException timex(int seconds) {
        return new SessionException(
            "Operation timed out after " + seconds + " seconds");
    }

    /**
     * Checks if a specified argument is true.
     * <p>
     * If the map does not contain the argument or it is null then false is
     * returned.
     * <p>
     * If the argument value is a boolean its value is returned.
     * <p>
     * If the argument is a String the parsed boolean value is returned.
     * <p>
     * Otherwise false is returned.
     */
    public static boolean argumentIsTrue(
        Map<String, Object> arguments,
        String key) {

        final Object arg = arguments.get(key);
        if (arg == null) {
            return false;
        }
        if (arg instanceof Boolean b) {
            return b;
        }
        if (arg instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    /**
     * Checks if a specified argument is false.
     * <p>
     * If the map does not contain the argument or it is null then true is
     * returned.
     * <p>
     * If the argument value is a boolean its negated value is returned.
     * <p>
     * If the argument is a String the negated parsed boolean value is returned.
     * <p>
     * Otherwise true is returned.
     */
    public static boolean argumentIsFalse(
        Map<String, Object> arguments,
        String key) {

        final Object arg = arguments.get(key);
        if (arg == null) {
            return false;
        }
        if (arg instanceof Boolean b) {
            return !b;
        }
        if (arg instanceof String s) {
            return !Boolean.parseBoolean(s);
        }
        return true;
    }

    /**
     * Returns the value of the integer argument with the specified key or null
     * if it is not present.
     */
    public static Integer intArgument(
        Map<String, Object> arguments,
        String key) {

        final Object arg = arguments.get(key);
        if (arg instanceof Integer i) {
            return i;
        }
        return null;
    }

    /**
     * Returns the value of the integer argument with the specified key or the
     * specified default value if it is not present.
     */
    public static int intArgument(
        Map<String, Object> arguments,
        String key,
        int defaultValue) {

        final Object arg = arguments.get(key);
        if (arg instanceof Integer i) {
            return i;
        }
        return defaultValue;
    }

    /**
     * Returns the value of the string argument with the specified key or null
     * if it is not present.
     */
    public static String stringArgument(
        Map<String, Object> arguments,
        String key) {
        String arg = (String) arguments.get(key);
        return arg == null ? null : arg.trim();
    }

    /**
     * Returns the value of the string argument with the specified key or the
     * specified default if it is not present.
     */
    public static String stringArgument(
        Map<String, Object> arguments,
        String key,
        String defaultValue) {
        return ((String) arguments.getOrDefault(key, defaultValue)).trim();
    }

    /**
     * Formats a tool operation from a tool name and a single argument.
     */
    public static String toolOperation(String toolName, String argument) {
        return toolName + " : " + argument;
    }

    /**
     * Formats a tool operation from a tool name and two arguments.
     */
    public static String toolOperation(
        String toolName,
        String arg1,
        String arg2) {
        return toolName + " : " + arg1 + " , " + arg2;
    }

    /**
     * Creates a call tool result that is either normal result or an error using
     * the specified text with optional parameter substitution.
     */
    private static CallToolResult callToolResult(
        String message,
        boolean error,
        Object... args) {

        if (args.length == 0) {
            return new CallToolResult(message, error);
        }
        else {
            return new CallToolResult(String.format(message, args), error);
        }
    }

}
