package com.socialsea.service;

import com.socialsea.dto.AdminDashboardStatsDto;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.util.MediaUrlUtils;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final AnonymousPostRepository anonymousPostRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public DashboardService(
        AnonymousPostRepository anonymousPostRepository
        ,PostRepository postRepository
        ,ReportRepository reportRepository
        ,UserRepository userRepository
    ) {
        this.anonymousPostRepository = anonymousPostRepository;
        this.postRepository = postRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    public AdminDashboardStatsDto getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();
        long totalVideos = postRepository.findAll().stream()
                .filter(p -> p.getMediaUrl() != null && !p.getMediaUrl().isBlank())
                .filter(p -> p.isReel() || MediaUrlUtils.isLikelyVideo(p.getMediaUrl()))
                .count();
        long pendingAnonymousPosts = anonymousPostRepository.countByApprovedFalseAndRejectedFalse();
        long approvedAnonymousPosts = anonymousPostRepository.countByApprovedTrue();
        long rejectedAnonymousPosts = anonymousPostRepository.countByRejectedTrue();
        long unresolvedReports = reportRepository.countByResolvedFalse();

        AdminDashboardStatsDto dto = new AdminDashboardStatsDto();
        dto.setTotalUsers(totalUsers);
        dto.setTotalPosts(totalPosts);
        dto.setTotalVideos(totalVideos);
        dto.setPendingAnonymousPosts(pendingAnonymousPosts);
        dto.setApprovedAnonymousPosts(approvedAnonymousPosts);
        dto.setRejectedAnonymousPosts(rejectedAnonymousPosts);
        dto.setReports(unresolvedReports);
        return dto;
    }
}
