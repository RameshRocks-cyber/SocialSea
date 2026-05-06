package com.socialsea.controller;

import com.socialsea.model.Story;
import com.socialsea.model.StoryView;
import com.socialsea.model.User;
import com.socialsea.repository.StoryCommentRepository;
import com.socialsea.repository.StoryLikeRepository;
import com.socialsea.repository.StoryViewRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.StoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
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
        "http://127.0.0.1:5173",
        "http://43.205.229.211:5173"
})
public class StoryViewController {

    private final StoryService storyService;
    private final StoryViewRepository storyViewRepo;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryCommentRepository storyCommentRepo;
    private final UserRepository userRepo;

    public StoryViewController(
            StoryService storyService,
            StoryViewRepository storyViewRepo,
            StoryLikeRepository storyLikeRepo,
            StoryCommentRepository storyCommentRepo,
            UserRepository userRepo
    ) {
        this.storyService = storyService;
        this.storyViewRepo = storyViewRepo;
        this.storyLikeRepo = storyLikeRepo;
        this.storyCommentRepo = storyCommentRepo;
        this.userRepo = userRepo;
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<?> view(@PathVariable("id") Long id, Authentication auth) {
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

        User owner = story.getUser();
        boolean isOwner = owner != null && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(user.getEmail());
        if (!isOwner && !storyViewRepo.existsByUserAndStory(user, story)) {
            storyViewRepo.save(new StoryView(null, user, story));
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
