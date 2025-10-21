/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Tests for {@link HttpConfig}.
 *
 * @author DiffusionData Limited
 */
final class HttpConfigTest {

    @Test
    @DisplayName("Defaults: host=localhost, port=7443, corsOrigin='*', hsts=null; keystore+password required")
    void testDefaultsHappyPath() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            // Defaults chosen by HttpConfig when not explicitly configured
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("localhost");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("7443");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn("/path/keystore.jks");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn("changeit");
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("*");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn(null);
            // The static getters also read from ServerConfig:
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyManagerPassword", null))
                  .thenReturn(null);

            HttpConfig cfg = new HttpConfig(); // should not throw

            assertThat(cfg.host()).isEqualTo("localhost");
            assertThat(cfg.port()).isEqualTo(7443);
            assertThat(cfg.keyStore()).isEqualTo("/path/keystore.jks");
            assertThat(cfg.corsOrigin()).isEqualTo("*");
            assertThat(cfg.hsts()).isNull();
            assertThat(HttpConfig.keyStorePassword()).isEqualTo("changeit");
            assertThat(HttpConfig.keyManagerPassword()).isNull();
        }
    }

    @Test
    @DisplayName("Custom values are read from ServerConfig parameters")
    void testCustomValues() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("0.0.0.0");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("8443");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn("/secure/ks.jks");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn("s3cr3t");
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("https://app.example");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn("max-age=31536000; includeSubDomains");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyManagerPassword", null))
                  .thenReturn("km-pass");

            HttpConfig cfg = new HttpConfig();

            assertThat(cfg.host()).isEqualTo("0.0.0.0");
            assertThat(cfg.port()).isEqualTo(8443);
            assertThat(cfg.keyStore()).isEqualTo("/secure/ks.jks");
            assertThat(cfg.corsOrigin()).isEqualTo("https://app.example");
            assertThat(cfg.hsts()).isEqualTo("max-age=31536000; includeSubDomains");
            assertThat(HttpConfig.keyStorePassword()).isEqualTo("s3cr3t");
            assertThat(HttpConfig.keyManagerPassword()).isEqualTo("km-pass");
        }
    }

    @Test
    @DisplayName("Missing keystore -> ConfigurationException with clear message")
    void testMissingKeyStoreThrows() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("localhost");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("7443");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn(null); // triggers error
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn("ignored");
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("*");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn(null);

            assertThatThrownBy(HttpConfig::new)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("javax.net.ssl.keyStore must be specified for HTTP transport");
        }
    }

    @Test
    @DisplayName("Missing keystore password -> ConfigurationException with clear message")
    void testMissingKeyStorePasswordThrows() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("localhost");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("7443");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn("/path/keystore.jks"); // present
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn(null); // triggers error
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("*");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn(null);

            assertThatThrownBy(HttpConfig::new)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("javax.net.ssl.keyStorePassword must be specified for HTTP transport");
        }
    }

    @Test
    @DisplayName("Non-numeric port -> ConfigurationException: \"Invalid port: <value>\"")
    void testInvalidPortNonNumeric() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("localhost");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("oops"); // non-numeric
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn("/path/keystore.jks");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn("changeit");
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("*");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn(null);

            assertThatThrownBy(HttpConfig::new)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Invalid port: oops");
        }
    }

    @Test
    @DisplayName("Out-of-range port -> ConfigurationException: \"Invalid port : <value>\"")
    void testInvalidPortOutOfRange() {
        try (MockedStatic<ServerConfig> mocked = mockStatic(ServerConfig.class)) {
            mocked.when(() -> ServerConfig.getParameter("mcp.host", "localhost"))
                  .thenReturn("localhost");
            mocked.when(() -> ServerConfig.getParameter("mcp.port", "7443"))
                  .thenReturn("70000"); // > 65535
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStore", null))
                  .thenReturn("/path/keystore.jks");
            mocked.when(() -> ServerConfig.getParameter("javax.net.ssl.keyStorePassword", null))
                  .thenReturn("changeit");
            mocked.when(() -> ServerConfig.getParameter("mcp.cors.origin", "*"))
                  .thenReturn("*");
            mocked.when(() -> ServerConfig.getParameter("mcp.hsts", null))
                  .thenReturn(null);

            assertThatThrownBy(HttpConfig::new)
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("Invalid port : 70000");
        }
    }
}
