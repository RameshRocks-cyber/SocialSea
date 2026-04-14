package com.socialsea.controller;

import com.socialsea.dto.AuthResponse;
import com.socialsea.dto.OtpSendResult;
import com.socialsea.dto.VerifyOtpRequest;
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

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            email = body.get("username");
        }

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        OtpSendResult result = otpService.sendOtp(email);
        boolean deliveryFailed = result.isDeliveryFailed();

        if (exposeDebugOtp || (deliveryFailed && returnFallbackOtpOnDeliveryFailure)) {
            return ResponseEntity.ok(Map.of(
                    "message", deliveryFailed ? "OTP generated, but email delivery failed" : "OTP sent",
                    "deliveryFailed", deliveryFailed,
                    "failureReason", deliveryFailed ? result.getFailureReason() : "",
                    "debugOtp", result.getOtp()
            ));
        }

        if (deliveryFailed) {
            return ResponseEntity.ok(Map.of(
                    "message", "OTP generated, but email delivery failed",
                    "deliveryFailed", true,
                    "failureReason", result.getFailureReason()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "OTP sent",
                "deliveryFailed", false
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = resolveEmail(request.getEmail(), request.getUsername());
        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }
        String otp = request.getOtp();
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
