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

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.LoginSessionService;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final LoginSessionService loginSessionService;

    public JwtHandshakeInterceptor(
            JwtUtil jwtUtil,
            UserDetailsService userDetailsService,
            UserRepository userRepository,
            LoginSessionService loginSessionService
    ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.loginSessionService = loginSessionService;
    }

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        if (isPublicSockJsPath(request)) {
            return true;
        }

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = resolveToken(servletRequest);

        if (token != null && !jwtUtil.isExpired(token)) {
            String email = jwtUtil.extractUsername(token);
            String sessionId = jwtUtil.extractTokenId(token);
            if (sessionId == null || sessionId.isBlank()) {
                return false;
            }
            User dbUser = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (dbUser == null || dbUser.getId() == null) {
                return false;
            }
            if (!loginSessionService.isActiveSession(dbUser.getId(), sessionId)) {
                return false;
            }
            UserDetails user = userDetailsService.loadUserByUsername(email);
            attributes.put("user", user);
            loginSessionService.touch(sessionId);
            return true;
        }
        return false;
    }

    private boolean isPublicSockJsPath(ServerHttpRequest request) {
        String path = request.getURI() != null ? request.getURI().getPath() : null;
        if (path == null || path.isBlank()) return false;
        String normalized = path.toLowerCase();
        return normalized.contains("/ws/info") || normalized.contains("/ws/iframe");
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
