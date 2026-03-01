package com.socialsea.dto;

public class AdminStatsDto {
    public long users;
    public long posts;
    public long likes;
    public long pendingAnonymous;
    public long unresolvedReports;

    // Backward-compatible aliases used by different frontend variants
    public long totalUsers;
    public long totalPosts;
    public long pendingAnonymousPosts;
    public long reports;
}
