package com.socialsea.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${app.upload.provider:s3}")
    private String uploadProvider;

    @Value("${app.upload.allow-local-fallback:false}")
    private boolean allowLocalFallback;

    @Value("${app.upload.serve-local:false}")
    private boolean serveLocalUploads;

    @Value("${app.s3.bucket:}")
    private String s3Bucket;

    @Value("${cloudinary.cloud_name:}")
    private String cloudinaryCloudName;

    private final Environment environment;

    public HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    public Object health(@RequestParam(name = "debug", defaultValue = "false") boolean debug) {
        if (!debug) {
            return "OK";
        }

        String provider = normalizeProvider(uploadProvider);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "OK");
        payload.put("uploadProvider", provider);
        payload.put("uploadAllowLocalFallback", allowLocalFallback);
        payload.put("uploadServeLocal", serveLocalUploads);
        payload.put("prodProfileActive", isProdProfileActive());
        payload.put("s3BucketConfigured", hasText(s3Bucket));
        payload.put("cloudinaryConfigured", hasText(cloudinaryCloudName));
        return payload;
    }

    private String normalizeProvider(String raw) {
        if (!hasText(raw)) {
            return "s3";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
            .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
            .anyMatch("prod"::equals);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
