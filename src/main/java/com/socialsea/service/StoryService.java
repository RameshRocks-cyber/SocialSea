package com.socialsea.service;

import com.socialsea.dto.StoryDto;
import com.socialsea.model.Follow;
import com.socialsea.model.Story;
import com.socialsea.model.User;
import com.socialsea.repository.FollowRepository;
import com.socialsea.repository.StoryRepository;
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

    public StoryService(StoryRepository storyRepo, FollowRepository followRepo) {
        this.storyRepo = storyRepo;
        this.followRepo = followRepo;
    }

    public List<StoryDto> fetchFeed(User viewer) {
        LocalDateTime now = LocalDateTime.now();
        List<Story> active = storyRepo.findActive(now);
        Set<Long> followingIds = new HashSet<>();
        if (viewer != null) {
            for (Follow f : followRepo.findByFollower(viewer)) {
                if (f.getFollowing() != null && f.getFollowing().getId() != null) {
                    followingIds.add(f.getFollowing().getId());
                }
            }
        }

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
            result.add(dto);
        }
        return result;
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
