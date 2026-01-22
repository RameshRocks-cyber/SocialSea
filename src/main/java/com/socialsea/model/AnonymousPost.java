package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ANONYMOUS_POST")
public class AnonymousPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String contentUrl;   // video / image / text
    private String type;         // VIDEO | IMAGE | TEXT
    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();

    // 🔐 INTERNAL ONLY (never exposed)
    private String uploaderEmail;

    private boolean approved = false;
    private boolean rejected = false;

    @Column(length = 500)
    private String rejectionReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUploaderEmail() {
        return uploaderEmail;
    }

    public void setUploaderEmail(String uploaderEmail) {
        this.uploaderEmail = uploaderEmail;
    }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isRejected() { return rejected; }
    public void setRejected(boolean rejected) { this.rejected = rejected; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
