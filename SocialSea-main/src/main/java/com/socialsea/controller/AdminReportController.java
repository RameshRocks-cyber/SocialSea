package com.socialsea.controller;

import com.socialsea.model.Report;
import com.socialsea.repository.ReportRepository;
import com.socialsea.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/reports")
@CrossOrigin(origins = {"https://socialsea.netlify.app","https://socialsea.co.in","https://www.socialsea.co.in","http://localhost:5173","http://127.0.0.1:5173"})
@PreAuthorize("hasAuthority('REPORT_RESOLVE')")
public class AdminReportController {

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;

    public AdminReportController(ReportRepository reportRepository, NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.notificationService = notificationService;
    }

    // 📋 View Pending Reports
    @GetMapping("/pending")
    public List<Report> pendingReports() {
        return reportRepository.findByResolvedFalse();
    }

    // ✅ Resolve Report
    @PostMapping("/resolve/{id}")
    public String resolve(@PathVariable Long id) {
        Report report = reportRepository.findById(id).orElseThrow();
        report.setResolved(true);
        reportRepository.save(report);

        if (report.getReporter() != null) {
            notificationService.notifyUser(
                report.getReporter().getEmail(),
                "Your report has been resolved."
            );
        }

        return "Report resolved";
    }
}
