package com.socialsea.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/auth")
            || path.startsWith("/anonymous")
            || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                if (!jwtUtil.isTokenExpired(token)) {
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);

                    // Optional: Check if user is banned
                    // User user = userRepository.findByEmail(email).orElse(null);
                    // if (user != null && user.isBanned()) { ... }

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
