package com.socialsea.controller;

import com.socialsea.dto.AuthResponse;
import com.socialsea.dto.OtpSendResult;
import com.socialsea.model.LoginRequest;
import com.socialsea.model.Role;
import com.socialsea.model.User;
import com.socialsea.repository.UserRepository;
import com.socialsea.service.AuthService;
import com.socialsea.service.DeviceSessionLimitException;
import com.socialsea.service.LoginSessionService;
import com.socialsea.service.OtpService;
import com.socialsea.security.AuthCookieUtil;
import com.socialsea.security.JwtUtil;
import com.socialsea.util.UserIdentityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginSessionService loginSessionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.otp.expose-debug-otp:false}")
    private boolean exposeDebugOtp;

    @Value("${app.otp.return-fallback-otp-on-delivery-failure:true}")
    private boolean returnFallbackOtpOnDeliveryFailure;

    @Value("${app.security.require-https:false}")
    private boolean requireHttps;

    @Value("${jwt.expiration:3600000}")
    private long accessTokenMaxAgeMs;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshTokenMaxAgeMs;

    @PostMapping({"/send-otp", "/forgot-password", "/forgotPassword"})
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {
        String identifier = resolveIdentifier(body);
        if (identifier == null || identifier.isBlank()) {
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
            result = isPhoneIdentifier
                    ? otpService.sendOtpToPhone(otpKey)
                    : otpService.sendOtp(otpKey);
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

        Map<String, Object> response = new HashMap<>();
        response.put("channel", isPhoneIdentifier ? "sms" : "email");
        response.put("message", deliveryFailed
                ? (isPhoneIdentifier ? "OTP generated, but SMS delivery is not configured yet" : "OTP generated, but email delivery failed")
                : "OTP sent");
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
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String identifier = resolveIdentifier(body);
        String otp = normalize(body.get("otp"));
        if (otp == null) {
            otp = normalize(body.get("code"));
        }

        if (identifier == null || otp == null) {
            return ResponseEntity
                .badRequest()
                .body(Map.of("message", "Email/mobile or OTP missing"));
        }

        String normalizedPhone = normalizePhone(identifier);
        String otpKey = (normalizedPhone != null && !identifier.contains("@"))
                ? normalizedPhone
                : identifier.toLowerCase(Locale.ROOT);

        try {
            AuthResponse response = authService.verifyOtp(otpKey, otp, httpRequest);
            return respondWithAuthCookies(response, httpRequest);
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        String username = normalize(body.get("username"));
        String email = normalize(body.get("email"));
        String phoneInput = normalize(body.get("phoneNumber"));
        if (phoneInput == null) phoneInput = normalize(body.get("mobileNumber"));
        if (phoneInput == null) phoneInput = normalize(body.get("phone"));
        String phoneNumber = normalizePhone(phoneInput);
        if (phoneInput != null && phoneNumber == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid mobile number"));
        }
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
        email = UserIdentityUtils.normalizeEmail(email);

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
        if (user == null && phoneNumber != null) {
            Optional<User> byPhone = userRepository.findByPhoneNumber(phoneNumber);
            if (byPhone.isPresent()) {
                user = byPhone.get();
            }
        }

        if (user != null && user.isBanned()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "User banned"));
        }

        if (user != null && user.getPassword() != null && !user.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "That email already has an account. Please log in instead."
            ));
        }

        if (user == null) {
            user = new User();
            user.setRole(Role.USER);
            user.setCreatedAt(LocalDateTime.now());
        }

        if (email == null) {
            email = generateLocalEmail(username != null ? username : "user");
        }
        email = UserIdentityUtils.normalizeEmail(email);

        String existingEmail = UserIdentityUtils.normalizeEmail(user.getEmail());
        if (existingEmail == null) {
            user.setEmail(email);
        } else if (!existingEmail.equals(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Email already linked to another account"));
        }

        if (phoneNumber != null) {
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                user.setPhoneNumber(phoneNumber);
            } else if (!user.getPhoneNumber().equals(phoneNumber)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Mobile number already linked to another account"));
            }
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
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "An account already exists for that email. Please log in instead."
            ));
        }

        try {
            var session = loginSessionService.startSession(user, httpRequest, body.get("deviceId"), body.get("deviceName"));
            String tokenSubject = resolveTokenSubject(user, email != null ? email : username);
            String accessToken = jwtUtil.generateAccessToken(tokenSubject, session.getSessionId());
            String refreshToken = jwtUtil.generateRefreshToken(tokenSubject, session.getSessionId());
            return respondWithAuthCookies(
                    new AuthResponse(accessToken, refreshToken, user, session.getDeviceId()),
                    httpRequest
            );
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
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

        if (user.isBanned()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "User banned"));
        }

        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            if (!legacyPasswordMatchAndUpgrade(user, password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
            }
        }

        try {
            var session = loginSessionService.startSession(user, httpRequest, body.get("deviceId"), body.get("deviceName"));
            String tokenSubject = resolveTokenSubject(user, identifier);
            String accessToken = jwtUtil.generateAccessToken(tokenSubject, session.getSessionId());
            String refreshToken = jwtUtil.generateRefreshToken(tokenSubject, session.getSessionId());
            return respondWithAuthCookies(
                    new AuthResponse(accessToken, refreshToken, user, session.getDeviceId()),
                    httpRequest
            );
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
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

    private String resolveIdentifier(Map<String, String> body) {
        String identifier = normalize(body.get("identifier"));
        if (identifier == null) identifier = normalize(body.get("email"));
        if (identifier == null) identifier = normalize(body.get("username"));
        if (identifier == null) identifier = normalize(body.get("phoneNumber"));
        if (identifier == null) identifier = normalize(body.get("mobileNumber"));
        if (identifier == null) identifier = normalize(body.get("phone"));
        return identifier;
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

    private String normalizePhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        String compact = normalized.replaceAll("[\\s\\-()]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (compact.startsWith("+")) {
            String rest = compact.substring(1).replaceAll("[^0-9]", "");
            if (rest.length() < 6 || rest.length() > 15) return null;
            return "+" + rest;
        }
        String digits = compact.replaceAll("[^0-9]", "");
        if (digits.length() < 6 || digits.length() > 15) return null;
        return digits;
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

    private ResponseCookie buildRefreshCookie(String refreshToken, HttpServletRequest request) {
        return AuthCookieUtil.buildRefreshTokenCookie(
                refreshToken,
                request,
                requireHttps,
                Duration.ofMillis(refreshTokenMaxAgeMs)
        );
    }

    private ResponseCookie buildAccessCookie(String accessToken, HttpServletRequest request) {
        return AuthCookieUtil.buildAccessTokenCookie(
                accessToken,
                request,
                requireHttps,
                Duration.ofMillis(accessTokenMaxAgeMs)
        );
    }

    private ResponseEntity<AuthResponse> respondWithAuthCookies(AuthResponse response, HttpServletRequest request) {
        if (response == null) {
            return ResponseEntity.ok(new AuthResponse(null, null, (User) null, null));
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        String accessToken = normalize(response.getToken());
        String refreshToken = normalize(response.getRefreshToken());
        if (accessToken != null) {
            builder.header(HttpHeaders.SET_COOKIE, buildAccessCookie(accessToken, request).toString());
        }
        if (refreshToken != null) {
            builder.header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken, request).toString());
        }
        AuthResponse sanitized = new AuthResponse(
                null,
                null,
                response.getUser(),
                response.getDeviceId()
        );
        sanitized.setRole(response.getRole());
        return builder.body(sanitized);
    }

    private String resolveRefreshToken(Map<String, String> body, HttpServletRequest request) {
        String refreshToken = null;
        if (body != null) {
            refreshToken = normalize(body.get("refreshToken"));
            if (refreshToken == null) refreshToken = normalize(body.get("refresh_token"));
            if (refreshToken == null) refreshToken = normalize(body.get("token"));
            if (refreshToken == null) refreshToken = normalize(body.get("jwt"));
        }
        if (refreshToken != null
                && !"null".equalsIgnoreCase(refreshToken)
                && !"undefined".equalsIgnoreCase(refreshToken)) {
            return refreshToken;
        }
        return AuthCookieUtil.resolveRefreshToken(request);
    }

    private ResponseEntity<String> unauthorizedWithClearedAuthCookies(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, AuthCookieUtil.clearAccessTokenCookie(request, requireHttps).toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookieUtil.clearRefreshTokenCookie(request, requireHttps).toString())
                .body(message);
    }

    private ResponseEntity<String> forbiddenWithClearedAuthCookies(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.SET_COOKIE, AuthCookieUtil.clearAccessTokenCookie(request, requireHttps).toString())
                .header(HttpHeaders.SET_COOKIE, AuthCookieUtil.clearRefreshTokenCookie(request, requireHttps).toString())
                .body(message);
    }

    private String resolveTokenSubject(User user, String fallback) {
        String email = UserIdentityUtils.normalizeEmail(user != null ? user.getEmail() : null);
        if (email != null) {
            return email;
        }
        String name = normalize(user != null ? user.getName() : null);
        if (name != null) {
            return name;
        }
        String fallbackValue = normalize(fallback);
        if (fallbackValue != null) {
            return fallbackValue;
        }
        throw new IllegalArgumentException("User has no valid identifier for JWT subject");
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }
        String email = normalize(request.getEmail());
        String password = normalize(request.getPassword());
        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }
        User admin = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        if (admin.isBanned()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "User banned"));
        }

        if (admin.getPassword() == null || !passwordEncoder.matches(password, admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        if (admin.getRole() != Role.ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not an admin"));
        }

        try {
            var session = loginSessionService.startSession(admin, httpRequest, null, null);
            String tokenSubject = resolveTokenSubject(admin, email);
            String accessToken = jwtUtil.generateAccessToken(tokenSubject, session.getSessionId());
            String refreshToken = jwtUtil.generateRefreshToken(tokenSubject, session.getSessionId());
            return respondWithAuthCookies(
                    new AuthResponse(accessToken, refreshToken, admin, session.getDeviceId()),
                    httpRequest
            );
        } catch (DeviceSessionLimitException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", ex.getMessage(), "code", "DEVICE_LIMIT"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String refreshToken = resolveRefreshToken(body, request);
        if (refreshToken == null || refreshToken.isBlank()) {
            return unauthorizedWithClearedAuthCookies("Refresh token missing", request);
        }

        try {
            if (jwtUtil.isExpired(refreshToken)) {
                return unauthorizedWithClearedAuthCookies("Refresh token expired", request);
            }
        } catch (Exception e) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }

        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception e) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }

        String sessionId;
        try {
            sessionId = jwtUtil.extractTokenId(refreshToken);
        } catch (Exception e) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }

        if (sessionId == null || sessionId.isBlank()) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }

        User user = userRepository.findByEmailIgnoreCase(username).orElse(null);
        if (user == null || user.getId() == null) {
            return unauthorizedWithClearedAuthCookies("Refresh token invalid", request);
        }
        if (user.isBanned()) {
            return forbiddenWithClearedAuthCookies("User banned", request);
        }

        if (!loginSessionService.isActiveSession(user.getId(), sessionId)) {
            return unauthorizedWithClearedAuthCookies("Session expired. Please login again.", request);
        }
        loginSessionService.touch(sessionId);

        String newAccessToken = jwtUtil.generateAccessToken(username, sessionId);
        ResponseCookie refreshCookie = buildRefreshCookie(refreshToken, request);
        ResponseCookie accessCookie = buildAccessCookie(newAccessToken, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new AuthResponse(null, null, user, null));
    }
}

