package com.leo.myblog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.myblog.common.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class SecurityResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), Result.error(status, message));
    }
}
