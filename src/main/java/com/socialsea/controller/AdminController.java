package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin("https://socialsea.netlify.app")
public class AdminController {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final AnonymousPostRepository anonRepo;
    private final ReportRepository reportRepo;

    public AdminController(
        UserRepository userRepo,
        PostRepository postRepo,
        AnonymousPostRepository anonRepo,
        ReportRepository reportRepo
    ) {
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.anonRepo = anonRepo;
        this.reportRepo = reportRepo;
    }

    // 👤 All users
    @GetMapping("/users")
    public List<User> users() {
        return userRepo.findAll();
    }

    // 🚫 Ban user
    @PostMapping("/ban/{id}")
    public void ban(@PathVariable Long id) {
        User u = userRepo.findById(id).orElseThrow();
        u.setBanned(true);
        userRepo.save(u);
    }

    // 📸 All posts
    @GetMapping("/posts")
    public List<Post> posts() {
        return postRepo.findAll();
    }

    // ❌ Delete post
    @DeleteMapping("/posts/{id}")
    public void deletePost(@PathVariable Long id) {
        postRepo.deleteById(id);
    }

    // 👻 Anonymous videos
    @GetMapping("/anonymous")
    public List<AnonymousPost> anonymous() {
        return anonRepo.findAll();
    }

    @DeleteMapping("/anonymous/{id}")
    public void deleteAnon(@PathVariable Long id) {
        anonRepo.deleteById(id);
    }

    // 🚩 All reports
    @GetMapping("/reports")
    public List<Report> reports() {
        return reportRepo.findAll();
    }
}
