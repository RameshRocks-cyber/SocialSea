package com.socialsea.controller;

import com.socialsea.model.AmbulanceDriverRequest;
import com.socialsea.model.User;
import com.socialsea.repository.AmbulanceDriverRequestRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/ambulance")
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
@CrossOrigin(origins = {"https://socialsea.netlify.app", "https://socialsea.co.in", "https://www.socialsea.co.in", "http://localhost:5173", "http://127.0.0.1:5173", "http://43.205.213.14:5173"})
@RequiredArgsConstructor
public class AdminAmbulanceController {

    private final AmbulanceDriverRequestRepository requestRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    @GetMapping("/requests")
    public ResponseEntity<?> listRequests(@RequestParam(name = "status", required = false) String statusRaw) {
        AmbulanceDriverRequest.Status status = null;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                status = AmbulanceDriverRequest.Status.valueOf(statusRaw.trim().toUpperCase());
            } catch (Exception ignored) {
                // ignore invalid status
            }
        }

        List<AmbulanceDriverRequest> items =
                status == null
                        ? requestRepo.findAll().stream().sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())).toList()
                        : requestRepo.findByStatusOrderByCreatedAtDesc(status);

        List<Map<String, Object>> out = items.stream().map(this::toView).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/requests/{id}/approve")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        AmbulanceDriverRequest req = requestRepo.findById(Objects.requireNonNull(id, "id")).orElse(null);
        if (req == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Request not found"));

        User user = req.getUser() != null && req.getUser().getId() != null
                ? userRepo.findById(req.getUser().getId()).orElse(null)
                : null;
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        user.setAmbulanceDriverApproved(true);
        userRepo.save(user);

        req.setStatus(AmbulanceDriverRequest.Status.APPROVED);
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewedBy(auth != null ? auth.getName() : null);
        req.setRejectReason(null);
        requestRepo.save(req);

        try {
            notificationService.notifyUserInApp(
                    user.getEmail(),
                    "Ambulance Driver Approved",
                    "Your ambulance driver request has been approved. You can now use Ambulance Navigation in Settings.",
                    "SYSTEM"
            );
        } catch (Exception ignored) {
            // best-effort
        }

        return ResponseEntity.ok(Map.of("ok", true, "request", toView(req)));
    }

    @PostMapping("/requests/{id}/reject")
    @Transactional
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth
    ) {
        AmbulanceDriverRequest req = requestRepo.findById(Objects.requireNonNull(id, "id")).orElse(null);
        if (req == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Request not found"));

        User user = req.getUser() != null && req.getUser().getId() != null
                ? userRepo.findById(req.getUser().getId()).orElse(null)
                : null;
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));

        String reason = String.valueOf(body != null ? body.getOrDefault("reason", "") : "").trim();
        if (reason.length() > 500) reason = reason.substring(0, 500);

        user.setAmbulanceDriverApproved(false);
        userRepo.save(user);

        req.setStatus(AmbulanceDriverRequest.Status.REJECTED);
        req.setReviewedAt(LocalDateTime.now());
        req.setReviewedBy(auth != null ? auth.getName() : null);
        req.setRejectReason(reason.isBlank() ? null : reason);
        requestRepo.save(req);

        try {
            notificationService.notifyUserInApp(
                    user.getEmail(),
                    "Ambulance Driver Request Rejected",
                    "Your ambulance driver request was rejected." + (reason.isBlank() ? "" : (" Reason: " + reason)),
                    "SYSTEM"
            );
        } catch (Exception ignored) {
            // best-effort
        }

        return ResponseEntity.ok(Map.of("ok", true, "request", toView(req)));
    }

    private Map<String, Object> toView(AmbulanceDriverRequest req) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", req.getId());
        row.put("status", req.getStatus() != null ? req.getStatus().name() : "PENDING");
        row.put("driverName", req.getDriverName());
        row.put("phone", req.getPhone());
        row.put("vehicleNumber", req.getVehicleNumber());
        row.put("serviceName", req.getServiceName());
        row.put("note", req.getNote());
        row.put("createdAt", req.getCreatedAt());
        row.put("reviewedAt", req.getReviewedAt());
        row.put("reviewedBy", req.getReviewedBy());
        row.put("rejectReason", req.getRejectReason());

        User u = req.getUser();
        if (u != null) {
            row.put("userId", u.getId());
            row.put("email", u.getEmail());
            row.put("name", u.getName());
            row.put("approved", u.isAmbulanceDriverApproved());
        }

        return row;
    }
}
