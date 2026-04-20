package com.socialsea.controller;

import com.socialsea.model.EmergencyAlert;
import com.socialsea.model.User;
import com.socialsea.repository.EmergencyAlertRepository;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class SosSignalingController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "rtc-offer", "rtc-answer", "rtc-candidate", "rtc-stop"
    );
    private static final int DEFAULT_RADIUS_METERS = 5000;

    private final SimpMessagingTemplate messagingTemplate;
    private final EmergencyAlertRepository emergencyRepo;
    private final UserRepository userRepo;

    @Value("${app.emergency.location-stale-minutes:180}")
    private long locationStaleMinutes;

    @MessageMapping({"/sos.signal/{alertId}", "/sos.signal.{alertId}"})
    public void signal(
            @DestinationVariable Long alertId,
            @Payload Map<String, Object> payload,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated() || alertId == null || payload == null) return;
        Optional<EmergencyAlert> alertOpt = emergencyRepo.findById(alertId);
        if (alertOpt.isEmpty()) return;
        EmergencyAlert alert = alertOpt.get();
        User viewer = userRepo.findByEmail(auth.getName()).orElse(null);
        if (!canSignalAlert(viewer, alert)) return;

        String type = String.valueOf(payload.getOrDefault("type", "")).trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) return;

        Map<String, Object> outbound = new HashMap<>(payload);
        outbound.put("type", type);
        outbound.put("alertId", alertId);
        outbound.put("fromEmail", auth.getName());
        outbound.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/sos/" + Objects.requireNonNull(alertId), outbound);
    }

    private boolean canSignalAlert(User viewer, EmergencyAlert alert) {
        if (viewer == null || alert == null || viewer.getEmail() == null || viewer.getEmail().isBlank()) return false;
        if (viewer.getEmail().equalsIgnoreCase(alert.getReporterEmail())) return true;
        Double viewerLat = viewer.getLastLatitude();
        Double viewerLon = viewer.getLastLongitude();
        LocalDateTime updatedAt = viewer.getLocationUpdatedAt();
        if (viewerLat == null || viewerLon == null || updatedAt == null) return false;
        long minutesOld = Duration.between(updatedAt, LocalDateTime.now()).toMinutes();
        if (minutesOld < 0 || minutesOld > locationStaleMinutes) return false;
        Double alertLat = alert.getCurrentLatitude() != null ? alert.getCurrentLatitude() : alert.getLatitude();
        Double alertLon = alert.getCurrentLongitude() != null ? alert.getCurrentLongitude() : alert.getLongitude();
        if (alertLat == null || alertLon == null) return false;
        int effectiveRadius = alert.getRadiusMeters() != null && alert.getRadiusMeters() > 0
                ? alert.getRadiusMeters()
                : DEFAULT_RADIUS_METERS;
        return haversineMeters(alertLat, alertLon, viewerLat, viewerLon) <= effectiveRadius;
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
