/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ServerConfig}.
 * Verifies transport parsing and parameter defaulting without mocking System.
 *
 * @author DiffusionData Limited
 */
final class ServerConfigTest {

    @BeforeEach
    void clearTransportProp() {
        System.clearProperty("mcp.transport");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("mcp.transport");
    }

    @Test
    @DisplayName("Class defines expected Transport enum and is abstract")
    void testTypeBasics() {
        assertThat(Modifier.isAbstract(ServerConfig.class.getModifiers())).isTrue();
        assertThat(ServerConfig.Transport.values())
            .extracting(Enum::name)
            .containsExactlyInAnyOrder("STDIO", "HTTPS");
    }

    @Test
    @DisplayName("load(): default (no property) -> StdioConfig")
    void testLoadDefaultsToStdio() {
        ServerConfig cfg = ServerConfig.load();
        assertThat(cfg).isInstanceOf(StdioConfig.class);
        assertThat(cfg.transport()).isEqualTo(ServerConfig.Transport.STDIO);
    }

    @Test
    @DisplayName("load(): mcp.transport=stdio -> StdioConfig")
    void testLoadExplicitStdio() {
        System.setProperty("mcp.transport", "stdio");
        ServerConfig cfg = ServerConfig.load();
        assertThat(cfg).isInstanceOf(StdioConfig.class);
        assertThat(cfg.transport()).isEqualTo(ServerConfig.Transport.STDIO);
    }

    @Test
    @DisplayName("load(): mcp.transport=https -> HttpConfig (requires keystore props)")
    void testLoadExplicitHttps() {
        // arrange
        System.setProperty("mcp.transport", "https");

        // minimal SSL config required by HttpConfig
        System.setProperty("javax.net.ssl.keyStore", "/tmp/test-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

        try {
            // act
            ServerConfig cfg = ServerConfig.load();

            // assert
            assertThat(cfg).isInstanceOf(HttpConfig.class);
            assertThat(cfg.transport()).isEqualTo(ServerConfig.Transport.HTTPS);
        }
        finally {
            // cleanup to avoid test cross-talk
            System.clearProperty("mcp.transport");
            System.clearProperty("javax.net.ssl.keyStore");
            System.clearProperty("javax.net.ssl.keyStorePassword");
            System.clearProperty("javax.net.ssl.keyManagerPassword");
        }
    }


    @Test
    @DisplayName("load(): invalid mcp.transport -> ConfigurationException with helpful message")
    void testLoadInvalidTransport() {
        System.setProperty("mcp.transport", "banana");
        assertThatThrownBy(ServerConfig::load)
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Unknown mcp.transport: banana (use stdio|https)");
    }

    @Test
    @DisplayName("getParameter: returns default when no property/env is set")
    void testGetParameterDefaulting() {
        // Use a random/unique key to avoid colliding with any real property
        String key = "unit.test." + UUID.randomUUID();
        System.clearProperty(key); // ensure absent
        String value = ServerConfig.getParameter(key, "DEF");
        assertThat(value).isEqualTo("DEF");
    }

    @Test
    @DisplayName("getParameter: system property takes precedence over default")
    void testGetParameterPropertyOverridesDefault() {
        String key = "unit.test." + UUID.randomUUID();
        System.setProperty(key, "VALUE");
        try {
            String value = ServerConfig.getParameter(key, "DEF");
            assertThat(value).isEqualTo("VALUE");
        }
        finally {
            System.clearProperty(key);
        }
    }
}
