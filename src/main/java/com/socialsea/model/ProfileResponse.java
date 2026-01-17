package com.socialsea.model;

public class ProfileResponse {
    private String username;
    private long followers;
    private long following;

    public ProfileResponse(String username, long followers, long following) {
        this.username = username;
        this.followers = followers;
        this.following = following;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public long getFollowers() { return followers; }
    public void setFollowers(long followers) { this.followers = followers; }

    public long getFollowing() { return following; }
    public void setFollowing(long following) { this.following = following; }
}
