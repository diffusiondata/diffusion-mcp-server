/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for {@link CorsFilter}.
 *
 * @author DiffusionData Limited
 */
@ExtendWith(MockitoExtension.class)
final class CorsFilterTest {

    @Mock
    private HttpConfig config;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    @Test
    @DisplayName("Wildcard origin: sets ACAO=* and handles preflight (OPTIONS) without invoking chain")
    void testWildcardPreflightOptions() throws Exception {
        when(config.corsOrigin()).thenReturn("*");
        when(request.getMethod()).thenReturn("OPTIONS");

        CorsFilter filter = new CorsFilter(config);
        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        verify(response).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Wildcard origin: non-OPTIONS request sets headers and proceeds down chain")
    void testWildcardSimpleRequest() throws Exception {
        when(config.corsOrigin()).thenReturn("*");
        when(request.getMethod()).thenReturn("GET");

        CorsFilter filter = new CorsFilter(config);
        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        verify(response).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    @DisplayName("Specific origin match: echoes Origin, sets Vary: Origin, proceeds")
    void testSpecificOriginMatch() throws Exception {
        String allowed = "https://app.example";
        when(config.corsOrigin()).thenReturn(allowed);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Origin")).thenReturn(allowed);

        CorsFilter filter = new CorsFilter(config);
        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", allowed);
        verify(response).setHeader("Vary", "Origin");
        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        verify(response).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Max-Age", "3600");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Specific origin does not match: no ACAO set, still sets other headers; OPTIONS short-circuits")
    void testSpecificOriginMismatchPreflight() throws Exception {
        String allowed = "https://app.example";
        when(config.corsOrigin()).thenReturn(allowed);
        when(request.getHeader("Origin")).thenReturn("https://malicious.example");
        when(request.getMethod()).thenReturn("OPTIONS");

        CorsFilter filter = new CorsFilter(config);
        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verify(response, never()).setHeader("Vary", "Origin");

        verify(response).setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        verify(response).setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        verify(response).setHeader("Access-Control-Max-Age", "3600");

        verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
        verifyNoInteractions(chain);
    }
}

