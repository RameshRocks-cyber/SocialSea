package com.socialsea.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mediaUrl;
    private boolean reel;
    private boolean approved = false;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String videoSettings;

    @Column(columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(length = 64)
    private String mediaFingerprint;

    @Column(length = 40)
    private String mediaType;

    private Long mediaSizeBytes;

    @Column(length = 255)
    private String originalFileName;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Post() {}

    public Post(String mediaUrl, boolean reel, User user) {
        this.mediaUrl = mediaUrl;
        this.reel = reel;
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public boolean isReel() { return reel; }
    public void setReel(boolean reel) { this.reel = reel; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVideoSettings() { return videoSettings; }
    public void setVideoSettings(String videoSettings) { this.videoSettings = videoSettings; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public String getMediaFingerprint() { return mediaFingerprint; }
    public void setMediaFingerprint(String mediaFingerprint) { this.mediaFingerprint = mediaFingerprint; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Long getMediaSizeBytes() { return mediaSizeBytes; }
    public void setMediaSizeBytes(Long mediaSizeBytes) { this.mediaSizeBytes = mediaSizeBytes; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
