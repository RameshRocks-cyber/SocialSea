package com.socialsea.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.socialsea.model.User;
import com.socialsea.util.PublicUserPayloads;
import lombok.Data;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AuthResponse {
    private String token;
    // Refresh tokens stay in HttpOnly cookies and never go back in JSON.
    @JsonIgnore
    @ToString.Exclude
    private String refreshToken;
    private PublicUserDto user;
    private String deviceId;
    private String role;

    public AuthResponse(String token, String refreshToken, User user) {
        this(token, refreshToken, user, null);
    }

    public AuthResponse(String token, String refreshToken, User user, String deviceId) {
        this(token, refreshToken, PublicUserPayloads.toUserSummary(user, user != null ? user.getProfilePic() : null), deviceId);
        if (user != null && user.getRole() != null) {
            this.role = user.getRole().name();
        }
    }

    public AuthResponse(String token, String refreshToken, PublicUserDto user) {
        this(token, refreshToken, user, null);
    }

    public AuthResponse(String token, String refreshToken, PublicUserDto user, String deviceId) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.user = user;
        this.deviceId = deviceId;
    }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
}
