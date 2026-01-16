package com.socialsea.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String root() {
        return "SocialSea backend is running";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
