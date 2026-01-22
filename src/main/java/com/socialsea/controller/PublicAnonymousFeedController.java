package com.socialsea.controller;

import com.socialsea.model.AnonymousPost;
import com.socialsea.service.AnonymousPostService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/anonymous")
@CrossOrigin("https://socialsea.netlify.app")
public class PublicAnonymousFeedController {

    private final AnonymousPostService service;

    public PublicAnonymousFeedController(AnonymousPostService service) {
        this.service = service;
    }

    @GetMapping("/feed")
    public List<AnonymousPost> feed() {
        return service.getApprovedPosts();
    }
}