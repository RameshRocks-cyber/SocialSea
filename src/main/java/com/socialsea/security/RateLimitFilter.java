package com.socialsea.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.socialsea.service.RedisRateLimitService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> REAL_IP_HEADERS =
        List.of("CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP");

    private final RedisRateLimitService redisRateLimitService;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RedisRateLimitService redisRateLimitService) {
        this.redisRateLimitService = redisRateLimitService;
    }

    private Bucket createBucket(int capacity, Duration duration) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, duration)
                .build())
            .build();
    }

    private RateLimitRule ruleForPath(String path) {
        if ("/public/report".equals(path) || path.startsWith("/public/report/")) {
            return new RateLimitRule(8, Duration.ofMinutes(10));
        }
        if ("/api/anonymous/upload".equals(path) || "/anonymous/upload".equals(path)) {
            return new RateLimitRule(10, Duration.ofMinutes(10));
        }
        if (
            path.startsWith("/api/anonymous") ||
            path.startsWith("/anonymous") ||
            path.startsWith("/public/anonymous")
        ) {
            return new RateLimitRule(30, Duration.ofMinutes(1));
        }
        if (
            "/auth/send-otp".equals(path) ||
            "/api/auth/send-otp".equals(path) ||
            "/auth/forgot-password".equals(path) ||
            "/api/auth/forgot-password".equals(path) ||
            "/auth/forgotPassword".equals(path) ||
            "/api/auth/forgotPassword".equals(path)
        ) {
            return new RateLimitRule(5, Duration.ofMinutes(10));
        }
        if ("/api/auth/admin/login".equals(path)) {
            return new RateLimitRule(5, Duration.ofMinutes(10));
        }
        if ("/auth/login".equals(path) || "/api/auth/login".equals(path)) {
            return new RateLimitRule(10, Duration.ofMinutes(1));
        }
        if ("/api/auth/verify-otp".equals(path)) {
            return new RateLimitRule(10, Duration.ofMinutes(10));
        }
        if (
            "/api/auth/reset-password".equals(path) ||
            "/api/auth/resetPassword".equals(path) ||
            "/auth/reset-password".equals(path) ||
            "/auth/resetPassword".equals(path)
        ) {
            return new RateLimitRule(5, Duration.ofMinutes(10));
        }
        if (path.startsWith("/api/admin") || path.startsWith("/admin")) {
            return new RateLimitRule(60, Duration.ofMinutes(1));
        }
        return new RateLimitRule(120, Duration.ofMinutes(1));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        return HttpMethod.OPTIONS.matches(request.getMethod())
            || path.equals("/")
            || path.equals("/health")
            || path.startsWith("/actuator")
            || path.startsWith("/api/actuator")
            || path.startsWith("/uploads")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/h2-console")
            || path.startsWith("/ws")
            || path.startsWith("/api/ws");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws IOException, ServletException {

        String path = request.getRequestURI();
        String ip = resolveClientIp(request);
        String key = ip + "|" + path;

        RateLimitRule rule = ruleForPath(path);
        Optional<Boolean> redisAllowed = redisRateLimitService.tryConsume(key, rule.capacity, rule.window);
        if (redisAllowed.isPresent()) {
            if (redisAllowed.get()) {
                chain.doFilter(request, response);
            } else {
                response.setStatus(429);
                response.getWriter().write("Too many requests");
            }
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rule.capacity, rule.window));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.getWriter().write("Too many requests");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        for (String header : REAL_IP_HEADERS) {
            String value = request.getHeader(header);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (header.equalsIgnoreCase("X-Forwarded-For")) {
                String[] parts = value.split(",");
                if (parts.length > 0 && !parts[0].isBlank()) {
                    return parts[0].trim();
                }
            } else {
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static final class RateLimitRule {
        private final int capacity;
        private final Duration window;

        private RateLimitRule(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }
    }
}
