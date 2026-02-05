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
        logAction(user, action, details, null, null, null);
    }

    public void logAction(User user, String action, String details, Long fileId, Long fileOwnerId, Long targetUserId) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setDetails(details);
        log.setFileId(fileId);
        log.setFileOwnerId(fileOwnerId);
        log.setTargetUserId(targetUserId);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    public List<AuditLog> searchLogs(Long actorUserId, Long fileId, Long fileOwnerId, String action,
                                     LocalDateTime from, LocalDateTime to) {
        return auditLogRepository.search(actorUserId, fileId, fileOwnerId, action, from, to);
    }
}
