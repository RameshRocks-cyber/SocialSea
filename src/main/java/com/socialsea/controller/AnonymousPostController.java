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
        // TODO: Inject CloudinaryService and upload file
        // String url = cloudinaryService.upload(file);
        String url = "https://placehold.co/600x400?text=Uploaded+Image"; // Placeholder

        AnonymousPost post = new AnonymousPost();
        post.setUrl(url);
        post.setCaption(caption);
        post.setCreatedAt(LocalDateTime.now());
        post.setApproved(false); // Requires admin approval

        anonymousPostRepo.save(post);

        return ResponseEntity.ok(Map.of("message", "Upload successful! Waiting for approval."));
    }
}