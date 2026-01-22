package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postId;          // For regular posts

    private Long anonymousPostId; // For anonymous posts (Public Report)

    private String type;

    private String reason;
    @Column(nullable = false)
    private boolean resolved = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    private User reporter;

    // ✅ REQUIRED NO-ARGS CONSTRUCTOR
    public Report() {}

    // ✅ GETTERS
    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAnonymousPostId() {
        return anonymousPostId;
    }

    public String getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public User getReporter() {
        return reporter;
    }

    public boolean isResolved() {
        return resolved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ✅ SETTERS (THIS FIXES YOUR ERROR)
    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public void setAnonymousPostId(Long anonymousPostId) {
        this.anonymousPostId = anonymousPostId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setReporter(User reporter) {
        this.reporter = reporter;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
