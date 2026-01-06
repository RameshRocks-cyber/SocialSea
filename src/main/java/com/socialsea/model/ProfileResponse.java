package com.socialsea.model;

import lombok.*;

@Getter @Setter
@AllArgsConstructor
public class ProfileResponse {
    private String username;
    private long followers;
    private long following;
}
