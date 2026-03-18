package com.socialsea.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class EmergencyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reporterEmail;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double accuracyMeters;
    private Integer radiusMeters = 100;
    private boolean frontCameraEnabled;
    private boolean backCameraEnabled;
    private boolean active = true;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime endedAt;
    private String mediaUrl;
    private Long durationMs;
    private Double currentLatitude;
    private Double currentLongitude;
    private boolean liveAudioActive;
    private boolean liveVideoActive;
    private LocalDateTime lastHeartbeatAt;
    @Lob
    @Column(length = 200000)
    private String lastPreviewFrame;
    private String lastPreviewFrameAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReporterEmail() {
        return reporterEmail;
    }

    public void setReporterEmail(String reporterEmail) {
        this.reporterEmail = reporterEmail;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public Integer getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Integer radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public boolean isFrontCameraEnabled() {
        return frontCameraEnabled;
    }

    public void setFrontCameraEnabled(boolean frontCameraEnabled) {
        this.frontCameraEnabled = frontCameraEnabled;
    }

    public boolean isBackCameraEnabled() {
        return backCameraEnabled;
    }

    public void setBackCameraEnabled(boolean backCameraEnabled) {
        this.backCameraEnabled = backCameraEnabled;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public boolean isLiveAudioActive() {
        return liveAudioActive;
    }

    public void setLiveAudioActive(boolean liveAudioActive) {
        this.liveAudioActive = liveAudioActive;
    }

    public boolean isLiveVideoActive() {
        return liveVideoActive;
    }

    public void setLiveVideoActive(boolean liveVideoActive) {
        this.liveVideoActive = liveVideoActive;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getLastPreviewFrame() {
        return lastPreviewFrame;
    }

    public void setLastPreviewFrame(String lastPreviewFrame) {
        this.lastPreviewFrame = lastPreviewFrame;
    }

    public String getLastPreviewFrameAt() {
        return lastPreviewFrameAt;
    }

    public void setLastPreviewFrameAt(String lastPreviewFrameAt) {
        this.lastPreviewFrameAt = lastPreviewFrameAt;
    }
}
