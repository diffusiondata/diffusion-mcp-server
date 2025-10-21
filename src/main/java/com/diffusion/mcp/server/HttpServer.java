/*
 * Copyright (c) 2025 DiffusionData Ltd.
 */
package com.diffusion.mcp.server;

import static com.diffusion.mcp.DiffusionMcpServer.OBJECT_MAPPER;
import static com.diffusion.mcp.DiffusionMcpServer.SERVER_NAME;
import static com.diffusion.mcp.DiffusionMcpServer.VERSION;
import static com.diffusion.mcp.prompts.DiffusionPrompts.createPrompts;
import static com.diffusion.mcp.tools.DiffusionTools.createTools;

import java.time.Duration;
import java.util.EnumSet;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.servlet.DispatcherType;

/**
 * HTTPS Jetty server exposing an MCP async **streamable** server over a single
 * servlet.
 *
 * Endpoint: /mcp (GET = SSE stream; POST = JSON-RPC messages; DELETE = end
 * session) Health: /status
 *
 * @author DiffusionData Limited
 */
public final class HttpServer {

    private static final Logger LOG =
        LoggerFactory.getLogger(HttpServer.class);

    private Server theJettyServer;
    private SessionManager theSessionManager;
    private McpAsyncServer theMcpServer;
    private HttpServletStreamableServerTransportProvider theTransportProvider;

    public void startAndBlock(HttpConfig config) throws Exception {

        final String host = config.host();
        final int port = config.port();

        LOG.info(
            "Starting Diffusion MCP server version {} with HTTP transport at {}:{}",
            VERSION,
            host,
            port);

        theSessionManager = new SessionManager();

        theJettyServer = new Server();

        /*
         * 1) Build the streamable HttpServlet transport (single endpoint)
         */
        theTransportProvider =
            HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(OBJECT_MAPPER))
                .mcpEndpoint("/mcp") // one path for SSE (GET) + messages (POST)
                .disallowDelete(false) // set true if you want to block DELETE
                                       // /mcp
                // .keepAliveInterval(Duration.ofSeconds(20)) // optional pings
                .build();

        /*
         * 2) Build MCP async server (streamable)
         */
        theMcpServer =
            McpServer.async(theTransportProvider)
                .serverInfo(SERVER_NAME, VERSION)
                .capabilities(
                    ServerCapabilities.builder()
                        .resources(false, true) // list + change notifications
                        .tools(true)
                        .prompts(true)
                        .logging()
                        .build())
                .tools(createTools(theSessionManager))
                .prompts(createPrompts())
                .build();

        /*
         * 3) HTTPS connector
         */
        theJettyServer.addConnector(
            createHttpsConnector(theJettyServer, config));

        /*
         * 4) Servlet context, filters, and endpoints
         */
        final ServletContextHandler contextHandler =
            new ServletContextHandler(ServletContextHandler.SESSIONS);

        contextHandler.setContextPath("/");

        // Request limits
        contextHandler.setMaxFormContentSize(1 * 1024 * 1024);
        contextHandler.setMaxFormKeys(1024);

        // Security headers + CORS

        // HTTP Strict Transport Security Settings
        contextHandler.addFilter(
            new FilterHolder(
                new SecurityHeadersFilter(config, true /*onlyIfSecure=*/)),
            "/*",
            java.util.EnumSet.of(DispatcherType.REQUEST)
        );

        contextHandler.addFilter(
            new FilterHolder(new CorsFilter(config)),
            "/*",
            EnumSet.of(DispatcherType.REQUEST));

        // Mount the single streamable servlet at /mcp
        contextHandler.addServlet(
            new ServletHolder("mcp-stream", theTransportProvider), "/mcp");

        // Health probe
        contextHandler.addServlet(StatusServlet.class, "/status");

        // Gzip wrapper
        final GzipHandler gzip = new GzipHandler();
        gzip.setHandler(contextHandler);
        gzip.setMinGzipSize(1024);
        gzip.setInflateBufferSize(16 * 1024);
        theJettyServer.setHandler(gzip);

        // Access log
        theJettyServer.setRequestLog(createRequestLog());

        // shutdown
        theJettyServer.setStopAtShutdown(false);
        theJettyServer.setStopTimeout(30_000);
        Runtime.getRuntime().addShutdownHook(
            new Thread(this::stop, "mcp-shutdown"));

        theJettyServer.start();
        theJettyServer.join();
    }

    public void stop() {
        final McpAsyncServer mcpServer = theMcpServer;
        if (mcpServer != null) {
            try {
                mcpServer.close();
            }
            catch (Exception ex) {
                LOG.error("Diffusion MCP Server Closedown Error", ex);
            }
        }

        final SessionManager sessionManager = theSessionManager;
        if (sessionManager != null) {
            sessionManager.shutdown();
        }

        final HttpServletStreamableServerTransportProvider transportProvider =
            theTransportProvider;
        final Server jettyServer = theJettyServer;
        try {
            if (transportProvider != null) {
                // Ensure SSE async contexts finish before stopping Jetty
                transportProvider.closeGracefully().block(Duration.ofSeconds(5));
            }
            if (jettyServer != null && jettyServer.isRunning()) {
                jettyServer.stop();
                jettyServer.join();
                jettyServer.destroy();
            }
        }
        catch (Exception ex) {
            LOG.error("Diffusion MCP Server Jetty Closedown Error", ex);
        }
    }

    private static ServerConnector createHttpsConnector(
        Server server,
        HttpConfig config) {

        final SslContextFactory.Server ssl = new SslContextFactory.Server();

        final String keyStorePassword = HttpConfig.keyStorePassword();
        final String keyManagerPassword = HttpConfig.keyManagerPassword();

        ssl.setKeyStorePath(config.keyStore());
        ssl.setKeyStorePassword(keyStorePassword);
        ssl.setKeyManagerPassword(
            keyManagerPassword == null ? keyStorePassword : keyManagerPassword);

        // TLS hardening
        ssl.setExcludeProtocols("SSL", "SSLv2", "SSLv3", "TLSv1", "TLSv1.1");
        ssl.setIncludeProtocols("TLSv1.2", "TLSv1.3");
        ssl.setRenegotiationAllowed(false);

        final HttpConfiguration httpsConf = new HttpConfiguration();
        httpsConf.setSendServerVersion(false);
        httpsConf.setSendDateHeader(true);
        httpsConf.setRequestHeaderSize(8 * 1024);
        httpsConf.setResponseHeaderSize(8 * 1024);
        httpsConf.setOutputBufferSize(32 * 1024);
        httpsConf.setSecureScheme("https");
        httpsConf.setSecurePort(config.port());
        httpsConf.addCustomizer(new ForwardedRequestCustomizer());

        final ServerConnector serverConnector =
            new ServerConnector(
                server,
                new SslConnectionFactory(ssl, "http/1.1"),
                new HttpConnectionFactory(httpsConf));

        serverConnector.setHost(config.host());
        serverConnector.setPort(config.port());
        serverConnector.setIdleTimeout(30_000);

        return serverConnector;
    }

    private static RequestLog createRequestLog() {
        final Slf4jRequestLogWriter writer = new Slf4jRequestLogWriter();
        writer.setLoggerName("http.request");
        return new CustomRequestLog(writer, CustomRequestLog.NCSA_FORMAT);
    }
}
