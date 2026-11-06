package com.smartstudy.config;

import java.io.IOException;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.smartstudy.common.JsonErrorWriter;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns a JSON 401 response (instead of a redirect to a login page) when an
 * unauthenticated client calls a protected endpoint.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final Clock clock;

    public JsonAuthenticationEntryPoint(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        JsonErrorWriter.write(response, clock, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication is required. Please sign in.", request.getRequestURI());
    }
}
