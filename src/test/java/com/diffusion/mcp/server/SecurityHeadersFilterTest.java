/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for {@link SecurityHeadersFilter}.
 *
 * @author DiffusionData Limited
 */
final class SecurityHeadersFilterTest {

    @Test
    @DisplayName("Sets core security headers on all responses")
    void testCoreHeadersAlwaysSet() throws IOException, ServletException {
        HttpConfig cfg = mock(HttpConfig.class);
        when(cfg.hsts()).thenReturn(null); // no HSTS

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new SecurityHeadersFilter(cfg, true).doFilter(req, res, chain);

        verify(res).setHeader("X-Content-Type-Options", "nosniff");
        verify(res).setHeader("X-Frame-Options", "DENY");
        verify(res).setHeader("Referrer-Policy", "no-referrer");
        verify(res).setHeader("Permissions-Policy", "geolocation=()");
        verify(res).setHeader("X-XSS-Protection", "0");
        verify(chain).doFilter(req, res);
        verify(res, never()).setHeader(eq("Strict-Transport-Security"), anyString());
    }

    @Test
    @DisplayName("HSTS: onlyIfSecure=true -> set header only for HTTPS requests")
    void testHstsOnlyIfSecure() throws IOException, ServletException {
        HttpConfig cfg = mock(HttpConfig.class);
        when(cfg.hsts()).thenReturn("max-age=31536000; includeSubDomains");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.isSecure()).thenReturn(true);
        new SecurityHeadersFilter(cfg, true).doFilter(req, res, chain);
        verify(res).setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        reset(res);
        when(req.isSecure()).thenReturn(false);
        new SecurityHeadersFilter(cfg, true).doFilter(req, res, chain);
        verify(res, never()).setHeader(eq("Strict-Transport-Security"), anyString());
    }

    @Test
    @DisplayName("HSTS: onlyIfSecure=false -> set header whenever configured")
    void testHstsAlwaysWhenConfigured() throws IOException, ServletException {
        HttpConfig cfg = mock(HttpConfig.class);
        when(cfg.hsts()).thenReturn("max-age=1000");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.isSecure()).thenReturn(false);
        new SecurityHeadersFilter(cfg, false).doFilter(req, res, chain);

        verify(res).setHeader("Strict-Transport-Security", "max-age=1000");
        verify(chain).doFilter(req, res);
    }
}
