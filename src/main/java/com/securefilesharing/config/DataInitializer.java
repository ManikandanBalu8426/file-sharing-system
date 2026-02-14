package com.securefilesharing.config;

import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Override
    public void run(String... args) throws Exception {
        // Create Admin user if not exists
        if (!userRepository.existsByEmail(adminEmail) && !userRepository.existsByUsername(adminUsername)) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN"); // "In DB -> ADMIN"
            admin.setActive(true);
            admin.setStatus("APPROVED");

            userRepository.save(admin);
            System.out.println("Admin account seeded: " + adminUsername + " / " + adminEmail);
        } else {
            System.out.println("Admin account already exists or username '" + adminUsername + "' is taken.");
        }
    }
}
