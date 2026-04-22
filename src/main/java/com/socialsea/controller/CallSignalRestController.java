package com.socialsea.controller;

import com.socialsea.dto.CallSignalDto;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.CallSignalInboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/calls")
@CrossOrigin(origins = {
        "https://socialsea.netlify.app",
        "https://socialsea.co.in",
        "https://www.socialsea.co.in",
        "http://localhost:5173",
        "http://43.205.213.14:5173"
})
@RequiredArgsConstructor
public class CallSignalRestController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "offer", "answer", "ice", "hangup", "reject", "busy", "ringing",
            "livekit-invite", "livekit-accept",
            "connected", "refreshing", "ended", "accepted", "typing"
    );
    private static final Logger log = LoggerFactory.getLogger(CallSignalRestController.class);

    private final UserRepository userRepository;
    private final CallSignalInboxService inboxService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/signal/{targetUserId}")
    public ResponseEntity<?> signal(
            @PathVariable Long targetUserId,
            @RequestBody(required = false) CallSignalDto payload,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (payload == null) return ResponseEntity.badRequest().body(Map.of("message", "payload required"));
        if (targetUserId == null || targetUserId.equals(me.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid target"));
        }

        User target = userRepository.findById(targetUserId).orElse(null);
        if (target == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        String type = normalize(payload.getType());
        if (!ALLOWED_TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid signal type"));
        }

        CallSignalDto outbound = new CallSignalDto();
        outbound.setType(type);
        outbound.setTyping(payload.getTyping());
        outbound.setFromUserId(me.getId());
        outbound.setToUserId(target.getId());
        outbound.setFromName(displayName(me));
        outbound.setFromEmail(me.getEmail());
        outbound.setMode("video".equals(normalize(payload.getMode())) ? "video" : "audio");
        outbound.setRoomId(payload.getRoomId());
        outbound.setGroup(payload.isGroup());
        outbound.setGroupMembers(payload.getGroupMembers());
        outbound.setSdp(payload.getSdp());
        outbound.setCandidate(payload.getCandidate());
        outbound.setSdpMid(payload.getSdpMid());
        outbound.setSdpMLineIndex(payload.getSdpMLineIndex());
        outbound.setTimestamp(System.currentTimeMillis());

        inboxService.enqueue(target.getId(), outbound);
        String targetEmail = target.getEmail();
        if (targetEmail != null && !targetEmail.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    Objects.requireNonNull(targetEmail),
                    "/queue/calls",
                    Objects.requireNonNull(outbound)
            );
        } else {
            messagingTemplate.convertAndSend("/topic/calls/" + target.getId(), outbound);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/inbox")
    public ResponseEntity<?> inbox(
            @RequestParam(name = "includeTyping", defaultValue = "true") boolean includeTyping,
            Authentication auth
    ) {
        try {
            User me = currentUser(auth);
            if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
            List<CallSignalDto> list = includeTyping
                    ? inboxService.drain(me.getId())
                    : inboxService.drainNonTyping(me.getId());
            return ResponseEntity.ok(list);
        } catch (Exception ex) {
            log.error("Failed to load call inbox.", ex);
            return ResponseEntity.ok(List.of());
        }
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
