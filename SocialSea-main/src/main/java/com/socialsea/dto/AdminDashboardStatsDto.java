package com.socialsea.dto;

public class AdminDashboardStatsDto {
    private long totalUsers;
    private long totalPosts;
    private long pendingAnonymousPosts;
    private long approvedAnonymousPosts;
    private long rejectedAnonymousPosts;
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

    public long getPendingAnonymousPosts() {
        return pendingAnonymousPosts;
    }

    public void setPendingAnonymousPosts(long pendingAnonymousPosts) {
        this.pendingAnonymousPosts = pendingAnonymousPosts;
    }

    public long getApprovedAnonymousPosts() {
        return approvedAnonymousPosts;
    }

    public void setApprovedAnonymousPosts(long approvedAnonymousPosts) {
        this.approvedAnonymousPosts = approvedAnonymousPosts;
    }

    public long getRejectedAnonymousPosts() {
        return rejectedAnonymousPosts;
    }

    public void setRejectedAnonymousPosts(long rejectedAnonymousPosts) {
        this.rejectedAnonymousPosts = rejectedAnonymousPosts;
    }

    public long getReports() {
        return reports;
    }

    public void setReports(long reports) {
        this.reports = reports;
    }
}
