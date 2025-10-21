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
 * Security Headers Filter.
 * <p>
 * HSTS = HTTP Strict Transport Security.<br>
 * e.g. "Strict-Transport-Security: max-age=31536000; includeSubDomains;
 * preload".<b> max-age = how long (in seconds) the rule sticks<br>
 * includeSubDomains = apply to all subdomains too<b> preload = request
 * inclusion in browser preload lists (use only when fully ready)
 *
 * @author DiffusionData Limited
 */
public final class SecurityHeadersFilter implements Filter {

    private final String theHsts;
    private final boolean onlyIfSecure;

    /**
     * Constructor.
     * @param config
     * @param onlyIfSecure
     */
    SecurityHeadersFilter(HttpConfig config, boolean onlyIfSecure) {
        theHsts = config.hsts();
        this.onlyIfSecure = onlyIfSecure;
    }

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain filterChain)
        throws IOException, ServletException {

        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        httpResponse.setHeader("Permissions-Policy", "geolocation=()");
        httpResponse.setHeader("X-XSS-Protection", "0");

        // Only set HSTS if configured, and (optionally) only on HTTPS
        // requests
        if (theHsts != null && !theHsts.isBlank() &&
            (!onlyIfSecure || httpRequest.isSecure())) {
            httpResponse.setHeader("Strict-Transport-Security", theHsts);
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