package com.socialsea.controller;

import com.socialsea.dto.AuthResponse;
import com.socialsea.dto.OtpSendResult;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AuthService;
import com.socialsea.service.DeviceSessionLimitException;
import com.socialsea.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class OtpController {

    private final OtpService otpService;
    private final AuthService authService;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${app.otp.expose-debug-otp:false}")
    private boolean exposeDebugOtp;

    @Value("${app.otp.return-fallback-otp-on-delivery-failure:true}")
    private boolean returnFallbackOtpOnDeliveryFailure;

    public OtpController(OtpService otpService, AuthService authService) {
        this.otpService = otpService;
        this.authService = authService;
    }

    @PostMapping({"/send-otp", "/forgot-password", "/forgotPassword"})
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String identifier = resolveIdentifier(body);
        if (identifier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email or mobile number is required"));
        }
        String normalizedPhone = normalizePhone(identifier);
        boolean isPhoneIdentifier = normalizedPhone != null && !identifier.contains("@");
        String otpKey = isPhoneIdentifier ? normalizedPhone : resolveEmailOtpTarget(identifier);
        if (!isPhoneIdentifier && otpKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", "Account not found",
                    "channel", "email",
                    "deliveryFailed", true,
                    "failureReason", "No email found for this account"
            ));
        }

        OtpSendResult result;
        try {
            result = isPhoneIdentifier ? otpService.sendOtpToPhone(otpKey) : otpService.sendOtp(otpKey);
        } catch (RuntimeException ex) {
            String reason = normalize(ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", isPhoneIdentifier ? "SMS provider unavailable" : "Email provider unavailable",
                    "channel", isPhoneIdentifier ? "sms" : "email",
                    "deliveryFailed", true,
                    "failureReason", reason != null ? reason : "OTP delivery provider error"
            ));
        }
        boolean deliveryFailed = result.isDeliveryFailed();

        if (exposeDebugOtp || (deliveryFailed && returnFallbackOtpOnDeliveryFailure)) {
            return ResponseEntity.ok(Map.of(
                    "message", deliveryFailed
                            ? (isPhoneIdentifier ? "OTP generated, but SMS delivery is not configured yet" : "OTP generated, but email delivery failed")
                            : "OTP sent",
                    "channel", isPhoneIdentifier ? "sms" : "email",
                    "deliveryFailed", deliveryFailed,
                    "failureReason", deliveryFailed ? result.getFailureReason() : "",
                    "debugOtp", result.getOtp()
            ));
        }

        if (deliveryFailed) {
            return ResponseEntity.ok(Map.of(
                    "message", isPhoneIdentifier ? "OTP generated, but SMS delivery is not configured yet" : "OTP generated, but email delivery failed",
                    "channel", isPhoneIdentifier ? "sms" : "email",
                    "deliveryFailed", true,
                    "failureReason", result.getFailureReason()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "OTP sent",
                "channel", isPhoneIdentifier ? "sms" : "email",
                "deliveryFailed", false
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        String identifier = resolveIdentifier(body);
        if (identifier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email or mobile number is required"));
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            otp = body.get("code");
        }
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is required"));
        }

        String normalizedPhone = normalizePhone(identifier);
        String otpKey = (normalizedPhone != null && !identifier.contains("@"))
                ? normalizedPhone
                : identifier.toLowerCase(Locale.ROOT);

        try {
            AuthResponse response = authService.verifyOtp(otpKey, otp, httpRequest);
            return ResponseEntity.ok(response);
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        String email = resolveEmail(body.get("email"), body.get("username"));
        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP is required"));
        }

        try {
            AuthResponse response = authService.verifyOtp(email, otp, httpRequest);
            return ResponseEntity.ok(response);
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
    }

    private String resolveIdentifier(Map<String, String> body) {
        String identifier = resolveEmail(body.get("identifier"), body.get("email"));
        if (identifier == null) identifier = resolveEmail(body.get("username"), null);
        if (identifier == null) identifier = resolveEmail(body.get("phoneNumber"), null);
        if (identifier == null) identifier = resolveEmail(body.get("mobileNumber"), null);
        if (identifier == null) identifier = resolveEmail(body.get("phone"), null);
        return identifier;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identifier);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        String normalizedPhone = normalizePhone(identifier);
        if (normalizedPhone != null) {
            Optional<User> byPhone = userRepository.findByPhoneNumber(normalizedPhone);
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }
        return userRepository.findByNameIgnoreCase(identifier);
    }

    private String resolveEmailOtpTarget(String identifier) {
        String normalized = normalize(identifier);
        if (normalized == null) {
            return null;
        }
        if (normalized.contains("@")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        User user = findUserByIdentifier(normalized).orElse(null);
        if (user == null) {
            return null;
        }
        String email = normalize(user.getEmail());
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        String compact = trimmed.replaceAll("[\\s\\-()]", "");
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (compact.startsWith("+")) {
            String rest = compact.substring(1).replaceAll("[^0-9]", "");
            if (rest.length() < 6 || rest.length() > 15) return null;
            return "+" + rest;
        }
        String digits = compact.replaceAll("[^0-9]", "");
        if (digits.length() < 6 || digits.length() > 15) return null;
        return digits;
    }

    @PostMapping({"/reset-password", "/resetPassword"})
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String identifier = resolveEmail(body.get("identifier"), body.get("email"));
        if (identifier == null) {
            identifier = body.get("username");
        }
        if (identifier != null) {
            identifier = identifier.trim();
            if (identifier.isBlank()) {
                identifier = null;
            }
        }

        String otp = body.get("otp");
        String password = body.get("newPassword");
        if (password == null || password.isBlank()) {
            password = body.get("new_password");
        }
        if (password == null || password.isBlank()) {
            password = body.get("password");
        }

        if (otp != null) {
            otp = otp.trim();
            if (otp.isBlank()) {
                otp = null;
            }
        }
        if (password != null) {
            password = password.trim();
            if (password.isBlank()) {
                password = null;
            }
        }

        if (identifier == null || otp == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Identifier, OTP and new password are required"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }

        Optional<User> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByNameIgnoreCase(identifier);
        }
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();
        String otpEmail = user.getEmail();
        if (otpEmail == null || otpEmail.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "User has no email for OTP verification"));
        }

        try {
            otpService.verifyOtp(otpEmail.trim(), otp);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", (msg != null && !msg.isBlank()) ? msg : "Invalid or expired OTP"));
        }

        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    private String resolveEmail(String email, String username) {
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (username != null && !username.isBlank()) {
            return username;
        }
        return null;
    }
}
