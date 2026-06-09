package com.socialsea.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class AuthCookieUtil {
    public static final String ACCESS_TOKEN_COOKIE_NAME = "socialsea_access_token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "socialsea_refresh_token";
    public static final String ACCESS_TOKEN_COOKIE_PATH = "/";
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api";
    private static final Duration DEFAULT_REFRESH_MAX_AGE = Duration.ofDays(30);

    private AuthCookieUtil() {
    }

    public static boolean shouldUseSecureCookies(HttpServletRequest request, boolean requireHttps) {
        return requireHttps || (request != null && request.isSecure());
    }

    public static ResponseCookie buildRefreshTokenCookie(
            String refreshToken,
            HttpServletRequest request,
            boolean requireHttps,
            Duration maxAge
    ) {
        boolean secure = shouldUseSecureCookies(request, requireHttps);
        Duration effectiveMaxAge = maxAge == null ? DEFAULT_REFRESH_MAX_AGE : maxAge;
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, safeValue(refreshToken))
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(effectiveMaxAge)
                .build();
    }

    public static ResponseCookie buildAccessTokenCookie(
            String accessToken,
            HttpServletRequest request,
            boolean requireHttps,
            Duration maxAge
    ) {
        boolean secure = shouldUseSecureCookies(request, requireHttps);
        Duration effectiveMaxAge = maxAge == null ? DEFAULT_REFRESH_MAX_AGE : maxAge;
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, safeValue(accessToken))
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(effectiveMaxAge)
                .build();
    }

    public static ResponseCookie clearRefreshTokenCookie(HttpServletRequest request, boolean requireHttps) {
        boolean secure = shouldUseSecureCookies(request, requireHttps);
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public static ResponseCookie clearAccessTokenCookie(HttpServletRequest request, boolean requireHttps) {
        boolean secure = shouldUseSecureCookies(request, requireHttps);
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path(ACCESS_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public static String resolveAccessToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            String normalized = authHeader.toLowerCase();
            if (normalized.startsWith("bearer ")) {
                String token = authHeader.substring(7).trim();
                if (!token.isBlank()) {
                    return token;
                }
            } else {
                String token = authHeader.trim();
                if (!token.isBlank()) {
                    return token;
                }
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookie == null || cookie.getName() == null) continue;
            String name = cookie.getName().trim();
            if (!ACCESS_TOKEN_COOKIE_NAME.equalsIgnoreCase(name)
                    && !"access_token".equalsIgnoreCase(name)
                    && !"token".equalsIgnoreCase(name)
                    && !"jwt".equalsIgnoreCase(name)) {
                continue;
            }
            String value = safeValue(cookie.getValue());
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public static String resolveRefreshToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookie == null || cookie.getName() == null) continue;
            String name = cookie.getName().trim();
            if (!REFRESH_TOKEN_COOKIE_NAME.equalsIgnoreCase(name)
                    && !"refreshToken".equalsIgnoreCase(name)
                    && !"refresh_token".equalsIgnoreCase(name)
                    && !"jwt".equalsIgnoreCase(name)
                    && !"access_token".equalsIgnoreCase(name)) {
                continue;
            }
            String value = safeValue(cookie.getValue());
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }
}
