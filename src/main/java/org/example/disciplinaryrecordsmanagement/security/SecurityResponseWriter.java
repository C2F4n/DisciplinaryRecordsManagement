package org.example.disciplinaryrecordsmanagement.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.example.disciplinaryrecordsmanagement.exception.ErrorCode;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityResponseWriter {
    private final JsonMapper json;

    public SecurityResponseWriter(JsonMapper json) {
        this.json = json;
    }

    public void success(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.OK, Result.success());
    }

    public void error(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code.status(), Result.error(code));
    }

    private void write(HttpServletResponse response, HttpStatus status, Result<Void> result) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(result));
    }
}
