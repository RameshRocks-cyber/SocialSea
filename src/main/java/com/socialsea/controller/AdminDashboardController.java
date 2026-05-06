package com.socialsea.controller;

import com.socialsea.dto.AdminChartDto;
import com.socialsea.dto.AdminStatsDto;
import com.socialsea.dto.ChartPointDto;
import com.socialsea.service.AdminDashboardService;
import com.socialsea.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://43.205.229.211:5173"
})
public class AdminDashboardController {

    private final AdminDashboardService service;
    private final AdminService adminService;

    @GetMapping
    public AdminStatsDto dashboard() {
        return service.getStats();
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return service.getStats();
    }

    @GetMapping("/users-chart")
    public AdminChartDto usersChart() {
        return adminService.getUserGrowth();
    }

    @GetMapping("/charts")
    public Map<String, List<ChartPointDto>> charts(@RequestParam(defaultValue = "7") int days) {
        try {
            return service.charts(days);
        } catch (Exception e) {
            log.error("Failed to build dashboard charts for days={}", days, e);
            throw e;
        }
    }

    @GetMapping(value = "/export/users", produces = "text/csv")
    public ResponseEntity<String> exportUsers(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=users_last_" + days + "_days.csv")
            .body(service.exportUsersCsv(days));
    }

    @GetMapping(value = "/export/posts", produces = "text/csv")
    public ResponseEntity<String> exportPosts(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=posts_last_" + days + "_days.csv")
            .body(service.exportPostsCsv(days));
    }
}

