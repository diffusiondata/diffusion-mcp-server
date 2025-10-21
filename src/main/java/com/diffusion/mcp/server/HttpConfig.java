/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

/**
 * Configuration for the Diffusion MCP Server over HTTP Transport.
 *
 * @author DiffusionData Limited
 */
public final class HttpConfig extends ServerConfig {

    private static final String HOST_PROPERTY = "mcp.host";
    private static final String HOST_DEFAULT = "localhost";
    private static final String PORT_PROPERTY = "mcp.port";
    private static final String PORT_DEFAULT = "7443";

    private static final String KEYSTORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEYSTORE_PASSWORD_PROPERTY =
        "javax.net.ssl.keyStorePassword";
    private static final String KEY_MANAGER_PASSWORD_PROPERTY =
        "javax.net.ssl.keyManagerPassword";

    private static final String CORS_ORIGIN_PROPERTY = "mcp.cors.origin";
    private static final String CORS_ORIGIN_DEFAULT = "*"; // Allow anything

    private static final String HSTS_PROPERTY = "mcp.hsts";

    private final String theHost;
    private final int thePort;
    private final String theKeyStore;
    private final String theCorsOrigin;
    /**
     * HTTP Strict Transport Security Settings. Can be null.
     */
    private final String theHsts;

    /**
     * HTTP Constructor.
     */
    public HttpConfig() throws ConfigurationException {

        super(Transport.HTTPS);

        theHost = getParameter(HOST_PROPERTY, HOST_DEFAULT);
        thePort = parsePort();
        theKeyStore = getParameter(KEYSTORE_PROPERTY, null);
        if (theKeyStore == null) {
            throw new ConfigurationException(
                KEYSTORE_PROPERTY + " must be specified for HTTP transport");
        }
        final String keyStorePasword =
            getParameter(KEYSTORE_PASSWORD_PROPERTY, null);
        if (keyStorePasword == null) {
            throw new ConfigurationException(
                KEYSTORE_PASSWORD_PROPERTY +
                    " must be specified for HTTP transport");
        }
        theCorsOrigin = getParameter(CORS_ORIGIN_PROPERTY, CORS_ORIGIN_DEFAULT);
        theHsts = getParameter(HSTS_PROPERTY, null);
    }

    String host() {
        return theHost;
    }

    int port() {
        return thePort;
    }

    String keyStore() {
        return theKeyStore;
    }

    String corsOrigin() {
        return theCorsOrigin;
    }

    /**
     * Returns HTTP Strict Transport Security Settings or null if none set.
     */
    String hsts() {
        return theHsts;
    }

    static String keyStorePassword() {
        // The keystore password is not stored.
        return getParameter(KEYSTORE_PASSWORD_PROPERTY, null);
    }

    static String keyManagerPassword() {
        // The key manager password is not stored.
        return getParameter(KEY_MANAGER_PASSWORD_PROPERTY, null);
    }

    private static int parsePort() throws ConfigurationException {
        final String portParameter = getParameter(PORT_PROPERTY, PORT_DEFAULT);
        try {
            final int port = Integer.parseInt(portParameter);
            if (port < 1 || port > 65535) {
                throw new ConfigurationException(
                    "Invalid port : " + portParameter);
            }
            return port;
        }
        catch (NumberFormatException e) {
            throw new ConfigurationException(
                "Invalid port: " + portParameter);
        }
    }
}
