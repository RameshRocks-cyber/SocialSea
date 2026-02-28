package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin("https://socialsea.netlify.app")
public class NotificationController {

    private final NotificationRepository repo;
    private final UserRepository userRepo;
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    public NotificationController(NotificationRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<Notification> list(Authentication auth) {
        List<Notification> items = repo.findByRecipientOrderByCreatedAtDesc(auth.getName());
        items.forEach(n -> n.setMessage(normalizeSenderName(n.getMessage())));
        return items;
    }

    @GetMapping("/unread-count")
    public long unread(Authentication auth) {
        return repo.countByRecipientAndReadFalse(auth.getName());
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable long id) {
        Notification n = repo.findById(id).orElseThrow();
        n.setRead(true);
        repo.save(n);
    }

    private String normalizeSenderName(String message) {
        if (message == null || message.isBlank()) return message;

        Matcher matcher = EMAIL_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String email = matcher.group();
            String display = userRepo.findByEmail(email)
                .map(u -> (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail())
                .orElse(email);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(display));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
