package com.socialsea.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.socialsea.model.User;
import com.socialsea.util.PublicUserPayloads;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicUserDto implements Serializable {
    private Long id;
    private String name;
    private String username;
    private String profilePic;
    private String profilePicUrl;

    public static PublicUserDto from(User user, String profilePicUrl) {
        PublicUserDto dto = new PublicUserDto();
        if (user == null) {
            return dto;
        }

        dto.setId(user.getId());
        dto.setName(PublicUserPayloads.publicDisplayName(user));

        String username = PublicUserPayloads.publicUsername(user);
        if (!username.isBlank()) {
            dto.setUsername(username);
        }

        if (profilePicUrl != null && !profilePicUrl.isBlank()) {
            dto.setProfilePic(profilePicUrl);
            dto.setProfilePicUrl(profilePicUrl);
        }

        return dto;
    }

    public Long getUserId() {
        return id;
    }

    public String getDisplayName() {
        return name;
    }

    public String getProfilePic() {
        return firstNonBlank(profilePic, profilePicUrl);
    }

    public String getProfilePicUrl() {
        return firstNonBlank(profilePicUrl, profilePic);
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
        if (profilePicUrl == null || profilePicUrl.isBlank()) {
            this.profilePicUrl = profilePic;
        }
    }

    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
        if (profilePic == null || profilePic.isBlank()) {
            this.profilePic = profilePicUrl;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return null;
    }
}
