package com.socialsea.service;

import com.socialsea.dto.AuthResponse;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final JwtUtil jwtUtil;
    private final LoginSessionService loginSessionService;

    public AuthService(
            UserRepository userRepository,
            OtpService otpService,
            JwtUtil jwtUtil,
            LoginSessionService loginSessionService
    ) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.jwtUtil = jwtUtil;
        this.loginSessionService = loginSessionService;
    }

    @Transactional
    public AuthResponse verifyOtp(String email, String otp, HttpServletRequest request) {
        otpService.verifyOtp(email, otp);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setRole(Role.USER);
            newUser.setCreatedAt(LocalDateTime.now());
            return userRepository.save(newUser);
        });

        var session = loginSessionService.startSession(user, request, null, null);
        String token = jwtUtil.generateAccessToken(user.getEmail(), session.getSessionId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), session.getSessionId());
        return new AuthResponse(token, refreshToken, user, session.getDeviceId());
    }
}
