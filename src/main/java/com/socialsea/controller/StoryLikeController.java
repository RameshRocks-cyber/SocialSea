package com.socialsea.controller;

import com.socialsea.model.Story;
import com.socialsea.model.StoryLike;
import com.socialsea.model.User;
import com.socialsea.repository.StoryCommentRepository;
import com.socialsea.repository.StoryLikeRepository;
import com.socialsea.repository.StoryViewRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import com.socialsea.service.StoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stories")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://43.205.213.14:5173"
})
public class StoryLikeController {

    private final StoryService storyService;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryCommentRepository storyCommentRepo;
    private final StoryViewRepository storyViewRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public StoryLikeController(
            StoryService storyService,
            StoryLikeRepository storyLikeRepo,
            StoryCommentRepository storyCommentRepo,
            StoryViewRepository storyViewRepo,
            UserRepository userRepo,
            NotificationService notificationService
    ) {
        this.storyService = storyService;
        this.storyLikeRepo = storyLikeRepo;
        this.storyCommentRepo = storyCommentRepo;
        this.storyViewRepo = storyViewRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    private Map<String, Object> buildStats(Story story, boolean liked) {
        long likeCount = storyLikeRepo.countByStory(story);
        long commentCount = storyCommentRepo.countByStory(story);
        long viewCount = storyViewRepo.countByStory(story);
        return Map.of(
                "liked", liked,
                "likeCount", likeCount,
                "commentCount", commentCount,
                "viewCount", viewCount
        );
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> like(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        if (!storyService.canViewStory(story, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        if (storyLikeRepo.existsByUserAndStory(user, story)) {
            return ResponseEntity.ok(buildStats(story, true));
        }

        storyLikeRepo.save(new StoryLike(null, user, story));

        User owner = story.getUser();
        if (owner != null && owner.getEmail() != null
                && !owner.getEmail().equalsIgnoreCase(user.getEmail())) {
            String actor = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : user.getEmail();
            notificationService.notifyUser(
                    owner.getEmail(),
                    actor + " liked your story"
            );
        }

        return ResponseEntity.ok(buildStats(story, true));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<?> unlike(@PathVariable("id") Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        if (!storyService.canViewStory(story, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        if (storyLikeRepo.existsByUserAndStory(user, story)) {
            storyLikeRepo.deleteByUserAndStory(user, story);
        }
        return ResponseEntity.ok(buildStats(story, false));
    }
}
