package com.securefilesharing.controller;

import com.securefilesharing.dto.JwtResponse;
import com.securefilesharing.dto.LoginRequest;
import com.securefilesharing.dto.MessageResponse;
import com.securefilesharing.dto.OtpRequestResponse;
import com.securefilesharing.dto.SigninOtpRequest;
import com.securefilesharing.dto.SigninOtpVerifyRequest;
import com.securefilesharing.dto.SignupRequest;
import com.securefilesharing.dto.SignupOtpRequest;
import com.securefilesharing.dto.SignupOtpVerifyRequest;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.security.jwt.JwtUtils;
import com.securefilesharing.service.OtpAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    OtpAuthService otpAuthService;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.badRequest().body(new MessageResponse(
                "OTP required. Use /api/auth/signin/request-otp then /api/auth/signin/verify"));
    }

    @PostMapping("/signin/request-otp")
    public ResponseEntity<?> requestSigninOtp(@RequestBody SigninOtpRequest request) {
        try {
            OtpRequestResponse resp = otpAuthService.requestSigninOtp(request);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/signin/verify")
    public ResponseEntity<?> verifySigninOtp(@RequestBody SigninOtpVerifyRequest request) {
        try {
            JwtResponse jwtResponse = otpAuthService.verifySigninOtp(request.getOtpRequestId(), request.getOtp());
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        return ResponseEntity.badRequest().body(new MessageResponse(
                "OTP required. Use /api/auth/signup/request-otp then /api/auth/signup/verify"));
    }

    @PostMapping("/signup/request-otp")
    public ResponseEntity<?> requestSignupOtp(@RequestBody SignupOtpRequest request) {
        try {
            OtpRequestResponse resp = otpAuthService.requestSignupOtp(request);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/signup/verify")
    public ResponseEntity<?> verifySignupOtp(@RequestBody SignupOtpVerifyRequest request) {
        try {
            otpAuthService.verifySignupOtp(request.getOtpRequestId(), request.getOtp());
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
