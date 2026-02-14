package com.securefilesharing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securefilesharing.dto.JwtResponse;
import com.securefilesharing.dto.OtpRequestResponse;
import com.securefilesharing.dto.SigninOtpRequest;
import com.securefilesharing.dto.SignupOtpRequest;
import com.securefilesharing.entity.OtpPurpose;
import com.securefilesharing.entity.OtpToken;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.OtpTokenRepository;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.security.jwt.JwtUtils;
import com.securefilesharing.security.services.UserDetailsImpl;
import com.securefilesharing.security.services.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class OtpAuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Autowired
    private AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.ttlSeconds:600}")
    private long otpTtlSeconds;

    @Value("${app.otp.maxAttempts:5}")
    private int otpMaxAttempts;

    public OtpAuthService(AuthenticationManager authenticationManager,
            UserDetailsServiceImpl userDetailsService,
            UserRepository userRepository,
            OtpTokenRepository otpTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            EmailService emailService,
            ObjectMapper objectMapper) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.otpTokenRepository = otpTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    public OtpRequestResponse requestSignupOtp(SignupOtpRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        // Security: role assignment is admin-controlled. New signups are always USER.
        String role = "USER";
        String passwordHash = passwordEncoder.encode(request.getPassword());

        SignupPayload payload = new SignupPayload();
        payload.username = request.getUsername();
        payload.email = request.getEmail();
        payload.passwordHash = passwordHash;
        payload.role = role;

        return createAndSendOtp(request.getEmail(), OtpPurpose.SIGNUP, payload, "Sign up");
    }

    public void verifySignupOtp(Long otpRequestId, String otp) {
        OtpToken token = validateOtpOrThrow(otpRequestId, OtpPurpose.SIGNUP, otp);
        SignupPayload payload = readPayload(token.getPayloadJson(), SignupPayload.class);

        if (userRepository.existsByUsername(payload.username)) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(payload.email)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = new User();
        user.setUsername(payload.username);
        user.setEmail(payload.email);
        user.setPassword(payload.passwordHash);
        user.setRole(payload.role);
        user.setActive(false);
        user.setStatus("PENDING");

        userRepository.save(user);
    }

    public OtpRequestResponse requestSigninOtp(SigninOtpRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        // Validate username/password first.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (userDetails.getId() == null) {
            throw new IllegalArgumentException("User id missing");
        }
        long userId = userDetails.getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("No email is set for this account. Cannot send OTP.");
        }

        SigninPayload payload = new SigninPayload();
        payload.username = userDetails.getUsername();

        return createAndSendOtp(user.getEmail(), OtpPurpose.SIGNIN, payload, "Sign in");
    }

    public JwtResponse verifySigninOtp(Long otpRequestId, String otp) {
        OtpToken token = validateOtpOrThrow(otpRequestId, OtpPurpose.SIGNIN, otp);
        SigninPayload payload = readPayload(token.getPayloadJson(), SigninPayload.class);

        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(payload.username);
        var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtUtils.generateJwtToken(authentication);

        String role = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_USER");
        List<String> permissions = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null && a.startsWith("PERM_"))
                .sorted()
                .toList();

        // Log successful login
        logAuthEvent(userDetails.getId(), payload.username, role, AuditService.ACTION_LOGIN_SUCCESS,
                AuditService.STATUS_SUCCESS, null);

        return new JwtResponse(jwt, userDetails.getId(), userDetails.getUsername(), role, permissions);
    }

    private OtpRequestResponse createAndSendOtp(String email, OtpPurpose purpose, Object payload, String purposeLabel) {
        String otp = generateOtp();

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setPurpose(purpose);
        token.setOtpHash(passwordEncoder.encode(otp));
        token.setAttempts(0);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(otpTtlSeconds));
        token.setConsumedAt(null);
        token.setPayloadJson(writePayload(payload));

        token = otpTokenRepository.save(token);

        emailService.sendOtpEmail(email, otp, purposeLabel);

        return new OtpRequestResponse("OTP sent to email", token.getId());
    }

    private OtpToken validateOtpOrThrow(Long otpRequestId, OtpPurpose purpose, String otp) {
        if (otpRequestId == null) {
            throw new IllegalArgumentException("otpRequestId is required");
        }
        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("otp is required");
        }

        OtpToken token = otpTokenRepository.findByIdAndPurpose(otpRequestId, purpose)
                .orElseThrow(() -> new IllegalArgumentException("OTP request not found"));

        if (token.getConsumedAt() != null) {
            throw new IllegalArgumentException("OTP already used");
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw new IllegalArgumentException("OTP expired");
        }
        if (token.getAttempts() >= otpMaxAttempts) {
            throw new IllegalArgumentException("Too many attempts");
        }

        token.setAttempts(token.getAttempts() + 1);
        boolean matches = passwordEncoder.matches(otp, token.getOtpHash());

        if (!matches) {
            otpTokenRepository.save(token);
            throw new IllegalArgumentException("Invalid OTP");
        }

        token.setConsumedAt(Instant.now());
        otpTokenRepository.save(token);

        return token;
    }

    private String generateOtp() {
        int code = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OTP payload", e);
        }
    }

    private <T> T readPayload(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OTP payload", e);
        }
    }

    private static class SignupPayload {
        public String username;
        public String email;
        public String passwordHash;
        public String role;
    }

    private static class SigninPayload {
        public String username;
    }

    /**
     * Log authentication events for audit trail.
     */
    private void logAuthEvent(Long userId, String username, String role, String action,
            String status, String details) {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            String ipAddress = extractIpAddress(request);
            String userAgent = extractUserAgent(request);

            auditService.logAuthEvent(userId, username, role, action,
                    AuditService.RESOURCE_USER, userId != null ? userId.toString() : null,
                    status, ipAddress, userAgent, details);
        } catch (Exception e) {
            // Don't fail authentication if audit logging fails
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractIpAddress(HttpServletRequest request) {
        if (request == null)
            return "unknown";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest request) {
        if (request == null)
            return "unknown";
        return request.getHeader("User-Agent");
    }
}
