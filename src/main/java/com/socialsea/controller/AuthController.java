package com.socialsea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("https://socialsea.netlify.app")
public class AuthController {

    // In-memory storage for demonstration. 
    // In a real application, replace these with a Database (UserRepository) and Redis (for OTPs).
    private static final Map<String, String> users = new HashMap<>();
    private static final Map<String, String> otpStorage = new HashMap<>();

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Phone number or Email is required");
        }

        // Check if user already exists (Enforces one account per email/phone)
        if (users.containsKey(username)) {
            return ResponseEntity.badRequest().body("Account already exists with this identifier");
        }

        // Generate a 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Store OTP temporarily
        otpStorage.put(username, otp);

        // Simulate sending OTP (Check your server console to see the code)
        System.out.println(">>> OTP for " + username + " is: " + otp);

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
        users.put(username, password);
        otpStorage.remove(username); // Clear OTP after successful registration

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (users.containsKey(username) && users.get(username).equals(password)) {
            // Return a dummy token for now
            return ResponseEntity.ok(Map.of("token", "dummy-jwt-token-" + System.currentTimeMillis()));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }
}