package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/anonymous")
@CrossOrigin(origins = "*")
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
        @RequestParam("caption") String caption
    ) {
        String url = cloudinaryService.upload(file);

        AnonymousPost post = new AnonymousPost();
        post.setContentUrl(url);
        post.setDescription(caption);
        post.setType(file.getContentType().startsWith("video") ? "VIDEO" : "IMAGE");
        post.setApproved(false);

        repo.save(post);

        return ResponseEntity.ok(
            Map.of("message", "Anonymous post submitted for review")
        );
    }
}