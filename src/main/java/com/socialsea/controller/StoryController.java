package com.socialsea.controller;

import com.socialsea.dto.StoryDto;
import com.socialsea.model.User;
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

import java.util.List;
import java.util.Map;
import java.net.URI;

@RestController
@RequestMapping("/api/stories")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://43.205.213.14:5173"
})
public class StoryController {

    private final StoryService storyService;
    private final UserRepository userRepo;

    public StoryController(StoryService storyService, UserRepository userRepo) {
        this.storyService = storyService;
        this.userRepo = userRepo;
    }

    @GetMapping("/feed")
    public ResponseEntity<?> feed(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        User viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired"));
        }
        List<StoryDto> stories = storyService.fetchFeed(viewer);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<?> media(@PathVariable("id") Long id, Authentication auth) {
        if (id == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Missing story id"));
        }
        User viewer = null;
        if (auth != null && auth.isAuthenticated()) {
            viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        }
        var story = storyService.findById(id);
        if (story == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story not found"));
        }
        if (!storyService.canViewStory(story, viewer)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        String mediaUrl = story.getMediaUrl();
        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Story media missing"));
        }
        String target = mediaUrl.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        }
        if (!target.startsWith("/")) {
            target = "/" + target;
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
