package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import com.socialsea.service.NotificationService;
import com.socialsea.util.MediaUrlUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/likes")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.229.211:5173"})
public class LikeController {

    private final LikeRepository likeRepo;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final NotificationService notificationService;

    public LikeController(
        LikeRepository likeRepo,
        UserRepository userRepo,
        PostRepository postRepo,
        NotificationService notificationService
    ) {
        this.likeRepo = likeRepo;
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.notificationService = notificationService;
    }

    @PostMapping("/{postId}")
    public String like(@PathVariable long postId, Authentication auth) {

        User user = userRepo.findByEmail(auth.getName()).orElseThrow();
        Post post = postRepo.findById(postId).orElseThrow();

        if (likeRepo.existsByUserAndPost(user, post)) {
            return "Already liked";
        }

        Like like = new Like(null, user, post);
        likeRepo.save(like);

        // 🔔 NOTIFICATION (must be before return)
        String actor = (user.getName() != null && !user.getName().isBlank())
            ? user.getName()
            : user.getEmail();
        String targetLabel = post.isReel() ? "reel" : (MediaUrlUtils.isLikelyVideo(post.getMediaUrl()) ? "video" : "post");
        notificationService.notifyUser(
            post.getUser().getEmail(),
            actor + " liked your " + targetLabel + " [postId:" + post.getId() + "]"
        );

        return "Liked";
    }

    @GetMapping("/{postId}/count")
    public long count(@PathVariable long postId) {
        Post post = postRepo.findById(postId).orElseThrow();
        return likeRepo.countByPost(post);
    }
}

