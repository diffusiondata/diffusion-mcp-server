/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConfigurationException}.
 *
 * @author DiffusionData Limited
 */
final class ConfigurationExceptionTest {

    @Test
    @DisplayName("Constructor(message) sets message and no cause")
    void testMessageOnlyConstructor() {
        ConfigurationException ex = new ConfigurationException("Bad config");
        assertThat(ex)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Bad config")
            .hasNoCause();
    }

    @Test
    @DisplayName("Constructor(message, cause) sets both message and cause")
    void testMessageAndCauseConstructor() {
        Throwable cause = new IllegalArgumentException("missing field");
        ConfigurationException ex =
            new ConfigurationException("Bad config", cause);

        assertThat(ex)
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("Bad config")
            .hasCause(cause);
    }

    @Test
    @DisplayName("Exception is final and extends RuntimeException (unchecked)")
    void testTypeCharacteristics() {
        assertThat(ConfigurationException.class.getSuperclass())
            .isEqualTo(RuntimeException.class);
        assertThat(
            Modifier.isFinal(ConfigurationException.class.getModifiers()))
                .as("class should be final")
                .isTrue();
    }
}
