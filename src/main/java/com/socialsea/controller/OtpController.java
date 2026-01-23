package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.security.JwtUtil;
import com.socialsea.service.EmailService;
import com.socialsea.service.OtpService;
import com.socialsea.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = {
    "http://localhost:5173",
    "https://socialsea.netlify.app"
})
public class OtpController {

    private final OtpService otpService;
    private final UserService userService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public OtpController(
            OtpService otpService,
            UserService userService,
            EmailService emailService,
            JwtUtil jwtUtil
    ) {
        this.otpService = otpService;
        this.userService = userService;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        try {
            String otp = otpService.generateOtp(email);
            emailService.sendOtpEmail(email, otp);
            return ResponseEntity.ok("OTP sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        try {
            // 1. Verify OTP
            otpService.verifyOtp(email, otp);

            // 2. Fetch User from DB (or create if new)
            User user = userService.getOrCreateVerifiedUser(email);

            // 3. Read Role from DB
            String role = user.getRole();

            // 4. Generate Token with DB Role
            String token = jwtUtil.generateToken(user.getEmail(), role);

            return ResponseEntity.ok(Map.of(
                    "message", "Email verified successfully",
                    "role", role,
                    "token", token
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
