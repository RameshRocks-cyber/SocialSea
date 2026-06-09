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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServicePrivacyTest {

    @Mock
    private StoryRepository storyRepo;

    @Mock
    private FollowRepository followRepo;

    @Mock
    private StoryLikeRepository storyLikeRepo;

    @Mock
    private StoryCommentRepository storyCommentRepo;

    @Mock
    private StoryViewRepository storyViewRepo;

    @InjectMocks
    private StoryService storyService;

    @Test
    void privateAccountStoriesStayHiddenFromNonFollowers() {
        User owner = user(2L, "owner@example.com", "Owner", true);
        User viewer = user(1L, "viewer@example.com", "Viewer", false);
        Story story = story(11L, owner, "public");

        when(storyRepo.findActive(any())).thenReturn(List.of(story));
        when(followRepo.findByFollower(viewer)).thenReturn(List.of());
        when(followRepo.existsByFollowerAndFollowing(viewer, owner)).thenReturn(false);

        assertFalse(storyService.canViewStory(story, viewer));

        List<StoryDto> feed = storyService.fetchFeed(viewer);
        assertTrue(feed.isEmpty());
    }

    @Test
    void followersCanSeePrivateAccountStories() {
        User owner = user(2L, "owner@example.com", "Owner", true);
        User viewer = user(1L, "viewer@example.com", "Viewer", false);
        Story story = story(11L, owner, "public");

        when(storyRepo.findActive(any())).thenReturn(List.of(story));
        when(followRepo.findByFollower(viewer)).thenReturn(List.of(new Follow(null, viewer, owner)));
        when(followRepo.existsByFollowerAndFollowing(viewer, owner)).thenReturn(true);

        assertTrue(storyService.canViewStory(story, viewer));

        List<StoryDto> feed = storyService.fetchFeed(viewer);
        assertEquals(1, feed.size());
        assertEquals("Owner", feed.get(0).getUsername());
    }

    private static User user(Long id, String email, String name, boolean privateAccount) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPrivateAccount(privateAccount);
        return user;
    }

    private static Story story(Long id, User owner, String privacy) {
        Story story = new Story();
        story.setId(id);
        story.setUser(owner);
        story.setPrivacy(privacy);
        story.setCreatedAt(LocalDateTime.now());
        return story;
    }
}
