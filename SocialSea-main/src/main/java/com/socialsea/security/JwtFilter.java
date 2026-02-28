package com.socialsea.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/") ||
            path.equals("/health") ||
            path.startsWith("/auth") ||
            path.startsWith("/api/auth") ||
            path.startsWith("/api/v1/auth") ||
            path.startsWith("/api/anonymous") ||
            path.startsWith("/anonymous") ||
            path.startsWith("/uploads") ||
            path.startsWith("/actuator") ||
            path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || token.equalsIgnoreCase("null") || token.equalsIgnoreCase("undefined")) {
            filterChain.doFilter(request, response);
            return;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtUtil.extractEmail(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!jwtUtil.isTokenExpired(token) && username.equals(userDetails.getUsername())) {

                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                        );

                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (Exception e) {
            // ignore malformed/expired tokens
            System.out.println("JWT verification failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
