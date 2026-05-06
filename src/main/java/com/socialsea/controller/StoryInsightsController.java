package com.socialsea.controller;

import com.socialsea.model.Story;
import com.socialsea.model.StoryComment;
import com.socialsea.model.StoryLike;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
public class StoryInsightsController {
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final StoryService storyService;
    private final StoryLikeRepository storyLikeRepo;
    private final StoryCommentRepository storyCommentRepo;
    private final StoryViewRepository storyViewRepo;
    private final UserRepository userRepo;

    public StoryInsightsController(
            StoryService storyService,
            StoryLikeRepository storyLikeRepo,
            StoryCommentRepository storyCommentRepo,
            StoryViewRepository storyViewRepo,
            UserRepository userRepo
    ) {
        this.storyService = storyService;
        this.storyLikeRepo = storyLikeRepo;
        this.storyCommentRepo = storyCommentRepo;
        this.storyViewRepo = storyViewRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/{id}/likes")
    public ResponseEntity<?> likes(@PathVariable("id") Long id, Authentication auth) {
        ResponseEntity<?> guard = guardOwner(id, auth);
        if (guard != null) return guard;

        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (StoryLike like : storyLikeRepo.findByStoryOrderByIdDesc(story)) {
            items.add(toUserItem(like.getUser(), null, null));
        }
        return ResponseEntity.ok(Map.of("count", items.size(), "items", items));
    }

    @GetMapping("/{id}/views")
    public ResponseEntity<?> views(@PathVariable("id") Long id, Authentication auth) {
        ResponseEntity<?> guard = guardOwner(id, auth);
        if (guard != null) return guard;

        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (StoryView view : storyViewRepo.findByStoryOrderByCreatedAtDesc(story)) {
            items.add(toUserItem(view.getUser(), "viewedAt", view.getCreatedAt()));
        }
        return ResponseEntity.ok(Map.of("count", items.size(), "items", items));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> comments(@PathVariable("id") Long id, Authentication auth) {
        ResponseEntity<?> guard = guardOwner(id, auth);
        if (guard != null) return guard;

        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (StoryComment comment : storyCommentRepo.findByStoryOrderByCreatedAtDesc(story)) {
            String text = comment.getText() == null ? "" : comment.getText().trim();
            items.add(toUserItem(comment.getUser(), "commentedAt", comment.getCreatedAt(), text));
        }
        return ResponseEntity.ok(Map.of("count", items.size(), "items", items));
    }

    private ResponseEntity<?> guardOwner(Long id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        User me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        Story story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        User owner = story.getUser();
        Long ownerId = owner != null ? owner.getId() : null;
        if (ownerId == null || !ownerId.equals(me.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only story owner can view insights"));
        }
        return null;
    }

    private Map<String, Object> toUserItem(User user, String atKey, LocalDateTime at) {
        return toUserItem(user, atKey, at, null);
    }

    private Map<String, Object> toUserItem(User user, String atKey, LocalDateTime at, String commentText) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (user != null) {
            item.put("userId", user.getId());
            item.put("email", user.getEmail());
            String displayName = (user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : user.getEmail();
            item.put("name", displayName);
            item.put("username", displayName);
            item.put("profilePic", user.getProfilePic());
        } else {
            item.put("userId", null);
            item.put("email", "");
            item.put("name", "Unknown");
            item.put("username", "Unknown");
            item.put("profilePic", null);
        }
        if (atKey != null) {
            item.put(atKey, formatIsoOffset(at));
        }
        if (commentText != null) {
            item.put("text", commentText);
        }
        return item;
    }

    private String formatIsoOffset(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(ZoneId.systemDefault()).format(ISO_OFFSET);
    }
}
