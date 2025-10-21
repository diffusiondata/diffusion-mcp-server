/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

/**
 * Helper for building responses.
 * <p>
 * This simply wraps a StringBuilder and adds String formatting capability.
 *
 * @author DiffusionData Limited
 */
public final class ToolResponse {

    final StringBuilder theResponse = new StringBuilder();

    /**
     * Add a line to the result.
     */
    public ToolResponse addLine(String message, Object... params) {
        theResponse.append(String.format(message + "%n", params));
        return this;
    }

    /**
     * Add an empty line to the result.
     */
    public ToolResponse addLine() {
        theResponse.append(String.format("%n"));
        return this;
    }

    @Override
    public String toString() {
        return theResponse.toString();
    }
}
