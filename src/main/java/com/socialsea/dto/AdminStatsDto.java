package com.socialsea.dto;

import java.io.Serializable;

public class AdminStatsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    public long users;
    public long posts;
    public long videos;
    public long likes;
    public long pendingAnonymous;
    public long unresolvedReports;

    // Backward-compatible aliases used by different frontend variants
    public long totalUsers;
    public long totalPosts;
    public long totalVideos;
    public long pendingAnonymousPosts;
    public long reports;
}
