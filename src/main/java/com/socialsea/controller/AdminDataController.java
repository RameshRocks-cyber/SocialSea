package com.socialsea.controller;

import com.socialsea.model.EmergencyAlert;
import com.socialsea.model.Post;
import com.socialsea.model.Report;
import com.socialsea.model.User;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.PostRepository;
import com.socialsea.repository.ReportRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import com.socialsea.util.MediaUrlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Optional;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://43.205.229.211:5173"
})
public class AdminDataController {
    private static final int DEFAULT_SOS_RADIUS_METERS = 5000;
    private static final int SOS_NEARBY_ALERT_LIMIT = 100;
    private static final long MAX_LOCATION_STALE_MINUTES = 30;

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final ReportRepository reportRepo;
    private final EmergencyAlertRepository emergencyRepo;
    private final NotificationService notificationService;

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return userRepo.findAll()
                .stream()
                .map(this::userView)
                .toList();
    }

    @PostMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(@PathVariable Long id) {
        return setUserBanState(id, true);
    }

    @PostMapping("/users/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable Long id) {
        return setUserBanState(id, false);
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable Long id) {
        return setUserBanState(id, true);
    }

    @PostMapping("/users/{id}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable Long id) {
        return setUserBanState(id, false);
    }

    @PostMapping("/users/{id}/notice")
    public ResponseEntity<?> issueUserNotice(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth
    ) {
        Long safeId = Objects.requireNonNull(id, "id");
        Optional<User> userOpt = userRepo.findById(safeId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();
        String recipient = String.valueOf(user.getEmail() != null ? user.getEmail() : "").trim();
        if (recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "User email is missing"));
        }

        String severity = normalizeNoticeSeverity(body != null ? body.get("severity") : null);
        String message = resolveNoticeMessage(body != null ? body.get("message") : null, severity);
        String issuedBy = auth != null && auth.getName() != null && !auth.getName().isBlank()
                ? auth.getName().trim()
                : "admin";
        String title = "yellow".equals(severity) ? "Yellow Notice" : "Red Notice";
        String type = "yellow".equals(severity) ? "MODERATION_YELLOW" : "MODERATION_RED";
        String finalMessage = message + " (Issued by " + issuedBy + ")";

        try {
            notificationService.notifyUserInApp(recipient, title, finalMessage, type);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to send notice"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("userId", user.getId());
        response.put("recipient", recipient);
        response.put("severity", severity);
        response.put("title", title);
        response.put("message", message);
        response.put("issuedBy", issuedBy);
        response.put("createdAt", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/posts")
    public List<Map<String, Object>> posts() {
        return postRepo.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::postView)
                .toList();
    }

    @GetMapping("/reports")
    public List<Map<String, Object>> reports() {
        return reportRepo.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::reportView)
                .toList();
    }

    @GetMapping("/live-recordings")
    public List<Map<String, Object>> liveRecordings() {
        List<User> allUsers = userRepo.findAll();
        return emergencyRepo.findAllByOrderByStartedAtDesc()
                .stream()
                .map(a -> liveRecordingView(a, allUsers))
                .toList();
    }

    @GetMapping("/sos-nearby")
    public List<Map<String, Object>> sosNearby() {
        List<User> allUsers = userRepo.findAll();
        return emergencyRepo.findAllByOrderByStartedAtDesc()
                .stream()
                .limit(SOS_NEARBY_ALERT_LIMIT)
                .map(alert -> sosNearbyView(alert, allUsers))
                .toList();
    }

    private Map<String, Object> userView(User u) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", u.getId());
        item.put("email", u.getEmail());
        item.put("name", u.getName());
        item.put("role", u.getRole() != null ? u.getRole().name() : "USER");
        item.put("banned", u.isBanned());
        item.put("profileCompleted", u.isProfileCompleted());
        item.put("createdAt", u.getCreatedAt());
        return item;
    }

    private ResponseEntity<?> setUserBanState(Long id, boolean banned) {
        Long safeId = Objects.requireNonNull(id, "id");
        Optional<User> userOpt = userRepo.findById(safeId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();
        user.setBanned(banned);
        User saved = userRepo.save(user);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "email", saved.getEmail(),
                "banned", saved.isBanned(),
                "message", banned ? "User blocked successfully" : "User unblocked successfully"
        ));
    }

    private String normalizeNoticeSeverity(Object rawSeverity) {
        String normalized = String.valueOf(rawSeverity != null ? rawSeverity : "")
                .trim()
                .toLowerCase();
        return "yellow".equals(normalized) ? "yellow" : "red";
    }

    private String resolveNoticeMessage(Object rawMessage, String severity) {
        String message = String.valueOf(rawMessage != null ? rawMessage : "").trim();
        if (message.isBlank()) {
            return "yellow".equals(severity)
                    ? "Policy warning issued."
                    : "Critical violation recorded.";
        }
        if (message.length() > 600) {
            return message.substring(0, 600);
        }
        return message;
    }

    private Map<String, Object> postView(Post p) {
        Map<String, Object> item = new HashMap<>();
        boolean video = p.isReel() || MediaUrlUtils.isLikelyVideo(p.getMediaUrl());
        item.put("id", p.getId());
        item.put("description", "");
        item.put("contentUrl", p.getMediaUrl());
        item.put("mediaUrl", p.getMediaUrl());
        item.put("type", video ? "VIDEO" : "IMAGE");
        item.put("reel", p.isReel());
        item.put("originalReel", p.isReel());
        item.put("isVideo", video);
        item.put("approved", p.isApproved());
        item.put("createdAt", p.getCreatedAt());
        if (p.getUser() != null) {
            item.put("userId", p.getUser().getId());
            item.put("email", p.getUser().getEmail());
            item.put("username", p.getUser().getName() != null ? p.getUser().getName() : p.getUser().getEmail());
        }
        return item;
    }

    private Map<String, Object> reportView(Report r) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", r.getId());
        item.put("reason", r.getReason());
        item.put("type", r.getType());
        item.put("postId", r.getPostId());
        item.put("anonymousPostId", r.getAnonymousPostId());
        item.put("resolved", r.isResolved());
        item.put("createdAt", r.getCreatedAt());
        if (r.getReporter() != null) {
            item.put("reporterEmail", r.getReporter().getEmail());
        }
        return item;
    }

    private Map<String, Object> liveRecordingView(EmergencyAlert a, List<User> allUsers) {
        Map<String, Object> item = new HashMap<>();
        Map<String, Object> nearbyContext = sosNearbyView(a, allUsers);
        Double exactLat = a.getCurrentLatitude() != null ? a.getCurrentLatitude() : a.getLatitude();
        Double exactLon = a.getCurrentLongitude() != null ? a.getCurrentLongitude() : a.getLongitude();

        item.put("id", a.getId());
        item.put("alertId", a.getId());
        item.put("email", a.getReporterEmail());
        item.put("reporterEmail", a.getReporterEmail());
        item.put("mediaUrl", a.getMediaUrl());
        item.put("startedAt", a.getStartedAt());
        item.put("endedAt", a.getEndedAt());
        item.put("durationMs", a.getDurationMs());
        item.put("active", a.isActive());
        item.put("latitude", exactLat);
        item.put("longitude", exactLon);
        item.put("currentLatitude", a.getCurrentLatitude());
        item.put("currentLongitude", a.getCurrentLongitude());
        item.put("accuracyMeters", a.getAccuracyMeters());
        item.put("radiusMeters", effectiveRadiusMeters(a));
        item.put("triggerType", "SOS_3_TAP");
        Map<String, Object> exactLocation = new HashMap<>();
        exactLocation.put("latitude", exactLat);
        exactLocation.put("longitude", exactLon);
        exactLocation.put("accuracyMeters", a.getAccuracyMeters());
        item.put("exactLocation", exactLocation);
        item.put("nearbyUsers", nearbyContext.get("nearbyUsers"));
        item.put("nearbyCount", nearbyContext.get("nearbyCount"));

        String username = userRepo.findByEmail(a.getReporterEmail())
                .map(User::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(a.getReporterEmail());
        item.put("username", username);
        item.put("reporterName", username);
        return item;
    }

    private Map<String, Object> sosNearbyView(EmergencyAlert alert, List<User> allUsers) {
        Map<String, Object> item = new HashMap<>();
        Double lat = alert.getCurrentLatitude() != null ? alert.getCurrentLatitude() : alert.getLatitude();
        Double lon = alert.getCurrentLongitude() != null ? alert.getCurrentLongitude() : alert.getLongitude();

        String reporterEmail = alert.getReporterEmail();
        String reporterName = userRepo.findByEmail(reporterEmail)
                .map(User::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(reporterEmail);

        item.put("alertId", alert.getId());
        item.put("startedAt", alert.getStartedAt());
        item.put("endedAt", alert.getEndedAt());
        item.put("active", alert.isActive());
        item.put("triggerType", "SOS_3_TAP");
        item.put("reporterName", reporterName);
        item.put("reporterEmail", reporterEmail);
        item.put("latitude", lat);
        item.put("longitude", lon);
        item.put("radiusMeters", effectiveRadiusMeters(alert));

        if (lat == null || lon == null) {
            item.put("nearbyUsers", List.of());
            item.put("nearbyCount", 0);
            return item;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> nearbyUsers = allUsers.stream()
                .filter(u -> u.getId() != null && u.getEmail() != null)
                .filter(u -> reporterEmail == null || !u.getEmail().equalsIgnoreCase(reporterEmail))
                .filter(u -> u.getLastLatitude() != null && u.getLastLongitude() != null && u.getLocationUpdatedAt() != null)
                .filter(u -> {
                    long minutesOld = Duration.between(u.getLocationUpdatedAt(), now).toMinutes();
                    return minutesOld >= 0 && minutesOld <= MAX_LOCATION_STALE_MINUTES;
                })
                .map(u -> {
                    double distanceMeters = haversineMeters(lat, lon, u.getLastLatitude(), u.getLastLongitude());
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", u.getId());
                    row.put("name", (u.getName() != null && !u.getName().isBlank()) ? u.getName() : u.getEmail());
                    row.put("email", u.getEmail());
                    row.put("distanceMeters", Math.round(distanceMeters));
                    row.put("latitude", u.getLastLatitude());
                    row.put("longitude", u.getLastLongitude());
                    row.put("locationUpdatedAt", u.getLocationUpdatedAt());
                    return row;
                })
                .filter(row -> {
                    Number distance = (Number) row.get("distanceMeters");
                    return distance != null && distance.longValue() <= effectiveRadiusMeters(alert);
                })
                .sorted(Comparator.comparingLong(row -> ((Number) row.get("distanceMeters")).longValue()))
                .toList();

        item.put("nearbyUsers", nearbyUsers);
        item.put("nearbyCount", nearbyUsers.size());
        return item;
    }

    private int effectiveRadiusMeters(EmergencyAlert alert) {
        if (alert == null || alert.getRadiusMeters() == null || alert.getRadiusMeters() <= 0) {
            return DEFAULT_SOS_RADIUS_METERS;
        }
        return alert.getRadiusMeters();
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}

