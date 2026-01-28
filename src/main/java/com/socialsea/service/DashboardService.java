package com.socialsea.service;

import com.socialsea.dto.AdminDashboardStatsDto;
import com.socialsea.repository.AnonymousPostRepository;
// import com.socialsea.repository.PostRepository;
// import com.socialsea.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final AnonymousPostRepository anonymousPostRepository;
    // private final UserRepository userRepository;
    // private final PostRepository postRepository;

    public DashboardService(
        AnonymousPostRepository anonymousPostRepository
        // UserRepository userRepository,
        // PostRepository postRepository
    ) {
        this.anonymousPostRepository = anonymousPostRepository;
        // this.userRepository = userRepository;
        // this.postRepository = postRepository;
    }

    public AdminDashboardStatsDto getDashboardStats() {
        // In the future, we'll use real repositories:
        // long totalUsers = userRepository.count();
        // long totalPosts = postRepository.count() + anonymousPostRepository.count();
        long pendingAnonymousPosts = anonymousPostRepository.countByApprovedFalseAndRejectedFalse();

        AdminDashboardStatsDto dto = new AdminDashboardStatsDto();
        dto.setTotalUsers(120); // Hardcoded as we don't have UserRepository yet
        dto.setTotalPosts(560); // Hardcoded as we don't have PostRepository yet
        dto.setPendingAnonymousPosts(pendingAnonymousPosts);
        dto.setReports(3);      // Hardcoded as per example
        return dto;
    }
}