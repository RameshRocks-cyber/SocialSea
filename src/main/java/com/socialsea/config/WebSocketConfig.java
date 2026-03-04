package com.socialsea.config;

import com.socialsea.security.JwtHandshakeInterceptor;
import com.socialsea.security.UserHandshakeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Objects;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor, UserHandshakeHandler userHandshakeHandler) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.userHandshakeHandler = userHandshakeHandler;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(Objects.requireNonNull(userHandshakeHandler))
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
