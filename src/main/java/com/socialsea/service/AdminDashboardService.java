package com.socialsea.service;

import com.socialsea.dto.AdminStatsDto;
import com.socialsea.dto.ChartPointDto;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.LikeRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.util.MediaUrlUtils;
import com.socialsea.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final LikeRepository likeRepo;
    private final AnonymousPostRepository anonRepo;
    private final ReportRepository reportRepo;

    public AdminStatsDto getStats() {
        AdminStatsDto dto = new AdminStatsDto();
        dto.users = userRepo.count();
        dto.posts = postRepo.count();
        dto.videos = postRepo.findAll().stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> p.isReel() || MediaUrlUtils.isLikelyVideo(p.getMediaUrl()))
                .count();
        dto.likes = likeRepo.count();
        dto.pendingAnonymous = anonRepo.countByApprovedFalseAndRejectedFalse();
        dto.unresolvedReports = reportRepo.countByResolvedFalse();

        // Backward-compatible aliases
        dto.totalUsers = dto.users;
        dto.totalPosts = dto.posts;
        dto.totalVideos = dto.videos;
        dto.pendingAnonymousPosts = dto.pendingAnonymous;
        dto.reports = dto.unresolvedReports;
        return dto;
    }

    public List<ChartPointDto> userGrowth() {
        return Optional.ofNullable(userRepo.userGrowth()).orElse(List.of());
    }

    public List<ChartPointDto> postGrowth() {
        return Optional.ofNullable(postRepo.postGrowth()).orElse(List.of());
    }

    @Cacheable(value = "dashboardUsers", key = "#days", unless = "#result == null")
    public List<ChartPointDto> getUserGrowth(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return Optional.ofNullable(userRepo.userGrowthFrom(from)).orElse(List.of());
    }

    @Cacheable(value = "dashboardPosts", key = "#days", unless = "#result == null")
    public List<ChartPointDto> getPostGrowth(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return Optional.ofNullable(postRepo.postGrowthFrom(from)).orElse(List.of());
    }

    public Map<String, List<ChartPointDto>> charts(int days) {
        LocalDateTime from = LocalDate.now()
                .minusDays(days)
                .atStartOfDay();

        return Map.of(
            "users", Optional.ofNullable(userRepo.userGrowthFrom(from)).orElse(List.of()),
            "posts", Optional.ofNullable(postRepo.postGrowthFrom(from)).orElse(List.of())
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
