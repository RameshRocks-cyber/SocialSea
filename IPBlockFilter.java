package com.socialsea.security;

import com.socialsea.repository.BannedIPRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnProperty(
    name = "security.ipblock.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class IPBlockFilter extends OncePerRequestFilter {

    private final BannedIPRepository bannedIPRepository;

    public IPBlockFilter(BannedIPRepository bannedIPRepository) {
        this.bannedIPRepository = bannedIPRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response);
    }
}