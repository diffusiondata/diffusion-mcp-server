/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

/**
 * Exception thrown when server configuration is invalid or incomplete.
 *
 * @author DiffusionData Limited
 */
public final class ConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new configuration exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new configuration exception with the specified detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
