package com.socialsea.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
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

            String token = AuthCookieUtil.resolveAccessToken(servletRequest.getServletRequest());

            if (token != null && !token.isBlank()) {
                try {
                    if (jwtUtil.isRefreshToken(token)) {
                        return true;
                    }
                    if (!jwtUtil.isExpired(token)) {
                        String email = jwtUtil.extractUsername(token);
                        UserDetails user = userDetailsService.loadUserByUsername(email);
                        attributes.put("user", user);
                    }
                } catch (Exception ignored) {
                    // Do not block websocket handshake for malformed/expired tokens.
                }
            }
        }
        // Always allow handshake. STOMP CONNECT authentication can still happen later via channel interceptor.
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {}
}
