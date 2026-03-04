package com.socialsea.repository;

import com.socialsea.model.EmergencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, Long> {
}
