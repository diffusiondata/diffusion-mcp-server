/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.DiffusionMcpServer.SERVER_NAME;
import static com.diffusion.mcp.DiffusionMcpServer.VERSION;
import static com.diffusion.mcp.prompts.DiffusionPrompts.createPrompts;
import static com.diffusion.mcp.tools.DiffusionTools.createTools;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/**
 * Stdio Implementation of the Diffusion MCP Server.
 *
 * @author DiffusionData Limited
 */
public final class StdioServer {

    private static final Logger LOG =
        LoggerFactory.getLogger(StdioServer.class);

    private StdioServer() { }

    /**
     * Start with stdio transport.
     */
    public static void startAndBlock() throws InterruptedException {

        LOG.info(
            "Starting Diffusion MCP server version {} with STDIO transport",
            VERSION);

        final SessionManager sessionManager = new SessionManager();

        final McpAsyncServer server =
            McpServer.async(
                new StdioServerTransportProvider(new JacksonMcpJsonMapper(OBJECT_MAPPER)))
                .serverInfo(SERVER_NAME, VERSION)
                .capabilities(
                    ServerCapabilities.builder()
                        .tools(true)
                        .prompts(true)
                        .logging()
                        .build())
                .tools(createTools(sessionManager))
                .prompts(createPrompts())
                .build();

        waitForShutdown(server, sessionManager);
    }

    private static void waitForShutdown(
        McpAsyncServer server, SessionManager sessionManager)
        throws InterruptedException {

        // Keep the server running
        final CountDownLatch latch = new CountDownLatch(1);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Diffusion MCP server...");
            try {
                // Close all sessions
                sessionManager.shutdown();

                // Close the MCP server
                if (server != null) {
                    server.close();
                }

                LOG.info("Diffusion MCP server shut down successfully");
            }
            finally {
                latch.countDown();
            }
        }));

        // Wait for shutdown signal
        latch.await();
    }

}
