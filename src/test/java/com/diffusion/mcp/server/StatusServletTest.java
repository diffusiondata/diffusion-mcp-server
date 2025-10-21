/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for {@link StatusServlet}.
 *
 * @author DiffusionData Limited
 */
final class StatusServletTest {

    @Test
    @DisplayName("doGet: writes JSON with ok=true, endpoint and transport; sets UTF-8 and application/json")
    void testDoGetResponse() throws IOException {
        StatusServlet servlet = new StatusServlet();

        HttpServletRequest req = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = org.mockito.Mockito.mock(HttpServletResponse.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ServletOutputStream sos = new ServletOutputStream() {
            @Override public void write(int b) { baos.write(b); }
            @Override public void setWriteListener(WriteListener writeListener) {
                // No-op
            }
            @Override public boolean isReady() { return true; }
        };

        org.mockito.Mockito.when(res.getOutputStream()).thenReturn(sos);

        servlet.doGet(req, res);

        org.mockito.Mockito.verify(res).setCharacterEncoding("UTF-8");
        org.mockito.Mockito.verify(res).setContentType("application/json");

        String body = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body)
            .contains("\"ok\":true")
            .contains("\"endpoint\":\"/mcp\"")
            .contains("\"transport\":\"https+streamable\"")
            .contains("\"version\"");
    }
}

