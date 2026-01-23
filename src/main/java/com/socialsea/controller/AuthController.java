package com.socialsea.controller;

import com.socialsea.model.LoginRequest;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UserService;
import com.socialsea.service.OtpService;
import com.socialsea.service.EmailService;
import com.socialsea.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("https://socialsea.netlify.app")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        String otp = otpService.generateOtp(email);
        emailService.sendOtpEmail(email, otp);
        return ResponseEntity.ok("OTP sent");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestParam String email,
            @RequestParam String otp
    ) {
        try {
            otpService.verifyOtp(email, otp);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        User user = userService.getOrCreateVerifiedUser(email);
        String role = user.getRole();

        String token = jwtUtil.generateToken(user.getEmail(), role);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "role", role,
                "token", token
        ));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody LoginRequest request) {

        User admin = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getPassword() == null || !passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        if (!admin.getRole().contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not an admin");
        }

        String token = jwtUtil.generateToken(admin.getEmail(), "ADMIN");

        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", "ADMIN"
        ));
    }
}