package com.socialsea.service;

import com.socialsea.dto.AdminStatsDto;
import com.socialsea.dto.ChartPointDto;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final AnonymousPostRepository anonRepo;
    private final ReportRepository reportRepo;

    public AdminStatsDto getStats() {
        AdminStatsDto dto = new AdminStatsDto();
        dto.users = userRepo.count();
        dto.posts = postRepo.count();
        dto.pendingAnonymous = anonRepo.countByApprovedFalseAndRejectedFalse();
        dto.unresolvedReports = reportRepo.countByResolvedFalse();
        return dto;
    }

    public List<ChartPointDto> userGrowth() {
        return userRepo.userGrowth();
    }

    public List<ChartPointDto> postGrowth() {
        return postRepo.postGrowth();
    }

    @Cacheable(value = "dashboardUsers", key = "#days", unless = "#result == null")
    public List<ChartPointDto> getUserGrowth(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return userRepo.userGrowthFrom(from);
    }

    @Cacheable(value = "dashboardPosts", key = "#days", unless = "#result == null")
    public List<ChartPointDto> getPostGrowth(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return postRepo.postGrowthFrom(from);
    }

    public Map<String, List<ChartPointDto>> charts(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return Map.of(
            "users", userRepo.userGrowthFrom(from),
            "posts", postRepo.postGrowthFrom(from)
        );
    }

    // 📄 Export Users CSV
    public String exportUsersCsv(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return CsvUtil.toCsv(userRepo.userGrowthFrom(from));
    }

    // 📄 Export Posts CSV
    public String exportPostsCsv(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return CsvUtil.toCsv(postRepo.postGrowthFrom(from));
    }
}