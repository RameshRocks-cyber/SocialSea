package com.socialsea.controller;

import com.socialsea.model.Comment;
import com.socialsea.model.Post;
import com.socialsea.model.User;
import com.socialsea.repository.CommentRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import com.socialsea.util.MediaUrlUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
public class CommentController {

    private final CommentRepository commentRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final NotificationService notificationService;

    public CommentController(
        CommentRepository commentRepo,
        UserRepository userRepo,
        PostRepository postRepo,
        NotificationService notificationService
    ) {
        this.commentRepo = commentRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.notificationService = notificationService;
    }

    @PostMapping("/{postId}")
    public Comment add(
        @PathVariable long postId,
        @RequestBody String text,
        Authentication auth
    ) {
        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        Post post = postRepo.findById(postId).orElseThrow();

        Comment c = new Comment();
        c.setText(text == null ? "" : text.trim());
        c.setUser(user);
        c.setPost(post);

        Comment saved = commentRepo.save(c);

        // Always notify post owner so notifications also appear in single-account testing.
        if (post.getUser() != null && post.getUser().getEmail() != null) {
            String actor = (user.getName() != null && !user.getName().isBlank())
                ? user.getName()
                : user.getEmail();
            String targetLabel = post.isReel() ? "reel" : (MediaUrlUtils.isLikelyVideo(post.getMediaUrl()) ? "video" : "post");
            notificationService.notifyUser(
                post.getUser().getEmail(),
                actor + " commented on your " + targetLabel + " [postId:" + post.getId() + "]"
            );
        }

        return saved;
    }

    @GetMapping("/{postId}")
    public List<Comment> list(@PathVariable long postId) {
        Post post = postRepo.findById(postId).orElseThrow();
        return commentRepo.findByPost(post);
    }
}

