package com.socialsea.controller;

import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("https://socialsea.netlify.app")
public class AuthController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private JwtUtil jwtUtil;

    // Temporary OTP storage (Use Redis in production for better scalability)
    private static final Map<String, String> otpStorage = new HashMap<>();

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number or Email is required");
        }

        // Check if user already exists (Enforces one account per email/phone)
        if (userRepo.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("Account already exists with this identifier");
        }

        // Generate a 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Store OTP temporarily
        otpStorage.put(username, otp);

        // Send OTP via Email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(username);
        message.setSubject("Your SocialSea OTP");
        message.setText("Your OTP code is: " + otp);
        mailSender.send(message);

        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String otp = request.get("otp");

        if (username == null || password == null || otp == null) {
            return ResponseEntity.badRequest().body("All fields are required");
        }

        // Verify OTP
        String storedOtp = otpStorage.get(username);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        // Register User
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password); // Note: Add BCrypt encoding here in production
        userRepo.save(newUser);
        
        otpStorage.remove(username); // Clear OTP after successful registration

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        User user = userRepo.findByUsername(username).orElse(null);

        if (user != null && user.getPassword().equals(password)) {
            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(Map.of("token", token));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }
}
}