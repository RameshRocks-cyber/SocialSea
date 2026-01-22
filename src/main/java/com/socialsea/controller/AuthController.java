package com.socialsea.controller;

import com.socialsea.model.LoginRequest;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.UserService;
import com.socialsea.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("https://socialsea.netlify.app")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // Temporary OTP storage (Use Redis in production for better scalability)
    private static final Map<String, String> otpStorage = new HashMap<>();

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        // Generate a 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Store OTP temporarily
        otpStorage.put(email, otp);

        // Send OTP via Email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your SocialSea OTP");
        message.setText("Your OTP code is: " + otp);
        mailSender.send(message);

        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestParam String email,
            @RequestParam String otp
    ) {
        // Verify OTP
        String storedOtp = otpStorage.get(email);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        otpStorage.remove(email); // Clear OTP

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