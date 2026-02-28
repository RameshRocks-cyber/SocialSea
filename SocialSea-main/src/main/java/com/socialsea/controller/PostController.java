package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final CloudinaryService cloudinaryService;

    public PostController(
        PostRepository postRepo,
        UserRepository userRepo,
        CloudinaryService cloudinaryService
    ) {
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        Authentication auth
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is required");
        }

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login required");
        }

        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        String url = cloudinaryService.upload(file);

        Post post = new Post();
        post.setMediaUrl(url);
        post.setReel(isVideo(file));
        post.setApproved(true);
        post.setUser(user);

        return ResponseEntity.ok(postRepo.save(post));
    }

    private boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("video");
    }
}
