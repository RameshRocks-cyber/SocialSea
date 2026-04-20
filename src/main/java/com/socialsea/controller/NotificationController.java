package com.socialsea.controller;

import com.socialsea.model.*;
import com.socialsea.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(originPatterns = "*")
public class NotificationController {

    private final NotificationRepository repo;
    private final UserRepository userRepo;
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern SOS_ALERT_ID_PATTERN =
        Pattern.compile("/sos/(?:live|navigate)/([0-9]+)", Pattern.CASE_INSENSITIVE);

    public NotificationController(NotificationRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return List.of();
        try {
            return buildNotificationPayload(auth.getName());
        } catch (Exception ex) {
            log.error("Failed to load notifications for {}", auth.getName(), ex);
            return List.of();
        }
    }

    @GetMapping("/unread-count")
    public long unread(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return 0;
        try {
            List<Notification> all = repo.findByRecipientIgnoreCaseOrderByCreatedAtDesc(auth.getName());
            Set<String> unreadThreads = new LinkedHashSet<>();
            for (Notification n : all) {
                if (n == null || n.isRead()) continue;
                unreadThreads.add(buildNotificationThreadKey(n));
            }
            return unreadThreads.size();
        } catch (Exception ex) {
            log.error("Failed to load unread notifications for {}", auth.getName(), ex);
            return 0;
        }
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable long id) {
        Notification n = repo.findById(id).orElseThrow();
        n.setRead(true);
        repo.save(n);
    }

    @PostMapping({"/read-all", "/mark-all-read"})
    public Map<String, Object> markAllRead(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("ok", false, "message", "Login required");
        }
        String recipient = auth.getName();
        repo.markAllAsRead(recipient);
        return Map.of("ok", true);
    }

    @PatchMapping
    public Map<String, Object> patchAll(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("ok", false, "message", "Login required");
        }
        boolean read = body == null || Boolean.TRUE.equals(body.get("read"));
        if (read) {
            repo.markAllAsRead(auth.getName());
            return Map.of("ok", true, "read", true);
        }
        return Map.of("ok", false, "message", "Unsupported patch");
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
        List<Notification> items = repo.findByRecipientIgnoreCaseOrderByCreatedAtDesc(recipientEmail);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seenFollowActors = new LinkedHashSet<>();

        for (Notification n : items) {
            String raw = n.getMessage();
            String kind = deriveKind(raw);
            String type = String.valueOf(n.getType());
            if ("EMERGENCY".equalsIgnoreCase(type)) {
                kind = "emergency";
            }
            if ("TRAFFIC".equalsIgnoreCase(type)) {
                kind = "traffic";
            }
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
            if ("emergency".equals(kind)) {
                String alertId = extractAlertId(raw);
                if (alertId != null) {
                    row.put("alertId", alertId);
                }
                row.put("liveUrl", extractUrlContaining(raw, "/sos/live/"));
                row.put("navigateUrl", extractUrlContaining(raw, "/sos/navigate/"));
                String mapsUrl = extractUrlContaining(raw, "google.com/maps");
                if (mapsUrl == null) {
                    mapsUrl = extractUrlContaining(raw, "maps.google.com");
                }
                row.put("mapsUrl", mapsUrl);
            }
            if ("traffic".equals(kind)) {
                String spotUrl = extractUrlContaining(raw, "/maps/search");
                String routeUrl = extractUrlContaining(raw, "/maps/dir");
                String mapsUrl = routeUrl != null ? routeUrl : spotUrl;
                if (mapsUrl == null) {
                    mapsUrl = extractUrlContaining(raw, "google.com/maps");
                }
                if (mapsUrl == null) {
                    mapsUrl = extractUrlContaining(raw, "maps.google.com");
                }
                row.put("spotUrl", spotUrl);
                row.put("routeUrl", routeUrl);
                row.put("mapsUrl", mapsUrl);
            }
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
        if (lower.contains("emergency")) return "emergency";
        if (lower.contains("follow")) return "follow";
        if (lower.contains("like")) return "like";
        if (lower.contains("comment")) return "comment";
        return "system";
    }

    private String extractUrlContaining(String message, String marker) {
        if (message == null || message.isBlank()) return null;
        Matcher matcher = URL_PATTERN.matcher(message);
        while (matcher.find()) {
            String url = matcher.group();
            if (url.contains(marker)) return trimUrl(url);
        }
        return null;
    }

    private String extractAlertId(String message) {
        if (message == null || message.isBlank()) return null;
        Matcher matcher = SOS_ALERT_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            String id = matcher.group(1);
            return (id != null && !id.isBlank()) ? id.trim() : null;
        }
        return null;
    }

    private String trimUrl(String value) {
        if (value == null) return null;
        return value.replaceAll("[),.;]+$", "");
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

    private String buildNotificationThreadKey(Notification notification) {
        String message = notification != null ? notification.getMessage() : null;
        String type = notification != null && notification.getType() != null ? notification.getType() : "SYSTEM";
        String kind = deriveKind(message);
        if ("EMERGENCY".equalsIgnoreCase(type)) {
            kind = "emergency";
        } else if ("TRAFFIC".equalsIgnoreCase(type)) {
            kind = "traffic";
        }

        String actorEmail = extractFirstEmail(message);
        if (actorEmail != null && !actorEmail.isBlank()) {
            return kind + ":" + actorEmail.trim().toLowerCase(Locale.ROOT);
        }

        if ("emergency".equals(kind)) {
            String alertId = extractAlertId(message);
            if (alertId != null && !alertId.isBlank()) {
                return "emergency:" + alertId.trim();
            }
        }

        String raw = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (raw.length() > 240) {
            raw = raw.substring(0, 240);
        }
        return kind + ":" + type.toLowerCase(Locale.ROOT) + ":" + raw;
    }
}

