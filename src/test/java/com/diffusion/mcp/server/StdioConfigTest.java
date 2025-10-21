/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StdioConfig}.
 *
 * @author DiffusionData Limited
 */
final class StdioConfigTest {

    @Test
    @DisplayName("Transport is STDIO")
    void testTransport() {
        StdioConfig cfg = new StdioConfig();
        assertThat(cfg.transport()).isEqualTo(ServerConfig.Transport.STDIO);
    }
}

