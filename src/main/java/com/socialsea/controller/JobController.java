package com.socialsea.controller;

import com.socialsea.model.JobOpening;
import com.socialsea.model.User;
import com.socialsea.repository.JobOpeningRepository;
import com.socialsea.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "${app.security.allowed-origins}")
@Transactional
public class JobController {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int DEFAULT_DURATION_DAYS = 30;

    private final JobOpeningRepository jobRepo;
    private final UserRepository userRepo;

    public JobController(JobOpeningRepository jobRepo, UserRepository userRepo) {
        this.jobRepo = jobRepo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(name = "includeExpired", defaultValue = "false") boolean includeExpired,
            @RequestParam(name = "includeClosed", defaultValue = "false") boolean includeClosed
    ) {
        List<JobOpening> source = jobRepo.findAllByOrderByCreatedAtDesc();

        List<Map<String, Object>> items = source.stream()
                .filter(job -> includeClosed || "open".equals(normalizeStatus(job.getStatus())))
                .filter(job -> includeExpired || !isExpired(job))
                .map(this::toPayload)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(
            @RequestParam(name = "includeExpired", defaultValue = "true") boolean includeExpired,
            @RequestParam(name = "includeClosed", defaultValue = "true") boolean includeClosed,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        List<Map<String, Object>> items = jobRepo.findByOwnerOrderByCreatedAtDesc(me).stream()
                .filter(job -> includeClosed || "open".equalsIgnoreCase(normalizeStatus(job.getStatus())))
                .filter(job -> includeExpired || !isExpired(job))
                .map(this::toPayload)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> payload, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (payload == null) return ResponseEntity.badRequest().body(Map.of("message", "payload required"));

        String title = text(payload.get("title"));
        if (title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "title required"));
        }

        JobOpening job = new JobOpening();
        long now = System.currentTimeMillis();
        String id = text(payload.get("id"));
        if (id.isBlank()) {
            id = "job-" + UUID.randomUUID();
        }
        if (jobRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "job id already exists"));
        }
        job.setId(id);
        applyPayload(job, payload, now);
        job.setOwner(me);
        job.setCreatedAt(number(payload.get("createdAt"), now));
        job.setUpdatedAt(number(payload.get("updatedAt"), now));
        if (job.getExpiresAt() == null || job.getExpiresAt() <= 0) {
            int duration = positiveInt(job.getDurationDays(), DEFAULT_DURATION_DAYS);
            job.setDurationDays(duration);
            job.setExpiresAt(job.getCreatedAt() + duration * DAY_MS);
        }

        JobOpening saved = jobRepo.save(job);
        return ResponseEntity.ok(toPayload(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> payload,
            Authentication auth
    ) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (payload == null) return ResponseEntity.badRequest().body(Map.of("message", "payload required"));

        String safeId = text(id);
        JobOpening job = jobRepo.findById(safeId).orElse(null);
        if (job == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Job not found"));
        if (job.getOwner() == null || !Objects.equals(job.getOwner().getId(), me.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        long now = System.currentTimeMillis();
        applyPayload(job, payload, now);
        job.setUpdatedAt(number(payload.get("updatedAt"), now));
        if (job.getCreatedAt() == null || job.getCreatedAt() <= 0) {
            job.setCreatedAt(now);
        }

        if (containsAny(payload, "durationDays", "expiresAt")) {
            int duration = positiveInt(job.getDurationDays(), DEFAULT_DURATION_DAYS);
            job.setDurationDays(duration);
            if (job.getExpiresAt() == null || job.getExpiresAt() <= 0) {
                job.setExpiresAt(job.getCreatedAt() + duration * DAY_MS);
            }
        }

        JobOpening saved = jobRepo.save(job);
        return ResponseEntity.ok(toPayload(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable("id") String id, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));

        String safeId = text(id);
        JobOpening job = jobRepo.findById(safeId).orElse(null);
        if (job == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Job not found"));
        if (job.getOwner() == null || !Objects.equals(job.getOwner().getId(), me.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not allowed"));
        }

        jobRepo.delete(job);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private void applyPayload(JobOpening job, Map<String, Object> payload, long now) {
        if (payload.containsKey("title")) job.setTitle(text(payload.get("title")));
        if (payload.containsKey("companyId")) job.setCompanyId(text(payload.get("companyId")));
        if (payload.containsKey("companyName")) job.setCompanyName(text(payload.get("companyName")));
        if (payload.containsKey("location")) job.setLocation(text(payload.get("location")));
        if (payload.containsKey("salary")) job.setSalary(text(payload.get("salary")));
        if (payload.containsKey("experience")) job.setExperience(text(payload.get("experience")));
        if (payload.containsKey("track")) job.setTrack(text(payload.get("track")));
        if (payload.containsKey("description")) job.setDescription(text(payload.get("description")));
        if (payload.containsKey("applyUrl")) job.setApplyUrl(text(payload.get("applyUrl")));
        if (payload.containsKey("ownerKey")) job.setOwnerKey(text(payload.get("ownerKey")));
        if (payload.containsKey("status")) job.setStatus(normalizeStatus(text(payload.get("status"))));
        if (payload.containsKey("durationDays")) job.setDurationDays(positiveInt(number(payload.get("durationDays"), 0), DEFAULT_DURATION_DAYS));
        if (payload.containsKey("expiresAt")) job.setExpiresAt(number(payload.get("expiresAt"), 0));
        if (payload.containsKey("skills")) job.setSkills(cleanList(payload.get("skills")));
        if (payload.containsKey("responsibilities")) job.setResponsibilities(cleanList(payload.get("responsibilities")));
        if (payload.containsKey("requirements")) job.setRequirements(cleanList(payload.get("requirements")));
        if (payload.containsKey("benefits")) job.setBenefits(cleanList(payload.get("benefits")));
        if (job.getUpdatedAt() == null || job.getUpdatedAt() <= 0) {
            job.setUpdatedAt(now);
        }
    }

    private Map<String, Object> toPayload(JobOpening job) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", text(job.getId()));
        item.put("title", text(job.getTitle()));
        item.put("companyId", text(job.getCompanyId()));
        item.put("companyName", text(job.getCompanyName()));
        item.put("location", text(job.getLocation()));
        item.put("salary", text(job.getSalary()));
        item.put("experience", text(job.getExperience()));
        item.put("track", text(job.getTrack()));
        item.put("skills", detachedList(job.getSkills()));
        item.put("description", text(job.getDescription()));
        item.put("responsibilities", detachedList(job.getResponsibilities()));
        item.put("requirements", detachedList(job.getRequirements()));
        item.put("benefits", detachedList(job.getBenefits()));
        item.put("applyUrl", text(job.getApplyUrl()));
        item.put("ownerKey", text(job.getOwnerKey()));
        item.put("status", normalizeStatus(job.getStatus()));
        item.put("durationDays", positiveInt(job.getDurationDays(), DEFAULT_DURATION_DAYS));
        item.put("expiresAt", number(job.getExpiresAt(), 0));
        item.put("createdAt", number(job.getCreatedAt(), 0));
        item.put("updatedAt", number(job.getUpdatedAt(), 0));
        item.put("ownerId", job.getOwner() != null && job.getOwner().getId() != null ? String.valueOf(job.getOwner().getId()) : "");
        item.put("ownerEmail", job.getOwner() != null ? text(job.getOwner().getEmail()) : "");
        return item;
    }

    private User currentUser(Authentication auth) {
        if (auth == null) return null;
        String email = text(auth.getName());
        if (email.isBlank() || "anonymousUser".equalsIgnoreCase(email)) return null;
        return userRepo.findByEmail(email).orElse(null);
    }

    private boolean isExpired(JobOpening job) {
        long expiresAt = number(job.getExpiresAt(), 0);
        return expiresAt > 0 && expiresAt <= System.currentTimeMillis();
    }

    private String normalizeStatus(String value) {
        String raw = text(value).toLowerCase(Locale.ROOT);
        if ("paused".equals(raw) || "closed".equals(raw)) return raw;
        return "open";
    }

    private String text(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private long number(Object value, long fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int positiveInt(long value, int fallback) {
        if (value <= 0) return fallback;
        if (value > Integer.MAX_VALUE) return fallback;
        return (int) value;
    }

    private List<String> cleanList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object item : c) {
                String normalized = text(item);
                if (!normalized.isBlank()) out.add(normalized);
            }
            return out;
        }
        String raw = text(value);
        if (raw.isBlank()) return new ArrayList<>();
        String[] pieces = raw.split("[\\n,]");
        List<String> out = new ArrayList<>();
        for (String piece : pieces) {
            String normalized = text(piece);
            if (!normalized.isBlank()) out.add(normalized);
        }
        return out;
    }

    private boolean containsAny(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) return true;
        }
        return false;
    }

    private List<String> detachedList(List<String> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(source);
    }
}
