package com.socialsea.repository;

import com.socialsea.model.AmbulanceDriverRequest;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AmbulanceDriverRequestRepository extends JpaRepository<AmbulanceDriverRequest, Long> {

    Optional<AmbulanceDriverRequest> findTopByUserOrderByCreatedAtDesc(User user);

    List<AmbulanceDriverRequest> findByStatusOrderByCreatedAtDesc(AmbulanceDriverRequest.Status status);
}

