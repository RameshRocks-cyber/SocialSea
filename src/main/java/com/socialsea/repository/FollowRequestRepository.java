package com.socialsea.repository;

import com.socialsea.model.FollowRequest;
import com.socialsea.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    boolean existsBySenderAndReceiver(User sender, User receiver);

    boolean existsBySenderAndReceiverAndStatus(User sender, User receiver, String status);

    List<FollowRequest> findByReceiverAndStatus(User receiver, String status);

    List<FollowRequest> findBySenderAndStatus(User sender, String status);

    Optional<FollowRequest> findFirstBySenderAndReceiverAndStatusIgnoreCase(User sender, User receiver, String status);
}
