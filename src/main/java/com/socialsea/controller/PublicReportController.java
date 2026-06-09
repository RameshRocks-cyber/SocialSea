package com.socialsea.controller;

import com.socialsea.model.Report;
import com.socialsea.repository.ReportRepository;
import com.socialsea.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/public/report")
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://43.205.229.211:5173"
})
public class PublicReportController {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;

    public PublicReportController(ReportRepository reportRepository, NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/{postId}")
    public ResponseEntity<?> reportPost(
            @PathVariable Long postId,
            @RequestParam String reason
    ) {
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Reason is required"));
        }

        Report report = new Report();
        report.setAnonymousPostId(postId);
        report.setType("ANONYMOUS_POST");
        report.setReason(safeReason);

        reportRepository.save(report);

        notificationService.notify(
            "New Report",
            "New report received for post ID " + postId,
            "REPORT"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Report submitted"));
    }
}
