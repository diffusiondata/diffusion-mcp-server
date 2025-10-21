/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import java.util.Locale;

/**
 * Base class for MCP server configuration.
 *
 * @author DiffusionData Limited
 */
public abstract class ServerConfig {

    private static final String TRANSPORT_PROPERTY = "mcp.transport";
    private static final String TRANSPORT_DEFAULT = "stdio";

    public enum Transport {
        STDIO,
        HTTPS
    }

    private final Transport theTransport;

    /**
     * Constructor.
     *
     * @param transport
     */
    ServerConfig(Transport transport) {
        theTransport = transport;
    }

    /**
     * Returns the transport.
     */
    public Transport transport() {
        return theTransport;
    }

    /**
     * Load the configuration.
     */
    public static ServerConfig load() throws ConfigurationException {
        if (parseTransport() == Transport.STDIO) {
            return new StdioConfig();
        }
        else {
            return new HttpConfig();
        }
    }

    /**
     * Read and parse the transport property - if supplied.
     */
    private static Transport parseTransport() throws ConfigurationException {
        final String transport =
            getParameter(TRANSPORT_PROPERTY, TRANSPORT_DEFAULT)
                .toLowerCase(Locale.ROOT).trim();
        return switch (transport) {
        case TRANSPORT_DEFAULT, "" -> Transport.STDIO;
        case "https", "http", "tls" -> Transport.HTTPS;
        default -> throw new ConfigurationException(
            "Unknown mcp.transport: " + transport + " (use stdio|https)");
        };
    }

    /**
     * Gets a named parameter, either from the system property with the given
     * key or from an environment variable with a name derived from it.
     *
     * @param key the system property key
     * @param defaultValue the value to return if neither the system property
     *        nor an equivalent environment b=variable has been set.
     */
    static String getParameter(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(envForProp(key));
        }
        return (value == null || value.isBlank()) ? defaultValue
            : value.trim();
    }

    /**
     * Returns an environment variable name to match the supplied system
     * property name.
     */
    private static String envForProp(String prop) {
        return prop.toUpperCase(Locale.ROOT)
            .replace('.', '_').replace('-', '_');
    }
}
