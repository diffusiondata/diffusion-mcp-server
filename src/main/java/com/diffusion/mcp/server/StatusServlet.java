/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static com.diffusion.mcp.DiffusionMcpServer.VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple health/status endpoint.
 *
 * @author DiffusionData Limited
 */
public final class StatusServlet extends HttpServlet {

    private static final long serialVersionUID = 8984738737517066255L;

    @Override
    protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        final String json =
            """
                {"ok":true,"transport":"https+streamable","endpoint":"/mcp","version":"%s"}
            """
            .formatted(VERSION);

        response.getOutputStream().write(json.getBytes(UTF_8));
    }
}