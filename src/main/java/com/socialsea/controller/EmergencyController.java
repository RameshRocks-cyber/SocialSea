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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/emergency")
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
    public ResponseEntity<?> trigger(@RequestBody TriggerRequest request, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        if (request == null || request.latitude == null || request.longitude == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "latitude and longitude are required"));
        }

        User reporter = userRepo.findByEmail(auth.getName()).orElse(null);
        if (reporter == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
        }

        int radiusMeters = request.radiusMeters != null && request.radiusMeters > 0
                ? request.radiusMeters
                : DEFAULT_RADIUS_METERS;

        reporter.setLastLatitude(request.latitude);
        reporter.setLastLongitude(request.longitude);
        reporter.setLocationUpdatedAt(LocalDateTime.now());
        userRepo.save(reporter);

        EmergencyAlert alert = new EmergencyAlert();
        alert.setReporterEmail(reporter.getEmail());
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

        String mapsUrl = "https://maps.google.com/?q=" + request.latitude + "," + request.longitude;
        String liveUrl = frontendBaseUrl.replaceAll("/+$", "") + "/sos/live/" + saved.getId();
        String message = "Emergency alert by " + reporter.getEmail()
                + ". Exact location: " + request.latitude + ", " + request.longitude
                + " (radius " + radiusMeters + "m). "
                + "Live AV: " + (alert.isLiveAudioActive() || alert.isLiveVideoActive() ? "ON" : "OFF")
                + ". " + mapsUrl
                + ". Live page: " + liveUrl;

        int notified = 0;
        List<User> users = userRepo.findAll();
        for (User user : users) {
            if (user.getId() == null || user.getEmail() == null) continue;
            if (user.getId().equals(reporter.getId())) continue;
            if (user.getLastLatitude() == null || user.getLastLongitude() == null) continue;

            double distance = haversineMeters(
                    request.latitude,
                    request.longitude,
                    user.getLastLatitude(),
                    user.getLastLongitude()
            );

            if (distance <= radiusMeters) {
                notificationService.notifyUser(user.getEmail(), message);
                notified++;
            }
        }

        notificationService.notify(
                "Emergency Alert",
                message + " Notified nearby users: " + notified,
                "EMERGENCY"
        );

        Map<String, Object> response = new HashMap<>();
        response.put("alertId", saved.getId());
        response.put("notifiedUsers", notified);
        response.put("radiusMeters", radiusMeters);
        response.put("liveUrl", liveUrl);
        response.put("audioActive", saved.isLiveAudioActive());
        response.put("videoActive", saved.isLiveVideoActive());
        response.put("location", Map.of(
                "latitude", request.latitude,
                "longitude", request.longitude,
                "accuracyMeters", request.accuracyMeters
        ));
        return ResponseEntity.ok(response);
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

    public static class TriggerRequest {
        public Double latitude;
        public Double longitude;
        public Double accuracyMeters;
        public Integer radiusMeters;
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
