package com.socialsea.controller;

import com.socialsea.dto.FeedItemDto;
import com.socialsea.model.Post;
import com.socialsea.model.SavedPost;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.SavedPostRepository;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "http://localhost:5173",
    "http://localhost:3000"
})
public class SavedPostController {

    private final SavedPostRepository savedPostRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;

    @PostMapping("/{postId}")
    public ResponseEntity<?> toggleSave(@PathVariable Long postId, Authentication auth) {
        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        Post post = postRepo.findById(postId).orElseThrow();

        if (savedPostRepo.existsByUserAndPost(user, post)) {
            SavedPost saved = savedPostRepo.findByUserAndPost(user, post).orElseThrow();
            savedPostRepo.delete(saved);
            return ResponseEntity.ok(Map.of("message", "Unsaved", "isSaved", false));
        } else {
            SavedPost saved = new SavedPost();
            saved.setUser(user);
            saved.setPost(post);
            savedPostRepo.save(saved);
            return ResponseEntity.ok(Map.of("message", "Saved", "isSaved", true));
        }
    }

    @GetMapping
    public ResponseEntity<?> getSavedPosts(Authentication auth) {
        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        List<FeedItemDto> savedPosts = savedPostRepo.findByUserOrderBySavedAtDesc(user)
                .stream()
                .map(saved -> FeedItemDto.fromEntity(saved.getPost()))
                .toList();
        return ResponseEntity.ok(savedPosts);
    }
}