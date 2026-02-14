package com.securefilesharing.config;

import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optional bootstrap for local/dev setups.
 *
 * Purpose: If all users become disabled (e.g., after adding the 'active' column),
 * you can still get an ADMIN account to log in and reactivate users.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    @Value("${app.bootstrap.admin.enabled:false}")
    private boolean enabled;

    @Value("${app.bootstrap.admin.username:}")
    private String username;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (isBlank(username) || isBlank(password)) {
            throw new IllegalStateException("Bootstrap admin is enabled but username/password not configured");
        }

        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!"ROLE_ADMIN".equals(existing.getRole())) {
                existing.setRole("ROLE_ADMIN");
                changed = true;
            }
            if (!existing.isActive()) {
                existing.setActive(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
        }, () -> {
            User admin = new User();
            admin.setUsername(username);
            admin.setEmail(isBlank(email) ? null : email);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole("ROLE_ADMIN");
            admin.setActive(true);
            userRepository.save(admin);
        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
