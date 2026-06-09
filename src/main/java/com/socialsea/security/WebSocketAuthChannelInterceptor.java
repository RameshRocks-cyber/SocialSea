package com.socialsea.security;

import com.socialsea.service.PresenceService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final PresenceService presenceService;

    public WebSocketAuthChannelInterceptor(JwtUtil jwtUtil, UserDetailsService userDetailsService, PresenceService presenceService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.presenceService = presenceService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (touchPresence(accessor)) {
            return message;
        }
        if (!(StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command))) {
            return message;
        }

        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            return message;
        }
        if (jwtUtil.isRefreshToken(token)) {
            return message;
        }

        String email;
        try {
            if (jwtUtil.isExpired(token)) {
                return message;
            }
            email = jwtUtil.extractUsername(token);
        } catch (Exception ignored) {
            // Invalid/malformed JWT must not break CONNECT handling.
            return message;
        }
        if (email == null || email.isBlank()) {
            return message;
        }

        try {
            UserDetails user = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            accessor.setUser(authentication);
            touchPresence(user.getUsername(), accessor.getSessionId());
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (Exception ignored) {
            // Keep socket flow resilient; anonymous connect may still be used for non-user channels.
        }
        return message;
    }

    private boolean touchPresence(StompHeaderAccessor accessor) {
        if (accessor == null) {
            return false;
        }

        var user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null || sessionId.isBlank()) {
            return false;
        }

        try {
            presenceService.refreshSocket(user.getName(), sessionId);
        } catch (Exception ignored) {
            // Presence must never block websocket traffic.
        }
        return true;
    }

    private void touchPresence(String username, String sessionId) {
        if (username == null || username.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        try {
            presenceService.refreshSocket(username, sessionId);
        } catch (Exception ignored) {
            // Presence must never block websocket traffic.
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String bearer = firstHeader(accessor, "Authorization");
        if ((bearer == null || bearer.isBlank())) {
            bearer = firstHeader(accessor, "authorization");
        }
        String bearerToken = extractBearerToken(bearer);
        if (bearerToken != null) {
            return bearerToken;
        }

        String token = firstHeader(accessor, "token");
        if (token == null || token.isBlank()) {
            token = firstHeader(accessor, "access_token");
        }
        if (token == null || token.isBlank()) {
            token = firstHeader(accessor, "accessToken");
        }
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return null;
    }

    private String extractBearerToken(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String normalized = headerValue.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("bearer ")) {
            return headerValue.trim();
        }
        return headerValue.substring(7).trim();
    }

    private String firstHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String first = values.get(0);
        return first == null ? null : first.trim();
    }
}
