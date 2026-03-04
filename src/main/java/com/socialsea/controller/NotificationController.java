package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {"https://socialsea.netlify.app", "https://socialsea.co.in", "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.213.14:5173"})
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
    public List<Map<String, Object>> list(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return List.of();
        return buildNotificationPayload(auth.getName());
    }

    @GetMapping("/unread-count")
    public long unread(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return 0;
        return buildNotificationPayload(auth.getName()).stream()
                .filter(item -> !Boolean.TRUE.equals(item.get("read")))
                .count();
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

    private List<Map<String, Object>> buildNotificationPayload(String recipientEmail) {
        List<Notification> items = repo.findByRecipientOrderByCreatedAtDesc(recipientEmail);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seenFollowActors = new LinkedHashSet<>();

        for (Notification n : items) {
            String raw = n.getMessage();
            String kind = deriveKind(raw);
            String actorEmail = extractFirstEmail(raw);
            Optional<User> actorOpt = actorEmail == null ? Optional.empty() : userRepo.findByEmail(actorEmail);
            String actorName = actorOpt
                    .map(u -> (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail())
                    .orElse(actorEmail);
            String normalizedMessage = normalizeSenderName(raw);

            // Collapse noisy repeated follow alerts from the same actor.
            if ("follow".equals(kind)) {
                String dedupeActor = (actorEmail != null && !actorEmail.isBlank())
                        ? actorEmail.toLowerCase()
                        : deriveActorFromMessage(normalizedMessage).toLowerCase();
                if (!seenFollowActors.add(dedupeActor)) {
                    continue;
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("id", n.getId());
            row.put("title", n.getTitle());
            row.put("type", n.getType());
            row.put("message", normalizedMessage);
            row.put("read", n.isRead());
            row.put("createdAt", n.getCreatedAt());
            row.put("recipient", n.getRecipient());
            row.put("kind", kind);
            row.put("actorEmail", actorEmail);
            row.put("actorName", actorName);
            row.put("actorProfilePic", actorOpt.map(User::getProfilePic).orElse(null));
            row.put("actorIdentifier", (actorEmail != null && !actorEmail.isBlank()) ? actorEmail : actorName);
            out.add(row);
        }

        return out;
    }

    private String extractFirstEmail(String message) {
        if (message == null || message.isBlank()) return null;
        Matcher matcher = EMAIL_PATTERN.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    private String deriveKind(String message) {
        String lower = String.valueOf(message).toLowerCase();
        if (lower.contains("follow")) return "follow";
        if (lower.contains("like")) return "like";
        if (lower.contains("comment")) return "comment";
        return "system";
    }

    private String deriveActorFromMessage(String message) {
        if (message == null || message.isBlank()) return "user";
        String lower = message.toLowerCase();
        String[] cuts = new String[]{" liked ", " started following ", " commented ", " mentioned "};
        for (String cut : cuts) {
            int idx = lower.indexOf(cut);
            if (idx > 0) return message.substring(0, idx).trim();
        }
        return "user";
    }
}

