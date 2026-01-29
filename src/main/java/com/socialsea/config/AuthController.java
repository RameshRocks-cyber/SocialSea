package com.socialsea.controller;

import com.socialsea.service.EmailService;
import com.socialsea.service.OtpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin("https://socialsea.netlify.app")
public class AuthController {

    private final EmailService emailService;
    private final OtpService otpService;

    public AuthController(EmailService emailService, OtpService otpService) {
        this.emailService = emailService;
        this.otpService = otpService;
    }

    @PostMapping("/auth/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email required");
        }

        try {
            String otp = otpService.generateOtp(email);
            emailService.sendOtpEmail(email, otp);
            return ResponseEntity.ok("OTP sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}