package com.socialsea.dto;

public class LiveBroadcastDto {
    private Long id;
    private String title;
    private String hostName;
    private String language;
    private String filter;
    private String screenRatio;
    private boolean screenSharing;
    private Long startedAt;
    private Long expiresAt;
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public String getScreenRatio() { return screenRatio; }
    public void setScreenRatio(String screenRatio) { this.screenRatio = screenRatio; }

    public boolean isScreenSharing() { return screenSharing; }
    public void setScreenSharing(boolean screenSharing) { this.screenSharing = screenSharing; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
