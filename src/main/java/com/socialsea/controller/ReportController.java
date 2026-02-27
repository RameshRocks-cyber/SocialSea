package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.socialsea.service.NotificationService;

@RestController
@RequestMapping("/api/report")
@CrossOrigin("https://socialsea.netlify.app")
public class ReportController {

    private final ReportRepository reportRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public ReportController(ReportRepository reportRepo, UserRepository userRepo, NotificationService notificationService) {
        this.reportRepo = reportRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    @PostMapping
    public void report(
        @RequestParam Long postId,
        @RequestParam String reason,
        @RequestParam String type,
        Authentication auth
    ) {
        User u = userRepo.findByEmail(auth.getName()).orElseThrow();

        Report r = new Report();
        r.setPostId(postId);
        r.setType(type);
        r.setReason(reason);
        r.setReporter(u);

        reportRepo.save(r);

        notificationService.notify(
            "🚩 New Report",
            "Post #" + postId + " reported by " + u.getEmail(),
            "REPORT"
        );
    }
}
