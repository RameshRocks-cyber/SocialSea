package com.socialsea.dto;

public class StoryDto {
    private Long id;
    private String mediaUrl;
    private String caption;
    private String storyText;
    private String storyStyle;
    private String storyTextStyle;
    private String privacy;
    private String createdAt;
    private String expiresAt;
    private Long userId;
    private String username;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getStoryText() { return storyText; }
    public void setStoryText(String storyText) { this.storyText = storyText; }

    public String getStoryStyle() { return storyStyle; }
    public void setStoryStyle(String storyStyle) { this.storyStyle = storyStyle; }

    public String getStoryTextStyle() { return storyTextStyle; }
    public void setStoryTextStyle(String storyTextStyle) { this.storyTextStyle = storyTextStyle; }

    public String getPrivacy() { return privacy; }
    public void setPrivacy(String privacy) { this.privacy = privacy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
