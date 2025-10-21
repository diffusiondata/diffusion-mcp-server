/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS filter with wildcard default for accessibility. Defaults to "*" for open
 * access, but supports specific origins when configured.
 *
 * @author DiffusionData Limited
 */
public final class CorsFilter implements Filter {

    private final String theOrigin;

    public CorsFilter(HttpConfig config) {
        theOrigin = config.corsOrigin();
    }

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain filterChain)
        throws IOException, ServletException {

        final var httpRequest = (HttpServletRequest) request;
        final var httpResponse = (HttpServletResponse) response;

        // If wildcard, use it directly
        // Otherwise, validate against specific origin(s)
        if ("*".equals(theOrigin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        }
        else {
            final String origin = httpRequest.getHeader("Origin");
            if (origin != null && origin.equals(theOrigin)) {
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                httpResponse.setHeader("Vary", "Origin");
            }
        }

        httpResponse.setHeader(
            "Access-Control-Allow-Headers",
            "Content-Type, Authorization");

        httpResponse.setHeader(
            "Access-Control-Allow-Methods",
            "GET, POST, DELETE, OPTIONS");

        httpResponse.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}