package com.smartstudy.auth;

/**
 * <pre>
 * GET /api/v1/auth/csrf
 * { "headerName": "X-CSRF-TOKEN", "parameterName": "_csrf", "token": "kJ3...Q==" }
 * </pre>
 */
public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
