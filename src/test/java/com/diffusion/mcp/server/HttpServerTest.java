/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

import com.diffusion.mcp.tools.SessionManager;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link HttpServer}.
 *
 * Covers:
 *  - stop() happy path, jetty-not-running branch, exception-tolerant branch, and null-safe no-op
 *  - HTTPS connector creation (host/port/timeout, factories, HttpConfiguration, Forwarded customizer)
 *  - startAndBlock() on an ephemeral port with a temp keystore (happy path + HSTS + bad-password)
 *
 * Author: DiffusionData Limited
 */
final class HttpServerTest {

    @Test
    @DisplayName("stop(): closes MCP server, shuts down SessionManager, closes transport provider, and stops Jetty when running")
    void testStopHappyPath() throws Exception {
        HttpServer http = new HttpServer();

        McpAsyncServer mcp = mock(McpAsyncServer.class);
        SessionManager sm = mock(SessionManager.class);
        HttpServletStreamableServerTransportProvider tp = mock(HttpServletStreamableServerTransportProvider.class);
        when(tp.closeGracefully()).thenReturn(Mono.empty());

        Server jetty = mock(Server.class);
        when(jetty.isRunning()).thenReturn(true);

        setPrivate(http, "theMcpServer", mcp);
        setPrivate(http, "theSessionManager", sm);
        setPrivate(http, "theTransportProvider", tp);
        setPrivate(http, "theJettyServer", jetty);

        http.stop();

        verify(mcp).close();
        verify(sm).shutdown();
        verify(tp).closeGracefully();
        verify(jetty).stop();
        verify(jetty).join();
        verify(jetty).destroy();
    }

    @Test
    @DisplayName("stop(): when Jetty is not running, skip stop/join/destroy but still close MCP + Session + Transport")
    void testStopJettyNotRunning() throws Exception {
        HttpServer http = new HttpServer();

        McpAsyncServer mcp = mock(McpAsyncServer.class);
        SessionManager sm = mock(SessionManager.class);
        HttpServletStreamableServerTransportProvider tp = mock(HttpServletStreamableServerTransportProvider.class);
        when(tp.closeGracefully()).thenReturn(Mono.empty());

        Server jetty = mock(Server.class);
        when(jetty.isRunning()).thenReturn(false);

        setPrivate(http, "theMcpServer", mcp);
        setPrivate(http, "theSessionManager", sm);
        setPrivate(http, "theTransportProvider", tp);
        setPrivate(http, "theJettyServer", jetty);

        http.stop();

        verify(mcp).close();
        verify(sm).shutdown();
        verify(tp).closeGracefully();
        verify(jetty, never()).stop();
        verify(jetty, never()).join();
        verify(jetty, never()).destroy();
    }

    @Test
    @DisplayName("stop(): handles exceptions from components gracefully (does not throw)")
    void testStopHandlesExceptionsGracefully() throws Exception {
        HttpServer http = new HttpServer();

        McpAsyncServer mcp = mock(McpAsyncServer.class);
        doThrow(new RuntimeException("boom")).when(mcp).close();

        SessionManager sm = mock(SessionManager.class);
        doNothing().when(sm).shutdown();

        HttpServletStreamableServerTransportProvider tp = mock(HttpServletStreamableServerTransportProvider.class);
        when(tp.closeGracefully()).thenReturn(Mono.error(new IllegalStateException("nope")));

        Server jetty = mock(Server.class);
        when(jetty.isRunning()).thenReturn(true);
        doThrow(new RuntimeException("stop fail")).when(jetty).stop();

        setPrivate(http, "theMcpServer", mcp);
        setPrivate(http, "theSessionManager", sm);
        setPrivate(http, "theTransportProvider", tp);
        setPrivate(http, "theJettyServer", jetty);

        assertThatCode(http::stop).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("stop(): with null internals -> safe no-op")
    void testStopWithNullsIsSafe() {
        HttpServer http = new HttpServer();
        assertThatCode(http::stop).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("createHttpsConnector(): host/port/timeout, SSL+HTTP factories, HttpConfiguration, Forwarded customizer")
    void testCreateHttpsConnectorBasicConfiguration() throws Exception {
        // Create an empty, accessible keystore and set the keystore password
        Path ksPath = createEmptyJks("changeit");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        System.clearProperty("javax.net.ssl.keyManagerPassword");

        try {
            Server server = new Server();

            HttpConfig cfg = mock(HttpConfig.class);
            when(cfg.host()).thenReturn("127.0.0.1");
            when(cfg.port()).thenReturn(8443);
            when(cfg.keyStore()).thenReturn(ksPath.toString());

            Method m = HttpServer.class.getDeclaredMethod("createHttpsConnector", Server.class, HttpConfig.class);
            m.setAccessible(true);

            Object result = m.invoke(null, server, cfg);
            assertThat(result).isInstanceOf(ServerConnector.class);

            try (ServerConnector connector = (ServerConnector) result) {
                // host/port/idleTimeout
                assertThat(connector.getHost()).isEqualTo("127.0.0.1");
                assertThat(connector.getPort()).isEqualTo(8443);
                assertThat(connector.getIdleTimeout()).isEqualTo(30_000);

                // SSL then HTTP factories
                boolean hasSsl = connector.getConnectionFactories().stream()
                    .anyMatch(SslConnectionFactory.class::isInstance);
                boolean hasHttp = connector.getConnectionFactories().stream()
                    .anyMatch(HttpConnectionFactory.class::isInstance);
                assertThat(hasSsl).isTrue();
                assertThat(hasHttp).isTrue();

                HttpConfiguration conf =
                    connector.getConnectionFactories().stream()
                        .filter(HttpConnectionFactory.class::isInstance)
                        .map(f -> ((HttpConnectionFactory) f).getHttpConfiguration())
                        .findFirst()
                        .orElseThrow();

                assertThat(conf.getSecureScheme()).isEqualTo("https");
                assertThat(conf.getSecurePort()).isEqualTo(8443);
                assertThat(conf.getRequestHeaderSize()).isEqualTo(8 * 1024);
                assertThat(conf.getResponseHeaderSize()).isEqualTo(8 * 1024);
                assertThat(conf.getOutputBufferSize()).isEqualTo(32 * 1024);
                assertThat(conf.getSendServerVersion()).isFalse();
                assertThat(conf.getSendDateHeader()).isTrue();
                assertThat(conf.getCustomizers().stream().anyMatch(ForwardedRequestCustomizer.class::isInstance)).isTrue();
            }
        }
        finally {
            System.clearProperty("javax.net.ssl.keyStorePassword");
            System.clearProperty("javax.net.ssl.keyManagerPassword");
            try {
                Files.deleteIfExists(ksPath);
            }
            catch (Exception ignore) {
                // Ignore
            }
        }
    }

    @Test
    @Tag("integration")
    @Timeout(30)
    @DisplayName("startAndBlock(): starts Jetty (ephemeral port), installs filters & request log; stop() unblocks")
    void testStartAndBlockHappyPath() throws Exception {
        Path ks = createEmptyJks("changeit");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
        System.clearProperty("javax.net.ssl.keyManagerPassword");

        HttpConfig cfg = Mockito.mock(HttpConfig.class);
        when(cfg.host()).thenReturn("127.0.0.1");
        when(cfg.port()).thenReturn(0); // ephemeral
        when(cfg.keyStore()).thenReturn(ks.toString());
        when(cfg.corsOrigin()).thenReturn("*");
        when(cfg.hsts()).thenReturn(null);

        HttpServer server = new HttpServer();

        Method startWithCfg = findMethod(HttpServer.class, "startAndBlock", HttpConfig.class);
        Method startNoArgs  = (startWithCfg == null) ? findMethod(HttpServer.class, "startAndBlock") : null;
        if (startWithCfg == null && startNoArgs == null) {
            throw new IllegalStateException("No startAndBlock() method found on HttpServer");
        }

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "http-start-thread");
            t.setDaemon(true);
            return t;
        });

        Future<?> f = exec.submit(() -> {
            try {
                if (startWithCfg != null) {
                    startWithCfg.setAccessible(true);
                    startWithCfg.invoke(server, cfg);
                }
                else {
                    startNoArgs.setAccessible(true);
                    startNoArgs.invoke(server);
                }
            }
            catch (Throwable ignored) {
                // if stop() closes while join() blocks, we can see exceptions; that's fine
            }
        });

        try {
            // Wait until Jetty is running
            Server jetty = awaitJettyRunning(server, 10, TimeUnit.SECONDS);
            assertThat(jetty).isNotNull();
            assertThat(jetty.isRunning()).isTrue();

            // Locate servlet context and ensure our filters are installed
            ServletContextHandler ctx = findServletContext(jetty);
            assertThat(ctx).as("ServletContextHandler present").isNotNull();

            List<String> filterClasses = ctx.getServletHandler().getFilters() == null
                ? List.of()
                : Arrays.stream(ctx.getServletHandler().getFilters())
                    .map(FilterHolder::getClassName)
                    .toList();

            assertThat(filterClasses).anySatisfy(n -> assertThat(n).contains("SecurityHeadersFilter"));
            assertThat(filterClasses).anySatisfy(n -> assertThat(n).contains("CorsFilter"));

            // Request log present (if configured by server)
            RequestLog requestLog = jetty.getRequestLog();
            assertThat(requestLog).as("Request log should be configured").isNotNull();

            // Stop to unblock join
            server.stop();

            // And wait for the thread to finish
            f.get(10, TimeUnit.SECONDS);
        }
        finally {
            exec.shutdownNow();
            System.clearProperty("javax.net.ssl.keyStorePassword");
            System.clearProperty("javax.net.ssl.keyManagerPassword");
            try {
                Files.deleteIfExists(ks);
            }
            catch (Exception ignore) {
                // Ignore
            }
        }
    }

    @Test
    @Tag("integration")
    @Timeout(20)
    @DisplayName("startAndBlock(): wrong keystore password -> startup fails")
    void testStartAndBlockBadKeystorePassword() throws Exception {
        Path ks = createEmptyJks("right-password");
        System.setProperty("javax.net.ssl.keyStorePassword", "wrong-password");
        System.clearProperty("javax.net.ssl.keyManagerPassword");

        HttpConfig cfg = Mockito.mock(HttpConfig.class);
        when(cfg.host()).thenReturn("127.0.0.1");
        when(cfg.port()).thenReturn(0);
        when(cfg.keyStore()).thenReturn(ks.toString());
        when(cfg.corsOrigin()).thenReturn("*");
        when(cfg.hsts()).thenReturn(null);

        HttpServer server = new HttpServer();

        Method startWithCfg = findMethod(HttpServer.class, "startAndBlock", HttpConfig.class);
        if (startWithCfg == null) {
            // If your signature is startAndBlock() without args, we can’t pass a bad cfg; skip.
            System.clearProperty("javax.net.ssl.keyStorePassword");
            try {
                Files.deleteIfExists(ks);
            }
            catch (Exception ignore) {
                // Ignore
            }
            return;
        }
        startWithCfg.setAccessible(true);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> {
            try {
                startWithCfg.invoke(server, cfg);
            }
            catch (Throwable e) {
                throw new CompletionException(e.getCause() != null ? e.getCause() : e);
            }
        });

        try {
            assertThatThrownBy(() -> {
                try {
                    f.get(10, TimeUnit.SECONDS);
                }
                catch (ExecutionException ex) {
                    throw ex.getCause();
                }
            }).isInstanceOf(Exception.class); // Jetty SSL init error
        }
        finally {
            exec.shutdownNow();
            server.stop();
            System.clearProperty("javax.net.ssl.keyStorePassword");
            System.clearProperty("javax.net.ssl.keyManagerPassword");
            try {
                Files.deleteIfExists(ks);
            }
            catch (Exception ignore) {
                // Ignore
            }
        }
    }

    @Test
    @Tag("integration")
    @Timeout(30)
    @DisplayName("startAndBlock(): starts successfully with HSTS configured")
    void testStartWithHsts() throws Exception {
        Path ks = createEmptyJks("changeit");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

        HttpConfig cfg = Mockito.mock(HttpConfig.class);
        when(cfg.host()).thenReturn("127.0.0.1");
        when(cfg.port()).thenReturn(0);
        when(cfg.keyStore()).thenReturn(ks.toString());
        when(cfg.corsOrigin()).thenReturn("*");
        when(cfg.hsts()).thenReturn("max-age=1000; includeSubDomains");

        HttpServer server = new HttpServer();

        Method startWithCfg = findMethod(HttpServer.class, "startAndBlock", HttpConfig.class);
        if (startWithCfg == null) {
            System.clearProperty("javax.net.ssl.keyStorePassword");
            try {
                Files.deleteIfExists(ks);
            }
            catch (Exception ignore) {
                // Ignore
            }
            return;
        }
        startWithCfg.setAccessible(true);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> f = exec.submit(() -> {
            try {
                startWithCfg.invoke(server, cfg);
            }
            catch (Throwable ignored) {
                // Ignore
            }
        });

        try {
            Server jetty = awaitJettyRunning(server, 10, TimeUnit.SECONDS);
            assertThat(jetty).isNotNull();
            assertThat(jetty.isRunning()).isTrue();
        }
        finally {
            server.stop();
            f.cancel(true);
            exec.shutdownNow();
            System.clearProperty("javax.net.ssl.keyStorePassword");
            try {
                Files.deleteIfExists(ks);
            }
            catch (Exception ignore) {
                // Ignore
            }
        }
    }

    private static void setPrivate(Object target, String field, Object value) throws Exception {
        Field f = HttpServer.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(m -> m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params))
            .findFirst()
            .orElse(null);
    }

    private static Server awaitJettyRunning(HttpServer http, long time, TimeUnit unit) throws Exception {
        long deadline = System.nanoTime() + unit.toNanos(time);
        do {
            Server s = getJetty(http);
            if (s != null && s.isRunning()) return s;
            Thread.sleep(50);
        }
        while (System.nanoTime() < deadline);
        return getJetty(http);
    }

    private static Server getJetty(HttpServer http) throws Exception {
        Field f = HttpServer.class.getDeclaredField("theJettyServer");
        f.setAccessible(true);
        return (Server) f.get(http);
    }

    /**
     * Walk the Jetty handler tree and return the first ServletContextHandler we find.
     * Avoids compile-time deps on HandlerCollection/HandlerList and works across Jetty versions.
     */
    private static ServletContextHandler findServletContext(Server jetty) {
        if (jetty == null) {
            return null;
        }
        return findInHandlerTree(jetty.getHandler());
    }

    private static ServletContextHandler findInHandlerTree(Handler h) {
        if (h == null) {
            return null;
        }

        // Direct hit
        if (h instanceof ServletContextHandler sch) {
            return sch;
        }

        // Try a convenient "getChildHandlerByClass" if present on this object
        try {
            Method m = h.getClass().getMethod("getChildHandlerByClass", Class.class);
            Object obj = m.invoke(h, ServletContextHandler.class);
            if (obj instanceof ServletContextHandler sch) {
                return sch;
            }
            if (obj instanceof Handler child) {
                ServletContextHandler r = findInHandlerTree(child);
                if (r != null) {
                    return r;
                }
            }
        }
        catch (Exception ignored) {
            // method not present on this Jetty version/type
        }

        // Single child: HandlerWrapper-style "getHandler()"
        try {
            Method m = h.getClass().getMethod("getHandler");
            Object child = m.invoke(h);
            if (child instanceof Handler childHandler) {
                ServletContextHandler r = findInHandlerTree(childHandler);
                if (r != null) {
                    return r;
                }
            }
        }
        catch (Exception ignored) {
            // Ignored
        }

        // Multiple children: Collection-style "getHandlers()"
        try {
            Method m = h.getClass().getMethod("getHandlers");
            Object arr = m.invoke(h);
            if (arr instanceof Object[]) {
                for (Object o : (Object[]) arr) {
                    if (o instanceof Handler childHandler) {
                        ServletContextHandler r = findInHandlerTree(childHandler);
                        if (r != null) {
                            return r;
                        }
                    }
                }
            }
        }
        catch (Exception ignored) {
            // Ignored
        }

        // As a last resort, some Jetty types expose ContainerLifeCycle#getBean(Class)
        try {
            Method m = h.getClass().getMethod("getBean", Class.class);
            Object bean = m.invoke(h, ServletContextHandler.class);
            if (bean instanceof ServletContextHandler sch) {
                return sch;
            }
        }
        catch (Exception ignored) {
            // Ignored
        }

        return null;
    }


    /** Create an empty JKS so Jetty’s keystore path check passes. */
    private static Path createEmptyJks(String password) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType()); // usually "jks"
        char[] pw = password.toCharArray();
        ks.load(null, pw);
        Path file = Files.createTempFile("httpserver-test-", ".jks");
        try (OutputStream out = Files.newOutputStream(file)) {
            ks.store(out, pw);
        }
        return file;
    }
}
