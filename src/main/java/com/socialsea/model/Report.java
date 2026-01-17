package com.socialsea.model;

import jakarta.persistence.*;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postId;

    private String type;

    private String reason;

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

    public String getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public User getReporter() {
        return reporter;
    }

    // ✅ SETTERS (THIS FIXES YOUR ERROR)
    public void setPostId(Long postId) {
        this.postId = postId;
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
}
