package com.socialsea.dto;

import com.socialsea.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private User user;

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
}
