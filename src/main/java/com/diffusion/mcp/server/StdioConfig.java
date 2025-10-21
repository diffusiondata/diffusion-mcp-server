/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

/**
 * Diffusion MCP Server Configuration for STDIO Transport.
 *
 * @author DiffusionData Limited
 */
final class StdioConfig extends ServerConfig {

    StdioConfig() {
        super(Transport.STDIO);
    }
}
