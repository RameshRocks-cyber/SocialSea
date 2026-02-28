package com.socialsea.controller;

import com.socialsea.dto.AdminChartDto;
import com.socialsea.dto.AdminStatsDto;
import com.socialsea.dto.ChartPointDto;
import com.socialsea.service.AdminDashboardService;
import com.socialsea.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "http://localhost:5173",
    "http://127.0.0.1:5173"
})
public class AdminDashboardController {

    private final AdminDashboardService service;
    private final AdminService adminService;

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return service.getStats();
    }

    // 📈 User Growth Chart
    @GetMapping("/users-chart")
    public AdminChartDto usersChart() {
        return adminService.getUserGrowth();
    }

    // 📊 Aggregated Charts
    @GetMapping("/charts")
    public Map<String, List<ChartPointDto>> charts() {
        return Map.of(
            "users", service.userGrowth(),
            "posts", service.postGrowth()
        );
    }

    // 📥 Download Users CSV
    @GetMapping(value = "/export/users", produces = "text/csv")
    public ResponseEntity<String> exportUsers(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok()
            .header("Content-Disposition",
                    "attachment; filename=users_last_" + days + "_days.csv")
            .body(service.exportUsersCsv(days));
    }

    // 📥 Download Posts CSV
    @GetMapping(value = "/export/posts", produces = "text/csv")
    public ResponseEntity<String> exportPosts(
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok()
            .header("Content-Disposition",
                    "attachment; filename=posts_last_" + days + "_days.csv")
            .body(service.exportPostsCsv(days));
    }
}
