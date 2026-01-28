package com.socialsea.dto;

public class AdminDashboardStatsDto {
    private long totalUsers;
    private long totalPosts;
    private long pendingAnonymousPosts;
    private long reports;

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalPosts() {
        return totalPosts;
    }

    public void setTotalPosts(long totalPosts) {
        this.totalPosts = totalPosts;
    }

    public long getPendingAnonymousPosts() { return pendingAnonymousPosts; }

    public void setPendingAnonymousPosts(long pendingAnonymousPosts) { this.pendingAnonymousPosts = pendingAnonymousPosts; }

    public long getReports() { return reports; }

    public void setReports(long reports) { this.reports = reports; }
}