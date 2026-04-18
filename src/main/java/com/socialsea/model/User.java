package com.socialsea.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private String password;

    private boolean banned = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    private String name;
    private String bio;
    private String profilePic;

    @Column(columnDefinition = "TEXT")
    private String resumeJson;

    private LocalDateTime resumeUpdatedAt;

    @Column(columnDefinition = "boolean default false")
    private boolean profileCompleted = false;

    @Column(columnDefinition = "boolean default false")
    private boolean privateAccount = false;

    @Column(columnDefinition = "boolean default false")
    private boolean trafficAlertsEnabled = false;

    @Column(length = 16)
    private String preferredLanguage = "en";

    @Column(length = 16)
    private String notificationVoice = "male";

    @Column(columnDefinition = "boolean default false")
    private boolean ambulanceDriverApproved = false;

    private Double lastLatitude;
    private Double lastLongitude;
    private LocalDateTime locationUpdatedAt;
    private LocalDateTime presenceUpdatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }

    @JsonIgnore
    public String getResumeJson() {
        return resumeJson;
    }

    public void setResumeJson(String resumeJson) {
        this.resumeJson = resumeJson;
    }

    public LocalDateTime getResumeUpdatedAt() {
        return resumeUpdatedAt;
    }

    public void setResumeUpdatedAt(LocalDateTime resumeUpdatedAt) {
        this.resumeUpdatedAt = resumeUpdatedAt;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
    }

    public boolean isPrivateAccount() {
        return privateAccount;
    }

    public void setPrivateAccount(boolean privateAccount) {
        this.privateAccount = privateAccount;
    }

    public boolean isTrafficAlertsEnabled() {
        return trafficAlertsEnabled;
    }

    public void setTrafficAlertsEnabled(boolean trafficAlertsEnabled) {
        this.trafficAlertsEnabled = trafficAlertsEnabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage == null || preferredLanguage.isBlank() ? "en" : preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getNotificationVoice() {
        return notificationVoice == null || notificationVoice.isBlank() ? "male" : notificationVoice;
    }

    public void setNotificationVoice(String notificationVoice) {
        this.notificationVoice = notificationVoice;
    }

    public boolean isAmbulanceDriverApproved() {
        return ambulanceDriverApproved;
    }

    public void setAmbulanceDriverApproved(boolean ambulanceDriverApproved) {
        this.ambulanceDriverApproved = ambulanceDriverApproved;
    }

    public Double getLastLatitude() {
        return lastLatitude;
    }

    public void setLastLatitude(Double lastLatitude) {
        this.lastLatitude = lastLatitude;
    }

    public Double getLastLongitude() {
        return lastLongitude;
    }

    public void setLastLongitude(Double lastLongitude) {
        this.lastLongitude = lastLongitude;
    }

    public LocalDateTime getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public LocalDateTime getPresenceUpdatedAt() {
        return presenceUpdatedAt;
    }

    public void setPresenceUpdatedAt(LocalDateTime presenceUpdatedAt) {
        this.presenceUpdatedAt = presenceUpdatedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        // role
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));

        // permissions
        role.getPermissions().forEach(p ->
            authorities.add(new SimpleGrantedAuthority(p.name()))
        );

        return authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !banned;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
