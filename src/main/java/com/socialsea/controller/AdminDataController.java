package com.socialsea.controller;

import com.socialsea.model.Post;
import com.socialsea.model.Report;
import com.socialsea.model.User;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://43.205.213.14:5173"
})
public class AdminDataController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final ReportRepository reportRepo;

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return userRepo.findAll()
                .stream()
                .map(this::userView)
                .toList();
    }

    @GetMapping("/posts")
    public List<Map<String, Object>> posts() {
        return postRepo.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::postView)
                .toList();
    }

    @GetMapping("/reports")
    public List<Map<String, Object>> reports() {
        return reportRepo.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::reportView)
                .toList();
    }

    private Map<String, Object> userView(User u) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", u.getId());
        item.put("email", u.getEmail());
        item.put("name", u.getName());
        item.put("role", u.getRole() != null ? u.getRole().name() : "USER");
        item.put("banned", u.isBanned());
        item.put("profileCompleted", u.isProfileCompleted());
        item.put("createdAt", u.getCreatedAt());
        return item;
    }

    private Map<String, Object> postView(Post p) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", p.getId());
        item.put("description", "");
        item.put("contentUrl", p.getMediaUrl());
        item.put("mediaUrl", p.getMediaUrl());
        item.put("type", p.isReel() ? "VIDEO" : "IMAGE");
        item.put("reel", p.isReel());
        item.put("approved", p.isApproved());
        item.put("createdAt", p.getCreatedAt());
        if (p.getUser() != null) {
            item.put("userId", p.getUser().getId());
            item.put("email", p.getUser().getEmail());
            item.put("username", p.getUser().getName() != null ? p.getUser().getName() : p.getUser().getEmail());
        }
        return item;
    }

    private Map<String, Object> reportView(Report r) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", r.getId());
        item.put("reason", r.getReason());
        item.put("type", r.getType());
        item.put("postId", r.getPostId());
        item.put("anonymousPostId", r.getAnonymousPostId());
        item.put("resolved", r.isResolved());
        item.put("createdAt", r.getCreatedAt());
        if (r.getReporter() != null) {
            item.put("reporterEmail", r.getReporter().getEmail());
        }
        return item;
    }
}

