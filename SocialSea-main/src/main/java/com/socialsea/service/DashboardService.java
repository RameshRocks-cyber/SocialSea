package com.socialsea.service;

import com.socialsea.dto.AdminDashboardStatsDto;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final AnonymousPostRepository anonymousPostRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public DashboardService(
        AnonymousPostRepository anonymousPostRepository
        ,UserRepository userRepository,
        PostRepository postRepository
    ) {
        this.anonymousPostRepository = anonymousPostRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    public AdminDashboardStatsDto getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count() + anonymousPostRepository.countByApprovedTrueAndRejectedFalse();

        long pendingAnonymousPosts = anonymousPostRepository.countByApprovedFalseAndRejectedFalse();
        long approvedAnonymousPosts = anonymousPostRepository.countByApprovedTrue();
        long rejectedAnonymousPosts = anonymousPostRepository.countByRejectedTrue();

        AdminDashboardStatsDto dto = new AdminDashboardStatsDto();
        dto.setTotalUsers(totalUsers);
        dto.setTotalPosts(totalPosts);
        dto.setPendingAnonymousPosts(pendingAnonymousPosts);
        dto.setApprovedAnonymousPosts(approvedAnonymousPosts);
        dto.setRejectedAnonymousPosts(rejectedAnonymousPosts);
        dto.setReports(3);
        return dto;
    }
}