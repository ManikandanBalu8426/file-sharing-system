package com.securefilesharing.config;

import com.securefilesharing.repository.AuditLogRepository;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ensures the system meets the requirement: "By default every account should be active".
 *
 * Why this is needed:
 * - When the 'active' column is added later (ddl-auto=update), existing rows can end up active=false.
 * - Spring Security then blocks login with "User is disabled".
 *
 * Safety rule:
 * - Only auto-enable users who are currently inactive AND have no explicit USER_DEACTIVATED audit event.
 *   This prevents undoing a deliberate admin deactivation.
 */
@Component
public class BackfillUserActiveRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillUserActiveRunner.class);

    @Value("${app.bootstrap.backfillActive.enabled:true}")
    private boolean enabled;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        List<User> inactiveUsers = userRepository.findByActiveFalse();
        int enabledCount = 0;
        for (User u : inactiveUsers) {
            Long userId = u.getId();
            if (userId == null) {
                continue;
            }
            boolean explicitlyDeactivated = auditLogRepository.existsByActionAndTargetUserId("USER_DEACTIVATED", userId);
            if (explicitlyDeactivated) {
                continue;
            }
            u.setActive(true);
            userRepository.save(u);
            enabledCount++;
        }

        if (enabledCount > 0) {
            log.info("Backfilled user active flag: enabled {} inactive user(s)", enabledCount);
        }
    }
}
