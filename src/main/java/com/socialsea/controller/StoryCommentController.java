package com.socialsea.controller;

import com.socialsea.model.Story;
import com.socialsea.model.StoryComment;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
public class StoryCommentController {

    private final StoryService storyService;
    private final StoryCommentRepository storyCommentRepo;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryViewRepository storyViewRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public StoryCommentController(
            StoryService storyService,
            StoryCommentRepository storyCommentRepo,
            StoryLikeRepository storyLikeRepo,
            StoryViewRepository storyViewRepo,
            UserRepository userRepo,
            NotificationService notificationService
    ) {
        this.storyService = storyService;
        this.storyCommentRepo = storyCommentRepo;
        this.storyLikeRepo = storyLikeRepo;
        this.storyViewRepo = storyViewRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<?> comment(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth
    ) {
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

        String raw = body == null ? "" : String.valueOf(body.getOrDefault("text", "")).trim();
        if (raw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Comment text required"));
        }
        if (raw.length() > 600) {
            return ResponseEntity.badRequest().body(Map.of("message", "Comment too long"));
        }

        storyCommentRepo.save(new StoryComment(null, user, story, raw));

        User owner = story.getUser();
        if (owner != null && owner.getEmail() != null
                && !owner.getEmail().equalsIgnoreCase(user.getEmail())) {
            String actor = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : user.getEmail();
            notificationService.notifyUser(
                    owner.getEmail(),
                    actor + " commented on your story [storyId:" + story.getId() + "]"
            );
        }

        long likeCount = storyLikeRepo.countByStory(story);
        long commentCount = storyCommentRepo.countByStory(story);
        long viewCount = storyViewRepo.countByStory(story);
        boolean likedByMe = storyLikeRepo.existsByUserAndStory(user, story);

        return ResponseEntity.ok(Map.of(
                "liked", likedByMe,
                "likeCount", likeCount,
                "commentCount", commentCount,
                "viewCount", viewCount
        ));
    }
}
