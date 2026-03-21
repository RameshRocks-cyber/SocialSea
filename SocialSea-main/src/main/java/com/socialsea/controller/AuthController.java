package com.socialsea.controller;

import com.socialsea.dto.VerifyOtpRequest;
import com.socialsea.model.LoginRequest;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AuthService;
import com.socialsea.service.OtpService;
import com.socialsea.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            email = body.get("username");
        }
        
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        String otp = null;
        try {
            otp = otpService.sendOtp(email);
        } catch (RuntimeException ex) {
            // Keep login usable even when email provider is down.
            // OTP is generated/saved before email call in service flow.
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent");
        if (otp != null) {
            response.put("debugOtp", otp);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = request.getUsername();
        }
        String otp = request.getOtp();

        if (email == null || otp == null) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("message", "Email or OTP missing"));
        }

        return ResponseEntity.ok(authService.verifyOtp(email, otp, httpRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String identifier = normalize(body.get("identifier"));
        if (identifier == null) {
            identifier = normalize(body.get("username"));
        }
        String email = normalize(body.get("email"));
        if (identifier == null) {
            identifier = email;
        }
        String password = normalize(body.get("password"));

        if (identifier == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username/email and password are required"));
        }

        User user = findUserByIdentifier(identifier).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            if (!legacyPasswordMatchAndUpgrade(user, password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
            }
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "user", user
        ));
    }

    @PostMapping({"/reset-password", "/resetPassword"})
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String identifier = normalize(body.get("identifier"));
        if (identifier == null) {
            identifier = normalize(body.get("email"));
        }
        if (identifier == null) {
            identifier = normalize(body.get("username"));
        }

        String otp = normalize(body.get("otp"));
        String password = normalize(body.get("newPassword"));
        if (password == null) {
            password = normalize(body.get("new_password"));
        }
        if (password == null) {
            password = normalize(body.get("password"));
        }

        if (identifier == null || otp == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Identifier, OTP and new password are required"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }

        Optional<User> userOpt = findUserByIdentifier(identifier);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();
        String otpEmail = normalize(user.getEmail());
        if (otpEmail == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "User has no email for OTP verification"));
        }

        try {
            otpService.verifyOtp(otpEmail, otp);
        } catch (Exception ex) {
            String msg = normalize(ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", msg != null ? msg : "Invalid or expired OTP"));
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(identifier);
    }

    private boolean legacyPasswordMatchAndUpgrade(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || rawPassword == null) return false;
        String trimmedStored = stored.trim();
        boolean looksBcrypt = trimmedStored.startsWith("$2a$") || trimmedStored.startsWith("$2b$") || trimmedStored.startsWith("$2y$");
        if (!looksBcrypt && rawPassword.equals(stored)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody LoginRequest request) {

        User admin = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getPassword() == null || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        if (admin.getRole() != Role.ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not an admin");
        }

        String accessToken = jwtUtil.generateAccessToken(admin.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(admin.getEmail());

        return ResponseEntity.ok(Map.of(
                "role", "ADMIN",
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Refresh token missing");
        }

        try {
            if (jwtUtil.isExpired(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Refresh token expired");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Refresh token invalid");
        }

        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Refresh token invalid");
        }

        String newAccessToken = jwtUtil.generateAccessToken(username);

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }
}
