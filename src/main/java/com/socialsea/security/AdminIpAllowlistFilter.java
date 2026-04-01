package com.socialsea.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminIpAllowlistFilter extends OncePerRequestFilter {

    private static final List<String> REAL_IP_HEADERS =
        List.of("CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP");

    private final Set<String> allowedIps;
    private final String adminSecret;
    private final boolean hideAdmin;

    public AdminIpAllowlistFilter(
        @Value("${app.admin.allowed-ips:}") String allowedIps,
        @Value("${app.admin.secret:}") String adminSecret,
        @Value("${app.admin.hide:true}") boolean hideAdmin
    ) {
        this.allowedIps = parseAllowedIps(allowedIps);
        this.adminSecret = normalize(adminSecret);
        this.hideAdmin = hideAdmin;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/admin")
            || path.startsWith("/admin")
            || path.startsWith("/api/auth/admin"));
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws IOException, ServletException {

        if (adminSecret != null) {
            String header = normalize(request.getHeader("X-Admin-Secret"));
            if (!adminSecret.equals(header)) {
                deny(response);
                return;
            }
        }

        if (!allowedIps.isEmpty()) {
            String clientIp = resolveClientIp(request);
            if (!allowedIps.contains(clientIp)) {
                deny(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Set<String> parseAllowedIps(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
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

    private void deny(HttpServletResponse response) throws IOException {
        if (hideAdmin) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Not found");
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Admin access restricted");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
