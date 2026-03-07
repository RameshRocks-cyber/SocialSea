package com.socialsea.controller;

import com.socialsea.dto.AuthResponse;
import com.socialsea.dto.OtpSendResult;
import com.socialsea.dto.VerifyOtpRequest;
import com.socialsea.service.AuthService;
import com.socialsea.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class OtpController {

    private final OtpService otpService;
    private final AuthService authService;
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

        AuthResponse response = authService.verifyOtp(email, otp, httpRequest);

        return ResponseEntity.ok(response);
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

        AuthResponse response = authService.verifyOtp(email, otp, httpRequest);

        return ResponseEntity.ok(response);
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
