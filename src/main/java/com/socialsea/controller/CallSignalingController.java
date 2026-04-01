package com.socialsea.controller;

import com.socialsea.dto.CallSignalDto;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class CallSignalingController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "offer", "answer", "ice", "hangup", "reject", "busy", "ringing",
            "livekit-invite", "livekit-accept",
            "connected", "refreshing", "ended", "accepted"
    );

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping({"/call.signal/{targetUserId}", "/call.signal.{targetUserId}"})
    public void signal(
            @DestinationVariable Long targetUserId,
            @Payload CallSignalDto payload,
            Authentication auth
    ) {
        if (auth == null || !auth.isAuthenticated() || targetUserId == null || payload == null) return;

        User sender = userRepository.findByEmail(auth.getName()).orElse(null);
        if (sender == null || sender.getId() == null) return;
        if (sender.getId().equals(targetUserId)) return;

        User target = userRepository.findById(targetUserId).orElse(null);
        if (target == null || target.getId() == null) return;

        String type = normalize(payload.getType());
        if (!ALLOWED_TYPES.contains(type)) return;

        CallSignalDto outbound = new CallSignalDto();
        outbound.setType(type);
        outbound.setFromUserId(sender.getId());
        outbound.setToUserId(target.getId());
        outbound.setFromName(displayName(sender));
        outbound.setFromEmail(sender.getEmail());
        outbound.setMode(normalizeMode(payload.getMode()));
        outbound.setRoomId(payload.getRoomId());
        outbound.setGroup(payload.isGroup());
        outbound.setGroupMembers(payload.getGroupMembers());
        outbound.setSdp(payload.getSdp());
        outbound.setCandidate(payload.getCandidate());
        outbound.setSdpMid(payload.getSdpMid());
        outbound.setSdpMLineIndex(payload.getSdpMLineIndex());
        outbound.setTimestamp(System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/calls/" + target.getId(), outbound);
        String targetEmail = target.getEmail();
        if (targetEmail != null && !targetEmail.isBlank()) {
            String encodedEmail = URLEncoder.encode(targetEmail, StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/calls/email/" + encodedEmail, outbound);
            messagingTemplate.convertAndSendToUser(
                    Objects.requireNonNull(targetEmail),
                    "/queue/calls",
                    Objects.requireNonNull(outbound)
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeMode(String mode) {
        String normalized = normalize(mode);
        return "video".equals(normalized) ? "video" : "audio";
    }

    private String displayName(User user) {
        if (user == null) return "User";
        String name = user.getName();
        if (name != null && !name.isBlank()) return name.trim();
        String email = user.getEmail();
        if (email != null && !email.isBlank()) return email.trim();
        return "User";
    }
}
