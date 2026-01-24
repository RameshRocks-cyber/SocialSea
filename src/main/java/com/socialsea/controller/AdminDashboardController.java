package com.socialsea.controller;

import com.socialsea.dto.AdminStatsDto;
import com.socialsea.service.AnonymousPostService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AnonymousPostService anonymousPostService;

    public AdminDashboardController(AnonymousPostService anonymousPostService) {
        this.anonymousPostService = anonymousPostService;
    }

    @GetMapping("/stats")
    public AdminStatsDto getDashboardStats() {
        return anonymousPostService.getAdminStats();
    }
}