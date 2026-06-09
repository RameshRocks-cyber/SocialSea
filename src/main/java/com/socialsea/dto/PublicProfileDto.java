package com.socialsea.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicProfileDto extends PublicUserDto {
    private String email;
    private String bio;
    private String coverUrl;
    private String coverPhoto;
    private String coverPhotoUrl;
    private String profileCoverUrl;
    private boolean profileCompleted;
    private long followers;
    private long following;
    private long postsCount;
    private long postCount;
    private long totalPosts;
    private long videosCount;
    private long videoCount;
    private long totalVideos;
    private boolean privateAccount;
    private boolean longVideosEnabled;
    private boolean trafficAlertsEnabled;
    private String preferredLanguage;
    private String notificationVoice;
    private boolean ambulanceDriverApproved;
    private boolean canViewContent;
    private String followStatus;
    private boolean followingUser;

    public String getCoverUrl() {
        return firstNonBlank(coverUrl, coverPhotoUrl, coverPhoto, profileCoverUrl);
    }

    public String getCoverPhoto() {
        return firstNonBlank(coverPhoto, coverPhotoUrl, coverUrl, profileCoverUrl);
    }

    public String getCoverPhotoUrl() {
        return firstNonBlank(coverPhotoUrl, coverPhoto, coverUrl, profileCoverUrl);
    }

    public String getProfileCoverUrl() {
        return firstNonBlank(profileCoverUrl, coverPhotoUrl, coverPhoto, coverUrl);
    }

    @JsonProperty("isFollowing")
    public boolean isFollowing() {
        return followingUser;
    }

    public void setCoverImage(String coverImageUrl) {
        this.coverUrl = coverImageUrl;
        this.coverPhoto = coverImageUrl;
        this.coverPhotoUrl = coverImageUrl;
        this.profileCoverUrl = coverImageUrl;
    }

    public void setCoverPhotoUrl(String coverPhotoUrl) {
        this.coverPhotoUrl = coverPhotoUrl;
        if (coverUrl == null || coverUrl.isBlank()) {
            coverUrl = coverPhotoUrl;
        }
        if (coverPhoto == null || coverPhoto.isBlank()) {
            coverPhoto = coverPhotoUrl;
        }
        if (profileCoverUrl == null || profileCoverUrl.isBlank()) {
            profileCoverUrl = coverPhotoUrl;
        }
    }

    public void setFollowingUser(boolean followingUser) {
        this.followingUser = followingUser;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
