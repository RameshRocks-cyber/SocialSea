package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mediaUrl;
    private String caption;
    private String storyText;
    private String storyStyle;

    @Column(columnDefinition = "TEXT")
    private String storyTextStyle;

    private String privacy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime expiresAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
