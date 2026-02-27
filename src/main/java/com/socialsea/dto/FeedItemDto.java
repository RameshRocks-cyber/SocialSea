package com.socialsea.dto;

import com.socialsea.model.AnonymousPost;
import com.socialsea.model.Post;
import java.time.LocalDateTime;

public class FeedItemDto {
    private Long id;
    private String content;
    private String contentUrl;
    private String description;
    private LocalDateTime createdAt;
    private boolean isAnonymous;
    private String type;
    private String username;

    public static FeedItemDto fromPost(Post post) {
        FeedItemDto dto = new FeedItemDto();
        dto.id = post.getId();
        dto.content = null;
        dto.contentUrl = post.getMediaUrl();
        dto.createdAt = post.getCreatedAt();
        dto.isAnonymous = false;
        dto.type = post.isReel() ? "VIDEO" : "IMAGE";
        if (post.getUser() != null) {
            dto.username = post.getUser().getEmail();
        }
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
        dto.type = post.getType();
        return dto;
    }

    public static FeedItemDto fromEntity(Post post) {
        return fromPost(post);
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public String getContentUrl() { return contentUrl; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isAnonymous() { return isAnonymous; }
    public String getType() { return type; }
    public String getUsername() { return username; }
}
