package com.socialsea.security;

import com.socialsea.repository.BannedIPRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
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

        String ip = request.getRemoteAddr();

        if (bannedIPRepository.findByIpAddress(ip).isPresent()) {
            response.setStatus(403);
            response.getWriter().write("Your IP is banned");
            return;
        }

        filterChain.doFilter(request, response);
    }
}