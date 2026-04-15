package com.socialsea.security;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.LoginSessionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final LoginSessionService loginSessionService;

    public WebSocketAuthChannelInterceptor(
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
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        if (accessor.getUser() != null) {
            return message;
        }

        String token = resolveToken(accessor);
        if (token == null || token.isBlank() || jwtUtil.isExpired(token)) {
            return message;
        }

        String email;
        String sessionId;
        try {
            email = jwtUtil.extractUsername(token);
            sessionId = jwtUtil.extractTokenId(token);
        } catch (Exception ignored) {
            return message;
        }
        if (email == null || email.isBlank() || sessionId == null || sessionId.isBlank()) {
            return message;
        }

        User dbUser = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (dbUser == null || dbUser.getId() == null) {
            return message;
        }
        if (!loginSessionService.isActiveSession(dbUser.getId(), sessionId)) {
            return message;
        }

        UserDetails user = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        accessor.setUser(authentication);
        try {
            loginSessionService.touch(sessionId);
        } catch (Exception ignored) {
            // never block WS connect on session touch failures
        }
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String bearer = firstHeader(accessor, "Authorization");
        if (bearer == null || bearer.isBlank()) {
            bearer = firstHeader(accessor, "authorization");
        }
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }

        String token = firstHeader(accessor, "token");
        if (token == null || token.isBlank()) {
            token = firstHeader(accessor, "access_token");
        }
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        return null;
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
