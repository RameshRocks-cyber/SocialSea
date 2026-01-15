package com.socialsea.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "banned_ips")
public class BannedIP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String ipAddress;

    private String reason;

    private LocalDateTime bannedAt = LocalDateTime.now();

    // Getters & setters
    public Long getId() { return id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getBannedAt() { return bannedAt; }
}