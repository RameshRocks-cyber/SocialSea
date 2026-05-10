package com.socialsea.repository;

import com.socialsea.model.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    List<WebPushSubscription> findByRecipientIgnoreCaseAndActiveTrue(String recipient);

    @Modifying
    @Transactional
    @Query("UPDATE WebPushSubscription s SET s.active = false WHERE lower(s.recipient) = lower(:recipient) AND s.endpoint = :endpoint")
    int deactivateByRecipientAndEndpoint(String recipient, String endpoint);
}

