package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
@CrossOrigin("https://socialsea.netlify.app")
public class AnonymousPostController {

    private final AnonymousPostRepository anonymousPostRepo;

    public AnonymousPostController(AnonymousPostRepository anonymousPostRepo) {
        this.anonymousPostRepo = anonymousPostRepo;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("caption") String caption
    ) {
        try {
            // TODO: Inject CloudinaryService and upload file
            // String fileUrl = cloudinaryService.upload(file);
            String fileUrl = "https://placehold.co/600x400?text=Uploaded+Image"; // Placeholder

            AnonymousPost post = new AnonymousPost();
            post.setContentUrl(fileUrl);
            post.setDescription(caption);
            post.setType("IMAGE"); // Defaulting to IMAGE for the placeholder
            post.setApproved(false);

            anonymousPostRepo.save(post);

            return ResponseEntity.ok(Map.of("message", "Anonymous post submitted for review"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}