package com.socialsea.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class SosSignalingController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "rtc-offer", "rtc-answer", "rtc-candidate", "rtc-stop"
    );

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping({"/sos.signal/{alertId}", "/sos.signal.{alertId}"})
    public void signal(
            @DestinationVariable Long alertId,
            @Payload Map<String, Object> payload,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated() || alertId == null || payload == null) return;
        String type = String.valueOf(payload.getOrDefault("type", "")).trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) return;

        Map<String, Object> outbound = new HashMap<>(payload);
        outbound.put("type", type);
        outbound.put("alertId", alertId);
        outbound.put("fromEmail", auth.getName());
        outbound.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/sos/" + Objects.requireNonNull(alertId), outbound);
    }
}
