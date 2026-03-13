package com.socialsea.controller;

import com.socialsea.dto.VerifyOtpRequest;
import com.socialsea.dto.AuthResponse;
import com.socialsea.dto.OtpSendResult;
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
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://socialsea.netlify.app",
    "https://socialsea.co.in",
    "https://www.socialsea.co.in", "http://localhost:5173", "http://43.205.213.14:5173"})
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

    @Value("${app.otp.expose-debug-otp:false}")
    private boolean exposeDebugOtp;

    @Value("${app.otp.return-fallback-otp-on-delivery-failure:true}")
    private boolean returnFallbackOtpOnDeliveryFailure;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = normalize(body.get("email"));
        if (email == null) {
            email = normalize(body.get("username"));
        }

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        OtpSendResult result = otpService.sendOtp(email);
        boolean deliveryFailed = result.isDeliveryFailed();

        Map<String, Object> response = new HashMap<>();
        response.put("message", deliveryFailed ? "OTP generated, but email delivery failed" : "OTP sent");
        response.put("deliveryFailed", deliveryFailed);
        if (deliveryFailed) {
            response.put("failureReason", result.getFailureReason());
        }
        if (exposeDebugOtp || (deliveryFailed && returnFallbackOtpOnDeliveryFailure)) {
            response.put("debugOtp", result.getOtp());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String email = normalize(request.getEmail());
        if (email == null) {
            email = normalize(request.getUsername());
        }
        String otp = normalize(request.getOtp());

        if (email == null || otp == null) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("message", "Email or OTP missing"));
        }

        return ResponseEntity.ok(authService.verifyOtp(email, otp, httpRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String username = normalize(body.get("username"));
        String email = normalize(body.get("email"));
        String password = normalize(body.get("password"));

        if (password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password is required"));
        }
        if (username == null && email == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username or email is required"));
        }

        if (email == null && username != null && username.contains("@")) {
            email = username;
        }

        User user = null;
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
            }
        }
        if (user == null && username != null) {
            Optional<User> byName = userRepository.findByNameIgnoreCase(username);
            if (byName.isPresent()) {
                user = byName.get();
            }
        }

        if (user != null && user.getPassword() != null && !user.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "User already registered"));
        }

        if (user == null) {
            user = new User();
            user.setRole(Role.USER);
            user.setCreatedAt(LocalDateTime.now());
        }

        if (email == null) {
            email = generateLocalEmail(username != null ? username : "user");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(email);
        } else if (!user.getEmail().equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already linked to another account"));
        }

        String preferredName = username;
        if (preferredName == null && email != null) {
            int idx = email.indexOf('@');
            preferredName = idx > 0 ? email.substring(0, idx) : email;
        }
        if (preferredName != null) {
            preferredName = preferredName.trim();
            if (!preferredName.isBlank()) {
                String availableName = ensureAvailableName(preferredName, user.getId());
                user.setName(availableName);
            }
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, user));
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

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, user));
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

        User user = findUserByIdentifier(identifier).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

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
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identifier);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return userRepository.findByNameIgnoreCase(identifier);
    }

    private boolean legacyPasswordMatchAndUpgrade(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || rawPassword == null) return false;
        String trimmedStored = stored.trim();
        // If stored password doesn't look like bcrypt, allow legacy match and upgrade.
        boolean looksBcrypt = trimmedStored.startsWith("$2a$") || trimmedStored.startsWith("$2b$") || trimmedStored.startsWith("$2y$");
        if (!looksBcrypt && rawPassword.equals(stored)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private String ensureAvailableName(String rawName, Long excludeId) {
        String base = rawName.trim();
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int i = 1;
        while (true) {
            Optional<User> existing = userRepository.findByNameIgnoreCase(candidate);
            if (existing.isEmpty() || (excludeId != null && excludeId.equals(existing.get().getId()))) {
                return candidate;
            }
            candidate = base + i;
            i++;
        }
    }

    private String generateLocalEmail(String username) {
        String base = String.valueOf(username == null ? "user" : username)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base + "@socialsea.local";
        int i = 1;
        while (userRepository.findByEmail(candidate).isPresent()) {
            candidate = base + i + "@socialsea.local";
            i++;
        }
        return candidate;
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

