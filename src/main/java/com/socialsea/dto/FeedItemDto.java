package com.socialsea.dto;

import com.socialsea.model.AnonymousPost;
import com.socialsea.model.Post;
import com.socialsea.util.MediaUrlUtils;
import com.socialsea.util.PublicUserPayloads;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class FeedItemDto {
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
    private boolean isAnonymous;
    private String type;
    private String username;
    private long likeCount;
    private long viewCount;
    private boolean reel;
    private boolean originalReel;
    private boolean isVideo;
    private Long userId;
    private String publicUsername;
    private String userDisplayName;
    private String profilePic;

    public static FeedItemDto fromPost(Post post) {
        FeedItemDto dto = new FeedItemDto();
        dto.id = post.getId();
        dto.content = post.getDescription();
        dto.contentUrl = post.getMediaUrl();
        boolean video = post.isReel() || MediaUrlUtils.isLikelyVideo(dto.contentUrl);
        dto.thumbnailUrl = MediaUrlUtils.thumbnailUrl(dto.contentUrl, video ? "video" : "image");
        dto.mediumUrl = MediaUrlUtils.mediumUrl(dto.contentUrl, video ? "video" : "image");
        dto.coverImageUrl = post.getCoverImageUrl();
        dto.posterUrl = (dto.coverImageUrl != null && !dto.coverImageUrl.isBlank()) ? dto.coverImageUrl : dto.thumbnailUrl;
        dto.description = post.getDescription();
        dto.title = post.getTitle();
        dto.videoSettings = post.getVideoSettings();
        dto.createdAt = post.getCreatedAt();
        dto.isAnonymous = false;
        dto.type = video ? "VIDEO" : "IMAGE";
        dto.reel = post.isReel();
        dto.originalReel = post.isReel();
        dto.isVideo = video;
        if (post.getUser() != null) {
            dto.userId = post.getUser().getId();
            dto.publicUsername = PublicUserPayloads.publicUsername(post.getUser());
            dto.userDisplayName = PublicUserPayloads.publicDisplayName(post.getUser());
            dto.profilePic = post.getUser().getProfilePic();
            dto.username = dto.publicUsername.isBlank() ? dto.userDisplayName : dto.publicUsername;
        }
        dto.likeCount = 0;
        dto.viewCount = 0;
        return dto;
    }

    public static FeedItemDto fromAnonymous(AnonymousPost post) {
        FeedItemDto dto = new FeedItemDto();
        dto.id = post.getId();
        dto.content = post.getDescription();
        dto.contentUrl = post.getContentUrl();
        dto.description = post.getDescription();
        dto.createdAt = post.getCreatedAt();
        dto.isAnonymous = true;
        String normalizedType = post.getType() == null ? "" : post.getType().trim().toUpperCase(Locale.ROOT);
        boolean video = "VIDEO".equals(normalizedType) || MediaUrlUtils.isLikelyVideo(dto.contentUrl);
        dto.type = video ? "VIDEO" : "IMAGE";
        dto.thumbnailUrl = MediaUrlUtils.thumbnailUrl(dto.contentUrl, video ? "video" : "image");
        dto.mediumUrl = MediaUrlUtils.mediumUrl(dto.contentUrl, video ? "video" : "image");
        dto.posterUrl = dto.thumbnailUrl;
        dto.reel = video;
        dto.originalReel = video;
        dto.isVideo = video;
        dto.username = "Anonymous Post";
        dto.likeCount = post.getLikeCount();
        dto.viewCount = post.getViewCount();
        return dto;
    }

    public static FeedItemDto fromEntity(Post post) {
        return fromPost(post);
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public String getContentUrl() { return contentUrl; }
    public String getMediaUrl() { return contentUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getThumbnail() { return thumbnailUrl; }
    public String getThumbUrl() { return thumbnailUrl; }
    public String getMediumUrl() { return mediumUrl; }
    public String getPosterUrl() { return posterUrl == null || posterUrl.isBlank() ? thumbnailUrl : posterUrl; }
    public String getPoster() { return getPosterUrl(); }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getCoverImage() { return coverImageUrl; }
    public String getDescription() { return description; }
    public String getTitle() { return title; }
    public String getVideoSettings() { return videoSettings; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isAnonymous() { return isAnonymous; }
    public String getType() { return type; }
    public String getUsername() { return username; }
    public Long getUserId() { return userId; }
    public String getName() { return userDisplayName; }
    public String getProfilePic() { return profilePic; }
    public Map<String, Object> getUser() {
        if (userId == null && publicUsername == null && userDisplayName == null && profilePic == null) {
            return null;
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("name", userDisplayName);
        if (publicUsername != null && !publicUsername.isBlank()) {
            user.put("username", publicUsername);
        }
        user.put("profilePic", profilePic);
        return user;
    }
    public long getLikeCount() { return likeCount; }
    public long getViewCount() { return viewCount; }
    public boolean isReel() { return reel; }
    public boolean isOriginalReel() { return originalReel; }
    @JsonProperty("video")
    public boolean isVideo() { return isVideo; }
    @JsonProperty("isVideo")
    public boolean getIsVideo() { return isVideo; }
}
