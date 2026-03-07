package com.socialsea.dto;

import com.socialsea.model.AnonymousPost;

import java.time.LocalDateTime;

public class FeedItemDto {
    private Long id;
    private String mediaUrl;
    private String type;
    private String description;
    private LocalDateTime createdAt;
    private long likeCount;
    private long viewCount;

    public FeedItemDto(Long id, String mediaUrl, String type, String description, LocalDateTime createdAt, long likeCount, long viewCount) {
        this.id = id;
        this.mediaUrl = mediaUrl;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
    }

    public static FeedItemDto fromAnonymous(AnonymousPost post) {
        return new FeedItemDto(
            post.getId(),
            post.getContentUrl(),
            post.getType(),
            post.getDescription(),
            post.getCreatedAt(),
            post.getLikeCount(),
            post.getViewCount()
        );
    }

    public Long getId() {
        return id;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }
}
