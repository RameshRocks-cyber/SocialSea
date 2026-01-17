package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class AnonymousPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String videoUrl;

    private LocalDateTime createdAt = LocalDateTime.now();

    public AnonymousPost() {}

    public AnonymousPost(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
