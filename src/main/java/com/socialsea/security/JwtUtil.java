package com.socialsea.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String CLAIM_TOKEN_USE = "token_use";
    private static final String TOKEN_USE_ACCESS = "access";
    private static final String TOKEN_USE_REFRESH = "refresh";

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:3600000}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpirationMs;

    private final Environment environment;

    private Key key;
    private static final String DEFAULT_SECRET = "socialsea_secret_key_123_secure_and_long_enough_for_hs256";

    public JwtUtil(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        boolean prod = environment != null && environment.acceptsProfiles("prod");
        String normalizedSecret = normalizeSecret(secret, prod);
        this.key = Keys.hmacShaKeyFor(normalizedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private final long CLOCK_SKEW_SECONDS = 300; // allow 5 minutes drift

    public String generateAccessToken(String username) {
        return generateAccessToken(username, null);
    }

    public String generateAccessToken(String username, String sessionId) {
        String subject = normalizeSubject(username);
        String sid = normalizeTokenId(sessionId);
        return Jwts.builder()
                .setSubject(subject)
                .setId(sid)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return generateRefreshToken(username, null);
    }

    public String generateRefreshToken(String username, String sessionId) {
        String subject = normalizeSubject(username);
        String sid = normalizeTokenId(sessionId);
        return Jwts.builder()
                .setSubject(subject)
                .setId(sid)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_REFRESH)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Compatibility for existing code
    public String generateToken(String email, String role) {
        return generateAccessToken(email);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractTokenId(String token) {
        return parseClaims(token).getId();
    }

    // Alias for compatibility
    public String extractEmail(String token) {
        return extractUsername(token);
    }

    public boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    // Alias for compatibility
    public boolean isTokenExpired(String token) {
        return isExpired(token);
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String tokenUse = normalizeTokenUse(claims.get(CLAIM_TOKEN_USE, String.class));
            if (TOKEN_USE_REFRESH.equals(tokenUse)) {
                return true;
            }
            if (TOKEN_USE_ACCESS.equals(tokenUse)) {
                return false;
            }
            return isLikelyRefreshToken(claims);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeSecret(String configuredSecret, boolean prod) {
        String candidate = configuredSecret == null ? "" : configuredSecret.trim();
        if (prod && candidate.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET must be configured in prod profile");
        }

        if (candidate.isEmpty()) {
            log.warn("JWT secret is missing; using development fallback secret");
            candidate = DEFAULT_SECRET;
        }

        byte[] bytes = candidate.getBytes(StandardCharsets.UTF_8);
        if (prod && bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes in prod profile");
        }
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

    private String normalizeTokenId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sessionId.trim();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private boolean isLikelyRefreshToken(Claims claims) {
        if (claims == null) {
            return false;
        }
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        if (issuedAt == null || expiration == null) {
            return false;
        }

        long lifetimeMs = expiration.getTime() - issuedAt.getTime();
        if (lifetimeMs <= 0L) {
            return false;
        }

        long accessLifetimeMs = Math.max(1L, accessExpirationMs);
        long refreshLifetimeMs = Math.max(accessLifetimeMs, refreshExpirationMs);
        if (refreshLifetimeMs <= accessLifetimeMs) {
            return false;
        }

        long midpointMs = accessLifetimeMs + Math.max(1L, (refreshLifetimeMs - accessLifetimeMs) / 2L);
        return lifetimeMs >= midpointMs;
    }

    private String normalizeTokenUse(String tokenUse) {
        if (tokenUse == null) {
            return "";
        }
        return tokenUse.trim().toLowerCase();
    }
}

