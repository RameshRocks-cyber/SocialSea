package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.service.AnonymousPostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/anonymous")
public class AnonymousFeedController {

    private final AnonymousPostService service;

    public AnonymousFeedController(AnonymousPostService service) {
        this.service = service;
    }

    // 🌍 Public feed (anonymous, no token)
    @GetMapping("/feed")
    public List<AnonymousPost> feed() {
        return service.getApprovedPosts();
    }
}