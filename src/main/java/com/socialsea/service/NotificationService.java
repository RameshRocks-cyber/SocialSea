package com.socialsea.service;

import com.socialsea.model.*;
import com.socialsea.repository.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void notify(User user, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        repo.save(n);
    }
}
