/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp;

import static com.diffusion.mcp.server.ServerConfig.Transport.STDIO;

import com.diffusion.mcp.server.ConfigurationException;
import com.diffusion.mcp.server.HttpConfig;
import com.diffusion.mcp.server.HttpServer;
import com.diffusion.mcp.server.ServerConfig;
import com.diffusion.mcp.server.StdioServer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Diffusion MCP Server Launcher.
 *
 * @author DiffusionData Limited
 */
public final class DiffusionMcpServer {

    public static final String SERVER_NAME = "diffusion-mcp-server";
    public static final String VERSION = "1.0.0";

    /** Shared, safe ObjectMapper (no polymorphic typing). */
    public static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private DiffusionMcpServer() {
    }

    public static void main(String[] args) throws Exception {

        // honour --help
        if (args.length == 1 &&
            ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printHelp();
            return;
        }

        final ServerConfig configuration;
        try {
            configuration = ServerConfig.load();
        }
        catch (ConfigurationException ex) {
            System.err.println(ex.getMessage());
            System.err.println("Use --help for usage.");
            System.exit(2);
            return;
        }

        if (configuration.transport() == STDIO) {
            StdioServer.startAndBlock();
        }
        else {
            new HttpServer().startAndBlock((HttpConfig)configuration);
        }
    }

    private static void printHelp() {
        System.out.println(
            """
                Diffusion MCP Server

                Configuration via system properties or environment variables:

                  mcp.transport   | MCP_TRANSPORT   = stdio (default) | https
                  mcp.host        | MCP_HOST        = HTTPS bind host (default: localhost)
                  mcp.port        | MCP_PORT        = HTTPS port (default: 7443)

                HTTPS requires a Java keystore:
                  -Djavax.net.ssl.keyStore=keystore.jks
                  -Djavax.net.ssl.keyStorePassword=changeit
                  -Djavax.net.ssl.keyManagerPassword=changeit

                Optional:
                  -Dmcp.cors.origin=<origin>        (default http://localhost:3000)
                  -Dmcp.hsts="max-age=31536000; includeSubDomains"

                Examples:
                  # Default (stdio)
                  java -jar server.jar

                  # HTTPS on all interfaces
                  java -Dmcp.transport=https -Dmcp.host=0.0.0.0 -Dmcp.port=7443 \\
                       -Djavax.net.ssl.keyStore=/path/keystore.jks \\
                       -Djavax.net.ssl.keyStorePassword=***** \\
                       -Djavax.net.ssl.keyManagerPassword=***** \\
                       -jar server.jar
                """);
    }
}
