package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.service.AnonymousPostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/anonymous")
@CrossOrigin
public class AdminAnonymousPostController {

    private final AnonymousPostService service;

    public AdminAnonymousPostController(AnonymousPostService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<AnonymousPost> getPending() {
        return service.getPendingPosts();
    }

    @PostMapping("/approve/{id}")
    public void approve(@PathVariable Long id) {
        service.approvePost(id);
    }

    @PostMapping("/reject/{id}")
    public void reject(
            @PathVariable Long id,
            @RequestBody String reason
    ) {
        service.rejectPost(id, reason);
    }
}