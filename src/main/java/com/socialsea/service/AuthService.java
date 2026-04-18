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
import java.util.Optional;

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
    public AuthResponse verifyOtp(String identifier, String otp, HttpServletRequest request) {
        String normalizedIdentifier = normalize(identifier);
        if (normalizedIdentifier == null) {
            throw new IllegalArgumentException("Identifier is required");
        }

        otpService.verifyOtp(normalizedIdentifier, otp);
        String normalizedPhone = normalizePhone(normalizedIdentifier);
        boolean isPhone = normalizedPhone != null && !normalizedIdentifier.contains("@");

        User user;
        if (isPhone) {
            Optional<User> byPhone = userRepository.findByPhoneNumber(normalizedPhone);
            if (byPhone.isPresent()) {
                user = byPhone.get();
            } else {
                User newUser = new User();
                newUser.setPhoneNumber(normalizedPhone);
                newUser.setEmail(generateLocalEmailFromPhone(normalizedPhone));
                newUser.setRole(Role.USER);
                newUser.setCreatedAt(LocalDateTime.now());
                user = userRepository.save(newUser);
            }
        } else {
            String normalizedEmail = normalizedIdentifier.toLowerCase();
            user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(normalizedEmail);
                newUser.setRole(Role.USER);
                newUser.setCreatedAt(LocalDateTime.now());
                return userRepository.save(newUser);
            });
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            String fallbackLocal = isPhone ? generateLocalEmailFromPhone(normalizedPhone) : generateLocalEmailFromPhone("user");
            user.setEmail(fallbackLocal);
            user = userRepository.save(user);
        }

        var session = loginSessionService.startSession(user, request, null, null);
        String subject = normalize(user.getEmail());
        if (subject == null) {
            subject = isPhone ? normalizedPhone : normalizedIdentifier;
        }
        String token = jwtUtil.generateAccessToken(subject, session.getSessionId());
        String refreshToken = jwtUtil.generateRefreshToken(subject, session.getSessionId());
        return new AuthResponse(token, refreshToken, user, session.getDeviceId());
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        String compact = normalized.replaceAll("[\\s\\-()]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (compact.startsWith("+")) {
            String rest = compact.substring(1).replaceAll("[^0-9]", "");
            if (rest.length() < 6 || rest.length() > 15) return null;
            return "+" + rest;
        }
        String digits = compact.replaceAll("[^0-9]", "");
        if (digits.length() < 6 || digits.length() > 15) return null;
        return digits;
    }

    private String generateLocalEmailFromPhone(String phone) {
        String digits = String.valueOf(phone == null ? "user" : phone).replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            digits = "user";
        }
        String base = "phone" + digits;
        String candidate = base + "@socialsea.local";
        int suffix = 1;
        while (userRepository.findByEmail(candidate).isPresent()) {
            candidate = base + suffix + "@socialsea.local";
            suffix++;
        }
        return candidate;
    }
}
