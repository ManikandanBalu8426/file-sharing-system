package com.securefilesharing.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${spring.mail.username:}")
    private String fallbackFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        String fromAddress = (from != null && !from.isBlank()) ? from : fallbackFrom;
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalStateException("Email sender address is not configured. Set app.mail.from or spring.mail.username");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP for " + purpose + " is: " + otp + "\n\nThis code will expire soon. Do not share it with anyone.");

        mailSender.send(message);
    }
}
