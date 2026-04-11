package com.socialsea.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String path = servletRequest.getServletRequest().getRequestURI();
            if (path != null) {
                String normalized = path.toLowerCase();
                if (normalized.endsWith("/ws/info") || normalized.contains("/ws/info") || normalized.contains("/ws/iframe")) {
                    return true; // allow SockJS info/iframe without auth
                }
            }

            String token = resolveToken(servletRequest);

            if (token != null && !jwtUtil.isExpired(token)) {
                String email = jwtUtil.extractUsername(token);
                UserDetails user = userDetailsService.loadUserByUsername(email);
                attributes.put("user", user);
                return true;
            }
        }
        return false;
    }

    private String resolveToken(ServletServerHttpRequest servletRequest) {
        String authHeader = servletRequest.getServletRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isBlank()) return token;
        }

        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie == null || cookie.getName() == null) continue;
                String name = cookie.getName();
                if (!"token".equalsIgnoreCase(name) && !"jwt".equalsIgnoreCase(name) && !"access_token".equalsIgnoreCase(name)) {
                    continue;
                }
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) return value.trim();
            }
        }

        String queryToken = servletRequest.getServletRequest().getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {}
}
