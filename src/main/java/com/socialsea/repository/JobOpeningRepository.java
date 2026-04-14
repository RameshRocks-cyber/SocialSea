package com.socialsea.repository;

import com.socialsea.model.JobOpening;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobOpeningRepository extends JpaRepository<JobOpening, String> {

    List<JobOpening> findByStatusOrderByCreatedAtDesc(String status);

    List<JobOpening> findByOwnerOrderByCreatedAtDesc(User owner);

    List<JobOpening> findAllByOrderByCreatedAtDesc();
}
