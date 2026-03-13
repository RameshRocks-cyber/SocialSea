package com.socialsea.controller;

import com.socialsea.model.EmergencyAlert;
import com.socialsea.model.User;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CloudinaryService;
import com.socialsea.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.Duration;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/emergency", "/emergency"})
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://43.205.213.14:5173"
})
@RequiredArgsConstructor
public class EmergencyController {

    private static final int DEFAULT_RADIUS_METERS = 5000;
    private static final long MAX_LOCATION_STALE_MINUTES = 30;
    private static final String DEFAULT_FRONTEND_BASE = "https://socialsea.co.in";

    private final UserRepository userRepo;
    private final EmergencyAlertRepository emergencyRepo;
    private final NotificationService notificationService;
    private final CloudinaryService cloudinaryService;
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @PostMapping("/presence")
    public ResponseEntity<?> presence(@RequestBody PresenceRequest request, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (request == null || request.latitude == null || request.longitude == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "latitude and longitude are required"));
        }

        User user = userRepo.findByEmail(auth.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }

        user.setLastLatitude(request.latitude);
        user.setLastLongitude(request.longitude);
        user.setLocationUpdatedAt(LocalDateTime.now());
        userRepo.save(user);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(@RequestBody TriggerRequest request, Authentication auth, HttpServletRequest httpRequest) {
        if (request == null || request.latitude == null || request.longitude == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "latitude and longitude are required"));
        }

        String reporterEmail = null;
        if (auth != null && auth.isAuthenticated()) {
            reporterEmail = auth.getName();
        }
        if ((reporterEmail == null || reporterEmail.isBlank()) && request.reporterEmail != null) {
            reporterEmail = request.reporterEmail.trim();
        }
        if (reporterEmail == null || reporterEmail.isBlank()) {
            reporterEmail = "anonymous@socialsea.local";
        }

        User reporter = userRepo.findByEmail(reporterEmail).orElse(null);

        int radiusMeters = request.radiusMeters != null && request.radiusMeters > 0
                ? request.radiusMeters
                : DEFAULT_RADIUS_METERS;

        if (reporter != null) {
            reporter.setLastLatitude(request.latitude);
            reporter.setLastLongitude(request.longitude);
            reporter.setLocationUpdatedAt(LocalDateTime.now());
            userRepo.save(reporter);
        }

        EmergencyAlert alert = new EmergencyAlert();
        alert.setReporterEmail(reporterEmail);
        alert.setLatitude(request.latitude);
        alert.setLongitude(request.longitude);
        alert.setAccuracyMeters(request.accuracyMeters);
        alert.setRadiusMeters(radiusMeters);
        alert.setFrontCameraEnabled(Boolean.TRUE.equals(request.frontCameraEnabled));
        alert.setBackCameraEnabled(Boolean.TRUE.equals(request.backCameraEnabled));
        alert.setLiveAudioActive(Boolean.TRUE.equals(request.audioActive));
        alert.setLiveVideoActive(Boolean.TRUE.equals(request.videoActive));
        alert.setCurrentLatitude(request.latitude);
        alert.setCurrentLongitude(request.longitude);
        alert.setLastHeartbeatAt(LocalDateTime.now());
        alert.setActive(true);
        alert.setStartedAt(LocalDateTime.now());
        EmergencyAlert saved = emergencyRepo.save(alert);

        String frontendBase = resolveFrontendBaseUrl(httpRequest);
        String mapsUrl = "https://maps.google.com/?q=" + request.latitude + "," + request.longitude;
        String liveUrl = frontendBase + "/sos/live/" + saved.getId();
        String navigateUrl = frontendBase + "/sos/navigate/" + saved.getId();
        // Keep URLs first so they survive NotificationService message clipping.
        String message = "Emergency alert by " + reporterEmail
                + ". Live page: " + liveUrl
                + ". Navigate: " + navigateUrl
                + ". Map: " + mapsUrl
                + ". Exact location: " + request.latitude + ", " + request.longitude
                + " (radius " + radiusMeters + "m). "
                + "Live AV: " + (alert.isLiveAudioActive() || alert.isLiveVideoActive() ? "ON" : "OFF");

        int notified = 0;
        boolean selfNotified = false;
        try {
            notificationService.notifyUser(
                    reporterEmail,
                    "Your SOS Is Active",
                    "Your SOS was triggered. Live page: " + liveUrl + ". Navigate: " + navigateUrl,
                    "EMERGENCY"
            );
            selfNotified = true;
        } catch (Exception ignored) {
            // Self notification should not block SOS trigger.
        }
        List<User> users = userRepo.findAll();
        for (User user : users) {
            if (user.getId() == null || user.getEmail() == null) continue;
            if (user.getEmail().equalsIgnoreCase(reporterEmail)) continue;
            if (reporter != null && user.getId().equals(reporter.getId())) continue;
            if (user.getLastLatitude() == null || user.getLastLongitude() == null
                    || user.getLocationUpdatedAt() == null
                    || Duration.between(user.getLocationUpdatedAt(), LocalDateTime.now()).toMinutes() > MAX_LOCATION_STALE_MINUTES) {
                // Safety first: if location is unavailable/stale, still notify.
                try {
                    notificationService.notifyUser(
                            user.getEmail(),
                            "Emergency Alert Nearby",
                            message,
                            "EMERGENCY"
                    );
                    notified++;
                } catch (Exception ignored) {
                    // Keep dispatching to others even if one recipient channel fails.
                }
                continue;
            }

            double distance = haversineMeters(
                    request.latitude,
                    request.longitude,
                    user.getLastLatitude(),
                    user.getLastLongitude()
            );

            if (distance <= radiusMeters) {
                try {
                    notificationService.notifyUser(
                            user.getEmail(),
                            "Emergency Alert Nearby",
                            message,
                            "EMERGENCY"
                    );
                    notified++;
                } catch (Exception ignored) {
                    // Keep dispatching to others even if one recipient channel fails.
                }
            }
        }

        try {
            notificationService.notify(
                    "Emergency Alert",
                    message + " Notified nearby users: " + notified,
                    "EMERGENCY"
            );
        } catch (Exception ignored) {
            // Admin summary notification failure should not block SOS trigger.
        }

        Map<String, Object> response = new HashMap<>();
        response.put("alertId", saved.getId());
        response.put("notifiedUsers", notified);
        response.put("selfNotified", selfNotified);
        response.put("radiusMeters", radiusMeters);
        response.put("liveUrl", liveUrl);
        response.put("navigateUrl", navigateUrl);
        response.put("audioActive", saved.isLiveAudioActive());
        response.put("videoActive", saved.isLiveVideoActive());
        Map<String, Object> location = new HashMap<>();
        location.put("latitude", request.latitude);
        location.put("longitude", request.longitude);
        location.put("accuracyMeters", request.accuracyMeters);
        response.put("location", location);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<?> activeAlerts(Authentication auth, HttpServletRequest httpRequest) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(List.of());
        }

        User me = userRepo.findByEmail(auth.getName()).orElse(null);
        if (me == null || me.getLastLatitude() == null || me.getLastLongitude() == null || me.getLocationUpdatedAt() == null
                || Duration.between(me.getLocationUpdatedAt(), LocalDateTime.now()).toMinutes() > MAX_LOCATION_STALE_MINUTES) {
            return ResponseEntity.ok(List.of());
        }

        String frontendBase = resolveFrontendBaseUrl(httpRequest);
        List<Map<String, Object>> items = emergencyRepo.findTop20ByActiveTrueOrderByStartedAtDesc()
                .stream()
                .filter(a -> !auth.getName().equalsIgnoreCase(a.getReporterEmail()))
                .filter(a -> {
                    Double lat = a.getCurrentLatitude() != null ? a.getCurrentLatitude() : a.getLatitude();
                    Double lon = a.getCurrentLongitude() != null ? a.getCurrentLongitude() : a.getLongitude();
                    if (lat == null || lon == null) return false;
                    int radiusMeters = a.getRadiusMeters() != null && a.getRadiusMeters() > 0
                            ? a.getRadiusMeters()
                            : DEFAULT_RADIUS_METERS;
                    double distance = haversineMeters(lat, lon, me.getLastLatitude(), me.getLastLongitude());
                    return distance <= radiusMeters;
                })
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertId", a.getId());
                    item.put("reporterEmail", a.getReporterEmail());
                    item.put("startedAt", a.getStartedAt());
                    item.put("latitude", a.getCurrentLatitude() != null ? a.getCurrentLatitude() : a.getLatitude());
                    item.put("longitude", a.getCurrentLongitude() != null ? a.getCurrentLongitude() : a.getLongitude());
                    item.put("audioActive", a.isLiveAudioActive());
                    item.put("videoActive", a.isLiveVideoActive());
                    item.put("radiusMeters", a.getRadiusMeters());
                    item.put("liveUrl", frontendBase + "/sos/live/" + a.getId());
                    item.put("navigateUrl", frontendBase + "/sos/navigate/" + a.getId());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{alertId}/assist")
    public ResponseEntity<?> assist(@PathVariable Long alertId, Authentication auth, HttpServletRequest httpRequest) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Alert not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        Double targetLat = alert.getCurrentLatitude() != null ? alert.getCurrentLatitude() : alert.getLatitude();
        Double targetLon = alert.getCurrentLongitude() != null ? alert.getCurrentLongitude() : alert.getLongitude();
        if (targetLat == null || targetLon == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "SOS location unavailable"));
        }

        String frontendBase = resolveFrontendBaseUrl(httpRequest);
        String mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=" + targetLat + "," + targetLon;
        String liveUrl = frontendBase + "/sos/live/" + alert.getId();
        String navigateUrl = frontendBase + "/sos/navigate/" + alert.getId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("alertId", alert.getId());
        payload.put("active", alert.isActive());
        payload.put("startedAt", alert.getStartedAt());
        payload.put("endedAt", alert.getEndedAt());
        payload.put("reporterEmail", alert.getReporterEmail());
        payload.put("latitude", targetLat);
        payload.put("longitude", targetLon);
        payload.put("mapsUrl", mapsUrl);
        payload.put("liveUrl", liveUrl);
        payload.put("navigateUrl", navigateUrl);
        payload.put("radiusMeters", alert.getRadiusMeters());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{alertId}/heartbeat")
    public ResponseEntity<?> heartbeat(
            @PathVariable Long alertId,
            @RequestBody HeartbeatRequest request,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Alert not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        if (!auth.getName().equalsIgnoreCase(alert.getReporterEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }
        if (!alert.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Alert already stopped"));
        }

        if (request != null && request.latitude != null && request.longitude != null) {
            alert.setCurrentLatitude(request.latitude);
            alert.setCurrentLongitude(request.longitude);

            User user = userRepo.findByEmail(auth.getName()).orElse(null);
            if (user != null) {
                user.setLastLatitude(request.latitude);
                user.setLastLongitude(request.longitude);
                user.setLocationUpdatedAt(LocalDateTime.now());
                userRepo.save(user);
            }
        }
        if (request != null) {
            alert.setLiveAudioActive(Boolean.TRUE.equals(request.audioActive));
            alert.setLiveVideoActive(Boolean.TRUE.equals(request.videoActive));
        }
        alert.setLastHeartbeatAt(LocalDateTime.now());
        emergencyRepo.save(alert);

        return ResponseEntity.ok(Map.of(
                "alertId", alert.getId(),
                "active", alert.isActive(),
                "audioActive", alert.isLiveAudioActive(),
                "videoActive", alert.isLiveVideoActive(),
                "lastHeartbeatAt", alert.getLastHeartbeatAt()
        ));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<?> status(@PathVariable Long alertId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Alert not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        if (!auth.getName().equalsIgnoreCase(alert.getReporterEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("alertId", alert.getId());
        payload.put("active", alert.isActive());
        payload.put("reporterEmail", alert.getReporterEmail());
        payload.put("radiusMeters", alert.getRadiusMeters());
        payload.put("startedAt", alert.getStartedAt());
        payload.put("endedAt", alert.getEndedAt());
        payload.put("latitude", alert.getCurrentLatitude() != null ? alert.getCurrentLatitude() : alert.getLatitude());
        payload.put("longitude", alert.getCurrentLongitude() != null ? alert.getCurrentLongitude() : alert.getLongitude());
        payload.put("audioActive", alert.isLiveAudioActive());
        payload.put("videoActive", alert.isLiveVideoActive());
        payload.put("lastHeartbeatAt", alert.getLastHeartbeatAt());
        payload.put("mediaUrl", alert.getMediaUrl());
        payload.put("durationMs", alert.getDurationMs());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/my-recordings")
    public ResponseEntity<?> myRecordings(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        List<EmergencyAlert> alerts = emergencyRepo.findByReporterEmailOrderByStartedAtDesc(auth.getName());
        List<Map<String, Object>> items = alerts.stream()
                .filter(a -> a.getMediaUrl() != null && !a.getMediaUrl().trim().isEmpty())
                .map(a -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertId", a.getId());
                    item.put("mediaUrl", a.getMediaUrl());
                    item.put("startedAt", a.getStartedAt());
                    item.put("endedAt", a.getEndedAt());
                    item.put("durationMs", a.getDurationMs());
                    item.put("latitude", a.getLatitude());
                    item.put("longitude", a.getLongitude());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(items);
    }

    @DeleteMapping("/my-recordings/{alertId}")
    public ResponseEntity<?> deleteMyRecording(@PathVariable Long alertId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Recording not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        if (!auth.getName().equalsIgnoreCase(alert.getReporterEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        emergencyRepo.delete(alert);
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", alertId));
    }

    @PostMapping(path = "/{alertId}/stop", consumes = {"multipart/form-data"})
    public ResponseEntity<?> stop(
            @PathVariable Long alertId,
            @RequestPart(value = "media", required = false) MultipartFile media,
            @RequestParam(value = "durationMs", required = false) Long durationMs,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Alert not found"));
        }

        EmergencyAlert alert = alertOpt.get();
        if (!auth.getName().equalsIgnoreCase(alert.getReporterEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        alert.setActive(false);
        alert.setEndedAt(LocalDateTime.now());
        alert.setDurationMs(durationMs);
        alert.setLiveAudioActive(false);
        alert.setLiveVideoActive(false);
        alert.setLastHeartbeatAt(LocalDateTime.now());

        if (media != null && !media.isEmpty()) {
            String url = cloudinaryService.upload(media);
            alert.setMediaUrl(url);
        }

        EmergencyAlert saved = emergencyRepo.save(alert);

        return ResponseEntity.ok(Map.of(
                "alertId", saved.getId(),
                "active", saved.isActive(),
                "mediaUrl", saved.getMediaUrl(),
                "durationMs", saved.getDurationMs(),
                "audioActive", saved.isLiveAudioActive(),
                "videoActive", saved.isLiveVideoActive()
        ));
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

    private String resolveFrontendBaseUrl(HttpServletRequest request) {
        String origin = String.valueOf(request.getHeader("Origin")).trim();
        String originBase = normalizeHttpBase(origin);
        if (originBase != null) return originBase;

        String referer = String.valueOf(request.getHeader("Referer")).trim();
        String refererBase = normalizeHttpBase(referer);
        if (refererBase != null) return refererBase;

        String configuredBase = normalizeHttpBase(frontendBaseUrl);
        if (configuredBase != null) return configuredBase;
        return DEFAULT_FRONTEND_BASE;
    }

    private String normalizeHttpBase(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            int port = uri.getPort();
            String portPart = port > 0 ? ":" + port : "";
            return (scheme + "://" + host + portPart).replaceAll("/+$", "");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class TriggerRequest {
        public Double latitude;
        public Double longitude;
        public Double accuracyMeters;
        public Integer radiusMeters;
        public String reporterEmail;
        public Boolean frontCameraEnabled;
        public Boolean backCameraEnabled;
        public Boolean audioActive;
        public Boolean videoActive;
    }

    public static class PresenceRequest {
        public Double latitude;
        public Double longitude;
    }

    public static class HeartbeatRequest {
        public Double latitude;
        public Double longitude;
        public Boolean audioActive;
        public Boolean videoActive;
    }
}
