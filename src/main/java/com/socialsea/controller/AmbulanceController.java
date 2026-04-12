package com.socialsea.controller;

import com.socialsea.model.AmbulanceDriverRequest;
import com.socialsea.model.User;
import com.socialsea.repository.AmbulanceDriverRequestRepository;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ambulance")
@CrossOrigin(origins = "${app.security.allowed-origins}")
@RequiredArgsConstructor
public class AmbulanceController {

    private static final int DEFAULT_RADIUS_METERS = 500;
    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 3000;
    private static final int MAX_NOTE_LEN = 1200;
    private static final long DEFAULT_LOCATION_STALE_MINUTES = 30;
    private static final int MAX_RESOLVE_REDIRECTS = 6;
    private static final String MAPS_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Safari";

    private static final Pattern PAT_AT =
            Pattern.compile("@(-?\\d{1,3}(?:\\.\\d+)?),\\s*(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern PAT_Q =
            Pattern.compile("[?&]q=(-?\\d{1,3}(?:\\.\\d+)?),\\s*(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern PAT_DEST =
            Pattern.compile("[?&]destination=(-?\\d{1,3}(?:\\.\\d+)?),\\s*(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern PAT_3D4D =
            Pattern.compile("!3d(-?\\d{1,3}(?:\\.\\d+)?)!4d(-?\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern PAT_LOOSE =
            Pattern.compile("(-?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)");

    private final UserRepository userRepo;
    private final AmbulanceDriverRequestRepository requestRepo;
    private final NotificationService notificationService;

    @Value("${app.traffic.location-stale-minutes:30}")
    private long locationStaleMinutes;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        Optional<AmbulanceDriverRequest> latest = requestRepo.findTopByUserOrderByCreatedAtDesc(me);
        Map<String, Object> payload = new HashMap<>();
        payload.put("approved", me.isAmbulanceDriverApproved());
        payload.put("trafficAlertsEnabled", me.isTrafficAlertsEnabled());
        payload.put("request", latest.map(this::requestView).orElse(null));
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/request")
    @Transactional
    public ResponseEntity<?> requestApproval(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (me.isAmbulanceDriverApproved()) {
            return ResponseEntity.ok(Map.of("ok", true, "approved", true, "message", "Already approved"));
        }

        Optional<AmbulanceDriverRequest> latestOpt = requestRepo.findTopByUserOrderByCreatedAtDesc(me);
        if (latestOpt.isPresent() && latestOpt.get().getStatus() == AmbulanceDriverRequest.Status.PENDING) {
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "approved", false,
                    "request", requestView(latestOpt.get()),
                    "message", "Request already pending"
            ));
        }

        String driverName = clip(String.valueOf(body != null ? body.getOrDefault("driverName", "") : "").trim(), 120);
        String phone = clip(String.valueOf(body != null ? body.getOrDefault("phone", "") : "").trim(), 40);
        String vehicleNumber = clip(String.valueOf(body != null ? body.getOrDefault("vehicleNumber", "") : "").trim(), 80);
        String serviceName = clip(String.valueOf(body != null ? body.getOrDefault("serviceName", "") : "").trim(), 140);
        String note = clip(String.valueOf(body != null ? body.getOrDefault("note", "") : "").trim(), MAX_NOTE_LEN);

        AmbulanceDriverRequest req = new AmbulanceDriverRequest();
        req.setUser(me);
        req.setDriverName(driverName.isBlank() ? displayName(me) : driverName);
        req.setPhone(phone);
        req.setVehicleNumber(vehicleNumber);
        req.setServiceName(serviceName);
        req.setNote(note);
        req.setStatus(AmbulanceDriverRequest.Status.PENDING);
        req.setCreatedAt(LocalDateTime.now());
        AmbulanceDriverRequest saved = requestRepo.save(req);

        try {
            notificationService.notifyAdmin(
                    "Ambulance Driver Approval Request",
                    "New ambulance driver request from " + me.getEmail() +
                            (vehicleNumber.isBlank() ? "" : (" (vehicle: " + vehicleNumber + ")")) +
                            ". Review in Admin > Ambulance Requests.",
                    "SYSTEM"
            );
        } catch (Exception ignored) {
            // Request persistence already succeeded.
        }

        return ResponseEntity.ok(Map.of("ok", true, "approved", false, "request", requestView(saved)));
    }

    @PostMapping("/resolve")
    public ResponseEntity<?> resolveGoogleMapsLink(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (!me.isAmbulanceDriverApproved()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ambulance approval required"));
        }

        String url = String.valueOf(body != null ? body.getOrDefault("url", "") : "").trim();
        if (url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "url is required"));
        }

        URI initial;
        try {
            initial = URI.create(url);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid URL"));
        }

        String scheme = initial.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only http/https URLs are supported"));
        }
        if (!isAllowedGoogleHost(initial.getHost())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only Google Maps links are supported"));
        }

        double[] immediate = extractLatLng(url, true);
        if (immediate != null) {
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "lat", immediate[0],
                    "lng", immediate[1],
                    "finalUrl", url,
                    "sourceUrl", url
            ));
        }

        try {
            ResolvedCoords resolved = resolveCoords(initial);
            if (resolved == null) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "message",
                        "Could not extract coordinates from that link. Open it once and copy the full Google Maps URL (with @lat,lng), or paste lat,lng."
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "lat", resolved.lat,
                    "lng", resolved.lng,
                    "finalUrl", resolved.finalUrl,
                    "sourceUrl", resolved.sourceUrl
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Unable to resolve Google Maps link right now"));
        }
    }

    @PostMapping("/trip/start")
    public ResponseEntity<?> startTrip(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (!me.isAmbulanceDriverApproved()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ambulance approval required"));
        }

        String destinationLabel = String.valueOf(body != null ? body.getOrDefault("destinationLabel", "") : "").trim();
        Double destinationLat = toDouble(body != null ? body.get("destinationLat") : null);
        Double destinationLng = toDouble(body != null ? body.get("destinationLng") : null);
        String mapsUrl = buildMapsUrl(destinationLat, destinationLng);

        try {
            notificationService.notifyAdmin(
                    "Ambulance Trip Started",
                    "Ambulance navigation started by " + me.getEmail() +
                            (destinationLabel.isBlank() ? "" : (" to " + destinationLabel)) +
                            (mapsUrl == null ? "" : (" " + mapsUrl)),
                    "TRAFFIC"
            );
        } catch (Exception ignored) {
            // admin notification is best-effort
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/alert")
    public ResponseEntity<?> alertNearby(@RequestBody Map<String, Object> body, Authentication auth) {
        User me = currentUser(auth);
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        if (!me.isAmbulanceDriverApproved()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ambulance approval required"));
        }

        Double latitude = toDouble(body.get("latitude"));
        Double longitude = toDouble(body.get("longitude"));
        if (latitude == null || longitude == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "latitude and longitude are required"));
        }

        int radiusMeters = clampInt(
                toInt(body.get("radiusMeters"), DEFAULT_RADIUS_METERS),
                MIN_RADIUS_METERS,
                MAX_RADIUS_METERS
        );

        String reason = String.valueOf(body.getOrDefault("reason", "TRAFFIC")).trim().toUpperCase();
        String destinationLabel = String.valueOf(body.getOrDefault("destinationLabel", "")).trim();
        Double destinationLat = toDouble(body.get("destinationLat"));
        Double destinationLng = toDouble(body.get("destinationLng"));
        String mapsUrl = buildMapsUrl(destinationLat, destinationLng);
        String spotUrl = "https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude;

        String title = "Ambulance Nearby";
        String message =
                "Ambulance approaching nearby. Please give way." +
                        (destinationLabel.isBlank() ? "" : (" Destination: " + destinationLabel + ".")) +
                        (" Location: " + spotUrl) +
                        (mapsUrl == null ? "" : (" Route: " + mapsUrl));

        LocalDateTime now = LocalDateTime.now();
        long staleMinutes = locationStaleMinutes > 0 ? locationStaleMinutes : DEFAULT_LOCATION_STALE_MINUTES;
        int notified = 0;

        List<User> users = userRepo.findAll();
        for (User user : users) {
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
            if (!user.isTrafficAlertsEnabled()) continue;
            if (user.getId() != null && Objects.equals(user.getId(), me.getId())) continue;
            if (user.getLastLatitude() == null || user.getLastLongitude() == null || user.getLocationUpdatedAt() == null) continue;

            long minutesOld = Duration.between(user.getLocationUpdatedAt(), now).toMinutes();
            if (minutesOld < 0 || minutesOld > staleMinutes) continue;

            double distance = haversineMeters(latitude, longitude, user.getLastLatitude(), user.getLastLongitude());
            if (distance > radiusMeters) continue;

            try {
                notificationService.notifyUserInApp(user.getEmail(), title, message, "TRAFFIC");
                notified++;
            } catch (Exception ignored) {
                // keep notifying others
            }
        }

        try {
            notificationService.notifyAdmin(
                    "Ambulance Alert (" + reason + ")",
                    "Driver " + me.getEmail() + " triggered an alert. " +
                            "Notified users: " + notified + ". " +
                            "Radius: " + radiusMeters + "m. " +
                            "Spot: " + spotUrl +
                            (mapsUrl == null ? "" : (" Route: " + mapsUrl)),
                    "TRAFFIC"
            );
        } catch (Exception ignored) {
            // best-effort
        }

        return ResponseEntity.ok(Map.of("ok", true, "notified", notified, "radiusMeters", radiusMeters));
    }

    private Map<String, Object> requestView(AmbulanceDriverRequest request) {
        if (request == null) return null;
        Map<String, Object> row = new HashMap<>();
        row.put("id", request.getId());
        row.put("status", request.getStatus() != null ? request.getStatus().name() : "PENDING");
        row.put("driverName", request.getDriverName());
        row.put("phone", request.getPhone());
        row.put("vehicleNumber", request.getVehicleNumber());
        row.put("serviceName", request.getServiceName());
        row.put("note", request.getNote());
        row.put("createdAt", request.getCreatedAt());
        row.put("reviewedAt", request.getReviewedAt());
        row.put("reviewedBy", request.getReviewedBy());
        row.put("rejectReason", request.getRejectReason());
        return row;
    }

    private User currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepo.findByEmail(auth.getName()).orElse(null);
    }

    private String displayName(User user) {
        if (user == null) return "User";
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        return user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : "User";
    }

    private String clip(String value, int maxLen) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() <= maxLen) return v;
        return v.substring(0, maxLen);
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int toInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) *
                                Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String buildMapsUrl(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng + "&travelmode=driving";
    }

    private record ResolvedCoords(double lat, double lng, String sourceUrl, String finalUrl) {
    }

    private ResolvedCoords resolveCoords(URI initial) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        URI current = initial;
        String finalUrl = initial.toString();

        double[] best = extractLatLng(current.toString(), true);
        String bestUrl = best != null ? current.toString() : null;

        for (int i = 0; i < MAX_RESOLVE_REDIRECTS; i += 1) {
            HttpRequest req = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", MAPS_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            int status = res.statusCode();
            if (!isRedirect(status)) {
                finalUrl = current.toString();
                break;
            }

            Optional<String> location = res.headers().firstValue("location");
            if (location.isEmpty() || location.get().isBlank()) {
                finalUrl = current.toString();
                break;
            }

            URI next = current.resolve(location.get());
            if (!isAllowedGoogleHost(next.getHost())) {
                finalUrl = current.toString();
                break;
            }

            current = next;
            finalUrl = current.toString();

            double[] coords = extractLatLng(finalUrl, true);
            if (coords != null) {
                best = coords;
                bestUrl = finalUrl;
            }
        }

        if (best != null && bestUrl != null) {
            return new ResolvedCoords(best[0], best[1], bestUrl, finalUrl);
        }

        // As a fallback, fetch the final HTML once and scan for coordinate patterns.
        HttpClient bodyClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        HttpRequest req = HttpRequest.newBuilder(current)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", MAPS_USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> bodyRes = bodyClient.send(req, HttpResponse.BodyHandlers.ofString());
        String fetchedUrl = bodyRes.uri() != null ? bodyRes.uri().toString() : finalUrl;
        double[] coords = extractLatLng(fetchedUrl, true);
        if (coords != null) {
            return new ResolvedCoords(coords[0], coords[1], fetchedUrl, fetchedUrl);
        }
        coords = extractLatLng(bodyRes.body(), false);
        if (coords != null) {
            return new ResolvedCoords(coords[0], coords[1], fetchedUrl, fetchedUrl);
        }
        return null;
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean isAllowedGoogleHost(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase();
        return h.equals("google.com") || h.endsWith(".google.com") || h.equals("goo.gl") || h.endsWith(".goo.gl");
    }

    private double[] extractLatLng(String text, boolean allowLoose) {
        if (text == null || text.isBlank()) return null;

        double[] coords = matchLatLng(text, PAT_AT);
        if (coords != null) return coords;

        coords = matchLatLng(text, PAT_Q);
        if (coords != null) return coords;

        coords = matchLatLng(text, PAT_DEST);
        if (coords != null) return coords;

        coords = matchLatLng(text, PAT_3D4D);
        if (coords != null) return coords;

        if (!allowLoose) return null;

        coords = matchLatLng(text, PAT_LOOSE);
        return coords;
    }

    private double[] matchLatLng(String text, Pattern pattern) {
        if (text == null) return null;
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        Double lat = safeCoord(m.group(1), -90, 90);
        Double lng = safeCoord(m.group(2), -180, 180);
        if (lat == null || lng == null) return null;
        return new double[]{lat, lng};
    }

    private Double safeCoord(String raw, double min, double max) {
        if (raw == null) return null;
        try {
            double v = Double.parseDouble(raw);
            if (v < min || v > max) return null;
            return v;
        } catch (Exception ignored) {
            return null;
        }
    }
}
