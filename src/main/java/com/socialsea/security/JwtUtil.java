package com.socialsea.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // Using a secure key for HS256 (must be >= 32 bytes)
    private final String SECRET = "socialsea_secret_key_123_secure_and_long_enough_for_hs256";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    private final long ACCESS_EXP = 1000 * 60 * 15; // 15 min
    private final long REFRESH_EXP = 1000 * 60 * 60 * 24 * 7; // 7 days

    public String generateAccessToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXP))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
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
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // Alias for compatibility
    public String extractEmail(String token) {
        return extractUsername(token);
    }

    public boolean isExpired(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getExpiration().before(new Date());
    }

    // Alias for compatibility
    public boolean isTokenExpired(String token) {
        return isExpired(token);
    }
}
