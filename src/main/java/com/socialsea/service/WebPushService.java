package com.socialsea.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialsea.model.WebPushSubscription;
import com.socialsea.repository.WebPushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private static final int MAX_RECIPIENT_LEN = 255;
    private static final int MAX_ENDPOINT_LEN = 2048;
    private static final int MAX_USER_AGENT_LEN = 512;
    private static final int MAX_KEY_LEN = 512;
    private static final int MAX_TYPE_LEN = 120;
    private static final int MAX_TITLE_LEN = 180;
    private static final int MAX_BODY_LEN = 1200;

    private final WebPushSubscriptionRepository subscriptionRepo;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushService(
            WebPushSubscriptionRepository subscriptionRepo,
            ObjectMapper objectMapper,
            @Value("${app.web-push.public-key:}") String publicKey,
            @Value("${app.web-push.private-key:}") String privateKey,
            @Value("${app.web-push.subject:mailto:support@socialsea.co.in}") String subject
    ) {
        this.subscriptionRepo = subscriptionRepo;
        this.objectMapper = objectMapper;
        this.publicKey = safe(publicKey);
        this.privateKey = safe(privateKey);
        this.subject = safe(subject);
    }

    public boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Transactional
    public void upsertSubscription(
            String recipient,
            String endpoint,
            String p256dh,
            String auth,
            String userAgent,
            Long expirationTime
    ) {
        String safeRecipient = normalizeRecipient(recipient);
        String safeEndpoint = clip(endpoint, MAX_ENDPOINT_LEN);
        String safeP256dh = clip(p256dh, MAX_KEY_LEN);
        String safeAuth = clip(auth, MAX_KEY_LEN);
        if (safeRecipient.isBlank() || safeEndpoint.isBlank() || safeP256dh.isBlank() || safeAuth.isBlank()) {
            return;
        }

        Optional<WebPushSubscription> existingOpt = subscriptionRepo.findByEndpoint(safeEndpoint);
        WebPushSubscription item = existingOpt.orElseGet(WebPushSubscription::new);
        item.setRecipient(clip(safeRecipient, MAX_RECIPIENT_LEN));
        item.setEndpoint(safeEndpoint);
        item.setP256dh(safeP256dh);
        item.setAuth(safeAuth);
        item.setUserAgent(clip(userAgent, MAX_USER_AGENT_LEN));
        item.setExpirationTime(expirationTime);
        item.setActive(true);
        subscriptionRepo.save(item);
    }

    @Transactional
    public void deactivateSubscription(String recipient, String endpoint) {
        String safeRecipient = normalizeRecipient(recipient);
        String safeEndpoint = clip(endpoint, MAX_ENDPOINT_LEN);
        if (safeRecipient.isBlank() || safeEndpoint.isBlank()) return;
        subscriptionRepo.deactivateByRecipientAndEndpoint(safeRecipient, safeEndpoint);
    }

    public void sendToRecipient(String recipient, String title, String message, String type) {
        if (!isConfigured()) return;
        String safeRecipient = normalizeRecipient(recipient);
        if (safeRecipient.isBlank()) return;

        List<WebPushSubscription> subscriptions = subscriptionRepo.findByRecipientIgnoreCaseAndActiveTrue(safeRecipient);
        if (subscriptions.isEmpty()) return;

        String payload = buildPayload(title, message, type);
        PushService pushService;
        try {
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (GeneralSecurityException ex) {
            log.warn("Web push is configured but failed to initialize push service: {}", ex.getMessage());
            return;
        }

        for (WebPushSubscription sub : subscriptions) {
            if (sub == null || !sub.isActive()) continue;
            String endpoint = safe(sub.getEndpoint());
            String p256dh = safe(sub.getP256dh());
            String auth = safe(sub.getAuth());
            if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) continue;

            try {
                Notification notification = new Notification(endpoint, p256dh, auth, payload);
                HttpResponse response = pushService.send(notification);
                int status = response != null && response.getStatusLine() != null
                        ? response.getStatusLine().getStatusCode()
                        : 0;
                if (status == 404 || status == 410) {
                    disableSubscription(sub);
                }
            } catch (IOException | GeneralSecurityException | JoseException | ExecutionException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                String msg = safe(ex.getMessage()).toLowerCase(Locale.ROOT);
                if (msg.contains("410") || msg.contains("404") || msg.contains("gone") || msg.contains("not found")) {
                    disableSubscription(sub);
                } else {
                    log.debug("Web push send failed for recipient {} endpoint {}: {}", safeRecipient, endpoint, ex.getMessage());
                }
            }
        }
    }

    private void disableSubscription(WebPushSubscription sub) {
        try {
            sub.setActive(false);
            subscriptionRepo.save(sub);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private String buildPayload(String title, String message, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", clip(title, MAX_TITLE_LEN).isBlank() ? "SocialSea" : clip(title, MAX_TITLE_LEN));
        payload.put("body", clip(message, MAX_BODY_LEN).isBlank() ? "You have a new notification." : clip(message, MAX_BODY_LEN));
        payload.put("message", clip(message, MAX_BODY_LEN));
        payload.put("type", clip(type, MAX_TYPE_LEN));
        payload.put("url", "/notifications");
        payload.put("icon", "/logo.png");
        payload.put("badge", "/logo-clean-round.png");
        payload.put("timestamp", Instant.now().toEpochMilli());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"title\":\"SocialSea\",\"body\":\"You have a new notification.\",\"url\":\"/notifications\"}";
        }
    }

    private String normalizeRecipient(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String clip(String value, int maxLen) {
        String safe = safe(value);
        if (safe.length() <= maxLen) return safe;
        return safe.substring(0, maxLen);
    }
}

