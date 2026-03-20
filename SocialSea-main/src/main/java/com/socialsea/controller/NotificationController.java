package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {"https://socialsea.netlify.app","https://socialsea.co.in","https://www.socialsea.co.in","http://localhost:5173","http://127.0.0.1:5173"})
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Notification> list(Authentication auth) {
        return repo.findByRecipientOrderByCreatedAtDesc(auth.getName());
    }

    @GetMapping("/unread-count")
    public long unread(Authentication auth) {
        return repo.countByRecipientAndReadFalse(auth.getName());
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        Notification n = repo.findById(id).orElseThrow();
        n.setRead(true);
        repo.save(n);
    }
}

