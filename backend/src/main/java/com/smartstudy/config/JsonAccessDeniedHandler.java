package com.smartstudy.config;

import java.io.IOException;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.smartstudy.common.JsonErrorWriter;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Returns a JSON 403 response. A missing or invalid CSRF token gets its own error code
 * ({@code CSRF_INVALID}) so the frontend can fetch a fresh token and retry once.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final Clock clock;

    public JsonAccessDeniedHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            JsonErrorWriter.write(response, clock, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                    "Your security token is missing or expired. Please retry.", request.getRequestURI());
            return;
        }
        JsonErrorWriter.write(response, clock, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.", request.getRequestURI());
    }
}
