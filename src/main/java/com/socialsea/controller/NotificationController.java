package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin
public class NotificationController {

    private final NotificationRepository repo;
    private final UserRepository userRepo;

    public NotificationController(NotificationRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<Notification> list(Authentication auth) {
        User user = userRepo.findByUsername(auth.getName()).orElseThrow();
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    @GetMapping("/unread-count")
    public long unread(Authentication auth) {
        User user = userRepo.findByUsername(auth.getName()).orElseThrow();
        return repo.countByUserAndReadFalse(user);
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        Notification n = repo.findById(id).orElseThrow();
        n.setRead(true);
        repo.save(n);
    }
}

