package com.socialsea.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.socialsea.model.AnonymousPost;
import com.socialsea.model.Post;
import com.socialsea.util.MediaUrlUtils;
import com.socialsea.util.PublicUserPayloads;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicFeedDto {
    private Long id;
    private String content;
    private String contentUrl;
    private String thumbnailUrl;
    private String mediumUrl;
    private String posterUrl;
    private String coverImageUrl;
    private String description;
    private String title;
    private String videoSettings;
    private LocalDateTime createdAt;
    private boolean anonymous;
    private String type;
    private String username;
    private long likeCount;
    private long viewCount;
    private boolean reel;
    private boolean originalReel;
    private boolean video;
    private Long userId;
    private String publicUsername;
    private String userDisplayName;
    private String profilePic;
    private String profilePicUrl;
    private PublicUserDto user;

    public PublicFeedDto(PublicFeedDto other) {
        if (other == null) {
            return;
        }
        this.id = other.id;
        this.content = other.content;
        this.contentUrl = other.contentUrl;
        this.thumbnailUrl = other.thumbnailUrl;
        this.mediumUrl = other.mediumUrl;
        this.posterUrl = other.posterUrl;
        this.coverImageUrl = other.coverImageUrl;
        this.description = other.description;
        this.title = other.title;
        this.videoSettings = other.videoSettings;
        this.createdAt = other.createdAt;
        this.anonymous = other.anonymous;
        this.type = other.type;
        this.username = other.username;
        this.likeCount = other.likeCount;
        this.viewCount = other.viewCount;
        this.reel = other.reel;
        this.originalReel = other.originalReel;
        this.video = other.video;
        this.userId = other.userId;
        this.publicUsername = other.publicUsername;
        this.userDisplayName = other.userDisplayName;
        this.profilePic = other.profilePic;
        this.profilePicUrl = other.profilePicUrl;
        this.user = other.user;
    }

    public static PublicFeedDto fromPost(Post post) {
        PublicFeedDto dto = new PublicFeedDto();
        if (post == null) {
            return dto;
        }
        dto.setId(post.getId());
        dto.setContent(post.getDescription());
        dto.setContentUrl(post.getMediaUrl());
        boolean video = post.isReel() || MediaUrlUtils.isLikelyVideo(dto.getContentUrl());
        dto.setThumbnailUrl(MediaUrlUtils.thumbnailUrl(dto.getContentUrl(), video ? "video" : "image"));
        dto.setMediumUrl(MediaUrlUtils.mediumUrl(dto.getContentUrl(), video ? "video" : "image"));
        dto.setCoverImageUrl(post.getCoverImageUrl());
        dto.setPosterUrl((dto.getCoverImageUrl() != null && !dto.getCoverImageUrl().isBlank())
                ? dto.getCoverImageUrl()
                : dto.getThumbnailUrl());
        dto.setDescription(post.getDescription());
        dto.setTitle(post.getTitle());
        dto.setVideoSettings(post.getVideoSettings());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setAnonymous(false);
        dto.setType(video ? "VIDEO" : "IMAGE");
        dto.setReel(post.isReel());
        dto.setOriginalReel(post.isReel());
        dto.setVideo(video);
        if (post.getUser() != null) {
            PublicUserDto user = PublicUserPayloads.toUserSummary(post.getUser(), post.getUser().getProfilePic());
            dto.setUser(user);
        }
        dto.setLikeCount(0);
        dto.setViewCount(0);
        return dto;
    }

    public static PublicFeedDto fromAnonymous(AnonymousPost post) {
        PublicFeedDto dto = new PublicFeedDto();
        if (post == null) {
            return dto;
        }
        dto.setId(post.getId());
        dto.setContent(post.getDescription());
        dto.setContentUrl(post.getContentUrl());
        dto.setDescription(post.getDescription());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setAnonymous(true);
        String normalizedType = post.getType() == null ? "" : post.getType().trim().toUpperCase();
        boolean video = "VIDEO".equals(normalizedType) || MediaUrlUtils.isLikelyVideo(dto.getContentUrl());
        dto.setType(video ? "VIDEO" : "IMAGE");
        dto.setThumbnailUrl(MediaUrlUtils.thumbnailUrl(dto.getContentUrl(), video ? "video" : "image"));
        dto.setMediumUrl(MediaUrlUtils.mediumUrl(dto.getContentUrl(), video ? "video" : "image"));
        dto.setPosterUrl(dto.getThumbnailUrl());
        dto.setReel(video);
        dto.setOriginalReel(video);
        dto.setVideo(video);
        dto.setUsername("Anonymous Post");
        dto.setLikeCount(post.getLikeCount());
        dto.setViewCount(post.getViewCount());
        return dto;
    }

    public static PublicFeedDto fromEntity(Post post) {
        return fromPost(post);
    }

    public String getMediaUrl() {
        return contentUrl;
    }

    public String getThumbnail() {
        return getThumbnailUrl();
    }

    public String getThumbUrl() {
        return getThumbnailUrl();
    }

    public String getPoster() {
        return getPosterUrl();
    }

    public String getPosterUrl() {
        if (posterUrl != null && !posterUrl.isBlank()) {
            return posterUrl;
        }
        return getThumbnailUrl();
    }

    public String getCoverImage() {
        return getCoverImageUrl();
    }

    public String getName() {
        if (userDisplayName != null && !userDisplayName.isBlank()) {
            return userDisplayName;
        }
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return username;
    }

    public String getUsername() {
        if (username != null && !username.isBlank()) {
            return username;
        }
        if (publicUsername != null && !publicUsername.isBlank()) {
            return publicUsername;
        }
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        if (userDisplayName != null && !userDisplayName.isBlank()) {
            return userDisplayName;
        }
        return null;
    }

    public String getProfilePic() {
        if (profilePic != null && !profilePic.isBlank()) {
            return profilePic;
        }
        if (profilePicUrl != null && !profilePicUrl.isBlank()) {
            return profilePicUrl;
        }
        if (user != null) {
            return user.getProfilePic();
        }
        return null;
    }

    public String getProfilePicUrl() {
        if (profilePicUrl != null && !profilePicUrl.isBlank()) {
            return profilePicUrl;
        }
        if (profilePic != null && !profilePic.isBlank()) {
            return profilePic;
        }
        if (user != null) {
            return user.getProfilePicUrl();
        }
        return null;
    }

    public void setUser(PublicUserDto user) {
        this.user = user;
        if (user == null) {
            return;
        }
        if (user.getId() != null) {
            this.userId = user.getId();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank() && (this.username == null || this.username.isBlank())) {
            this.username = user.getUsername();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank() && (this.publicUsername == null || this.publicUsername.isBlank())) {
            this.publicUsername = user.getUsername();
        }
        if (user.getName() != null && !user.getName().isBlank() && (this.userDisplayName == null || this.userDisplayName.isBlank())) {
            this.userDisplayName = user.getName();
        }
        if (this.profilePic == null || this.profilePic.isBlank()) {
            this.profilePic = user.getProfilePic();
        }
        if (this.profilePicUrl == null || this.profilePicUrl.isBlank()) {
            this.profilePicUrl = user.getProfilePicUrl();
        }
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
        if (this.profilePicUrl == null || this.profilePicUrl.isBlank()) {
            this.profilePicUrl = profilePic;
        }
    }

    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
        if (this.profilePic == null || this.profilePic.isBlank()) {
            this.profilePic = profilePicUrl;
        }
    }

    @JsonProperty("isAnonymous")
    public boolean isAnonymous() {
        return anonymous;
    }

    @JsonProperty("video")
    public boolean isVideo() {
        return video;
    }

    @JsonProperty("isVideo")
    public boolean getIsVideo() {
        return video;
    }
}
