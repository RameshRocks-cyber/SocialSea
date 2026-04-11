package com.socialsea.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.lang.NonNull;

import java.security.Principal;
import java.util.Map;

@Component
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            @NonNull ServerHttpRequest request,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        UserDetails user = (UserDetails) attributes.get("user");
        if (user == null) {
            return super.determineUser(request, wsHandler, attributes);
        }
        return user::getUsername;
    }
}
