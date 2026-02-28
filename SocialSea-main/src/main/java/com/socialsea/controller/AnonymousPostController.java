package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/anonymous")
public class AnonymousPostController {

    private final AnonymousPostRepository repo;
    private final CloudinaryService cloudinaryService;

    public AnonymousPostController(
        AnonymousPostRepository repo,
        CloudinaryService cloudinaryService
    ) {
        this.repo = repo;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "description", required = false) String description
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }
        if (description == null) {
            description = "";
        }
        String url = cloudinaryService.upload(file);

        AnonymousPost post = new AnonymousPost();
        post.setContentUrl(url);
        post.setDescription(description);
        String contentType = file.getContentType();
        post.setType(contentType != null && contentType.startsWith("video") ? "VIDEO" : "IMAGE");
        post.setApproved(false);

        AnonymousPost saved = repo.save(post);

        return ResponseEntity.ok(saved);
    }
}
