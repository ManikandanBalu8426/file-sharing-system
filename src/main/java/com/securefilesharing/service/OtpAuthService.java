package com.securefilesharing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securefilesharing.dto.JwtResponse;
import com.securefilesharing.dto.OtpRequestResponse;
import com.securefilesharing.dto.SigninOtpRequest;
import com.securefilesharing.dto.SignupOtpRequest;
import com.securefilesharing.entity.OtpPurpose;
import com.securefilesharing.entity.OtpToken;
import com.securefilesharing.entity.Role;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.OtpTokenRepository;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.security.jwt.JwtUtils;
import com.securefilesharing.security.services.UserDetailsImpl;
import com.securefilesharing.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

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

        // Security: role assignment is admin-controlled. New signups are always ROLE_USER.
        Role role = Role.ROLE_USER;
        String passwordHash = passwordEncoder.encode(request.getPassword());

        SignupPayload payload = new SignupPayload();
        payload.username = request.getUsername();
        payload.email = request.getEmail();
        payload.passwordHash = passwordHash;
        payload.role = role.name();

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
        user.setRole(Role.valueOf(payload.role));
        user.setActive(true);

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
        User user = userRepository.findById(userDetails.getId()).orElseThrow(() -> new IllegalArgumentException("User not found"));

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
        String role = userDetails.getAuthorities().stream().findFirst().get().getAuthority();

        return new JwtResponse(jwt, userDetails.getId(), userDetails.getUsername(), role);
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

    private Role parseRole(String inputRole) {
        Role role = Role.ROLE_USER;
        if (inputRole != null) {
            try {
                String reqRole = inputRole.toUpperCase();
                if (!reqRole.startsWith("ROLE_")) {
                    reqRole = "ROLE_" + reqRole;
                }
                role = Role.valueOf(reqRole);
            } catch (IllegalArgumentException e) {
                // default
            }
        }
        return role;
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
}
