package com.socialsea.config;

import com.socialsea.security.JwtHandshakeInterceptor;
import com.socialsea.security.UserHandshakeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Objects;
import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final UserHandshakeHandler userHandshakeHandler;
    private final String[] allowedOrigins;
    private final boolean brokerRelayEnabled;
    private final String brokerRelayHost;
    private final int brokerRelayPort;
    private final String brokerRelayUsername;
    private final String brokerRelayPassword;

    public WebSocketConfig(
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            UserHandshakeHandler userHandshakeHandler,
            @Value("${app.security.allowed-origins:*}") String allowedOriginsCsv,
            @Value("${app.websocket.broker-relay.enabled:false}") boolean brokerRelayEnabled,
            @Value("${app.websocket.broker-relay.host:localhost}") String brokerRelayHost,
            @Value("${app.websocket.broker-relay.port:61613}") int brokerRelayPort,
            @Value("${app.websocket.broker-relay.username:}") String brokerRelayUsername,
            @Value("${app.websocket.broker-relay.password:}") String brokerRelayPassword
    ) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.userHandshakeHandler = userHandshakeHandler;
        String[] configuredOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toArray(String[]::new);
        this.allowedOrigins = configuredOrigins.length > 0 ? configuredOrigins : new String[]{"*"};
        this.brokerRelayEnabled = brokerRelayEnabled;
        this.brokerRelayHost = brokerRelayHost;
        this.brokerRelayPort = brokerRelayPort;
        this.brokerRelayUsername = brokerRelayUsername;
        this.brokerRelayPassword = brokerRelayPassword;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        if (brokerRelayEnabled) {
            config.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(brokerRelayHost)
                    .setRelayPort(brokerRelayPort)
                    .setClientLogin(brokerRelayUsername)
                    .setClientPasscode(brokerRelayPassword)
                    .setSystemLogin(brokerRelayUsername)
                    .setSystemPasscode(brokerRelayPassword);
        } else {
            config.enableSimpleBroker("/topic", "/queue");
        }
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(Objects.requireNonNull(userHandshakeHandler))
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
