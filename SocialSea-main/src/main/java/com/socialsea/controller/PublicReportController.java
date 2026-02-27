package com.socialsea.controller;

import com.socialsea.model.Report;
import com.socialsea.repository.ReportRepository;
import com.socialsea.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/public/report")
@CrossOrigin("https://socialsea.netlify.app")
public class PublicReportController {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;

    public PublicReportController(ReportRepository reportRepository, NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.notificationService = notificationService;
    }

    // ✅ PUBLIC REPORT ENDPOINT
    @PostMapping("/{postId}")
    public String reportPost(
            @PathVariable @NonNull Long postId,
            @RequestParam String reason
    ) {
        Report report = new Report();
        report.setAnonymousPostId(postId); // Maps to Anonymous Post
        report.setReason(reason);

        reportRepository.save(report);

        notificationService.notify(
            "🚩 New Report",
            "New report received for post ID " + postId,
            "REPORT"
        );

        return "Report submitted";
    }
}