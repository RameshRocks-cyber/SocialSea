package com.socialsea.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:socialsea_secret_key_123_secure_and_long_enough_for_hs256}")
    private String secret;

    private Key key;
    private static final String DEFAULT_SECRET = "socialsea_secret_key_123_secure_and_long_enough_for_hs256";

    @PostConstruct
    public void init() {
        String normalizedSecret = normalizeSecret(secret);
        this.key = Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private final long ACCESS_EXP = 1000 * 60 * 60 * 24 * 7; // 7 days
    private final long REFRESH_EXP = 1000 * 60 * 60 * 24 * 30; // 30 days
    private final long CLOCK_SKEW_SECONDS = 300; // allow 5 minutes drift

    public String generateAccessToken(String username) {
        String subject = normalizeSubject(username);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXP))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        String subject = normalizeSubject(username);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXP))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Compatibility for existing code
    public String generateToken(String email, String role) {
        return generateAccessToken(email);
    }

    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Alias for compatibility
    public String extractEmail(String token) {
        return extractUsername(token);
    }

    public boolean isExpired(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
    }

    // Alias for compatibility
    public boolean isTokenExpired(String token) {
        return isExpired(token);
    }

    private String normalizeSecret(String configuredSecret) {
        String candidate = configuredSecret == null ? "" : configuredSecret.trim();
        if (candidate.isEmpty()) {
            candidate = DEFAULT_SECRET;
        }

        byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            StringBuilder sb = new StringBuilder(candidate);
            while (sb.toString().getBytes(StandardCharsets.UTF_8).length < 32) {
                sb.append('_');
            }
            candidate = sb.toString();
        }
        return candidate;
    }

    private String normalizeSubject(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT subject cannot be null or blank");
        }
        return username.trim();
    }
}

