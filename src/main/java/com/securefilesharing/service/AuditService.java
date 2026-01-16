package com.securefilesharing.service;

import com.securefilesharing.entity.AuditLog;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void logAction(User user, String action, String details) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }
}
