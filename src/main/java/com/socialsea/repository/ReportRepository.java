package com.socialsea.repository;

import com.socialsea.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // 🔍 Find pending reports
    List<Report> findByResolvedFalse();
}
