package com.socialsea.dto;

public class AdminStatsDto {

    private long totalPosts;
    private long approvedPosts;
    private long pendingPosts;

    public AdminStatsDto(long totalPosts, long approvedPosts, long pendingPosts) {
        this.totalPosts = totalPosts;
        this.approvedPosts = approvedPosts;
        this.pendingPosts = pendingPosts;
    }

    public long getTotalPosts() {
        return totalPosts;
    }

    public long getApprovedPosts() {
        return approvedPosts;
    }

    public long getPendingPosts() {
        return pendingPosts;
    }
}

