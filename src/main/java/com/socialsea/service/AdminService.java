package com.socialsea.service;

import com.socialsea.dto.AdminChartDto;
import com.socialsea.dto.AdminStatsDto;
import com.socialsea.repository.AnonymousPostRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnonymousPostRepository anonymousPostRepository;

    @Autowired
    private ReportRepository reportRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminStatsDto getDashboardStats() {
        long users = userRepository.count();
        long posts = postRepository.count();
        long pending = anonymousPostRepository.countByApprovedFalseAndRejectedFalse();
        long reports = reportRepository.countByResolvedFalse();

        AdminStatsDto dto = new AdminStatsDto();
        dto.users = users;
        dto.posts = posts;
        dto.pendingAnonymous = pending;
        dto.unresolvedReports = reports;
        return dto;
    }

    public AdminChartDto getUserGrowth() {
        List<Object[]> rows = entityManager.createQuery(
            "SELECT cast(u.createdAt as date), COUNT(u) FROM User u GROUP BY cast(u.createdAt as date) ORDER BY cast(u.createdAt as date)", Object[].class)
            .getResultList();

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();

        for (Object[] row : rows) {
            labels.add(row[0].toString());
            values.add((Long) row[1]);
        }

        return new AdminChartDto(labels, values);
    }
}