package com.socialsea.repository;

import com.socialsea.model.LiveBroadcast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveBroadcastRepository extends JpaRepository<LiveBroadcast, Long> {

    List<LiveBroadcast> findByActiveTrueOrderByStartedAtDesc();

    Optional<LiveBroadcast> findFirstByActiveTrueOrderByStartedAtDesc();
}
