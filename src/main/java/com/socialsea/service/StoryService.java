package com.socialsea.service;

import com.socialsea.dto.StoryDto;
import com.socialsea.model.Follow;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.StoryCommentRepository;
import com.socialsea.repository.StoryLikeRepository;
import com.socialsea.repository.StoryRepository;
import com.socialsea.repository.StoryViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StoryService {

    private final StoryRepository storyRepo;
    private final FollowRepository followRepo;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryCommentRepository storyCommentRepo;
    private final StoryViewRepository storyViewRepo;

    public StoryService(
            StoryRepository storyRepo,
            FollowRepository followRepo,
            StoryLikeRepository storyLikeRepo,
            StoryCommentRepository storyCommentRepo,
            StoryViewRepository storyViewRepo
    ) {
        this.storyRepo = storyRepo;
        this.followRepo = followRepo;
        this.storyLikeRepo = storyLikeRepo;
        this.storyCommentRepo = storyCommentRepo;
        this.storyViewRepo = storyViewRepo;
    }

    public List<StoryDto> fetchFeed(User viewer) {
        LocalDateTime now = LocalDateTime.now();
        List<Story> active = storyRepo.findActive(now);
        Set<Long> followingIds = collectFollowingIds(viewer);

        List<StoryDto> result = new ArrayList<>();
        for (Story story : active) {
            User owner = story.getUser();
            Long ownerId = owner != null ? owner.getId() : null;
            String privacy = story.getPrivacy() != null ? story.getPrivacy().toLowerCase() : "public";
            boolean isOwner = viewer != null && ownerId != null && ownerId.equals(viewer.getId());
            boolean allow = "public".equals(privacy) || isOwner;
            if (!allow && "followers".equals(privacy) && ownerId != null) {
                allow = followingIds.contains(ownerId);
            }
            if (!allow) continue;
            StoryDto dto = toDto(story);
            if (owner != null) {
                dto.setUserId(ownerId);
                dto.setUsername(owner.getEmail());
            }
            if (storyLikeRepo != null) {
                dto.setLikeCount(storyLikeRepo.countByStory(story));
                if (viewer != null) {
                    dto.setLikedByMe(storyLikeRepo.existsByUserAndStory(viewer, story));
                }
            }
            if (storyCommentRepo != null) {
                dto.setCommentCount(storyCommentRepo.countByStory(story));
            }
            if (storyViewRepo != null) {
                dto.setViewCount(storyViewRepo.countByStory(story));
            }
            result.add(dto);
        }
        return result;
    }

    public Story findById(Long id) {
        if (id == null) return null;
        return storyRepo.findById(id).orElse(null);
    }

    public boolean canViewStory(Story story, User viewer) {
        if (story == null) return false;
        if (story.getExpiresAt() != null && story.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        String privacy = story.getPrivacy() != null ? story.getPrivacy().toLowerCase() : "public";
        if ("public".equals(privacy)) return true;
        if (viewer == null) return false;
        User owner = story.getUser();
        Long ownerId = owner != null ? owner.getId() : null;
        if (ownerId != null && ownerId.equals(viewer.getId())) return true;
        if ("followers".equals(privacy) && owner != null) {
            return followRepo.existsByFollowerAndFollowing(viewer, owner);
        }
        return false;
    }

    private Set<Long> collectFollowingIds(User viewer) {
        Set<Long> followingIds = new HashSet<>();
        if (viewer == null) return followingIds;
        for (Follow f : followRepo.findByFollower(viewer)) {
            if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                followingIds.add(f.getFollowing().getId());
            }
        }
        return followingIds;
    }

    public StoryDto toDto(Story story) {
        StoryDto dto = new StoryDto();
        dto.setId(story.getId());
        dto.setMediaUrl(story.getMediaUrl());
        dto.setCaption(story.getCaption());
        dto.setStoryText(story.getStoryText());
        dto.setStoryStyle(story.getStoryStyle());
        dto.setStoryTextStyle(story.getStoryTextStyle());
        dto.setPrivacy(story.getPrivacy());
        dto.setCreatedAt(story.getCreatedAt() != null ? story.getCreatedAt().toString() : null);
        dto.setExpiresAt(story.getExpiresAt() != null ? story.getExpiresAt().toString() : null);
        return dto;
    }
}
