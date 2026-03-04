package com.socialsea.repository;

import com.socialsea.model.EmergencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {
    List<EmergencyAlert> findByReporterEmailOrderByStartedAtDesc(String reporterEmail);
}
