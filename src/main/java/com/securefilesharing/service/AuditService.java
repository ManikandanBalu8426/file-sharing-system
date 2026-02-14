package com.securefilesharing.service;

import com.securefilesharing.entity.AuditLog;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralized Audit Service for structured logging.
 * All user actions must be logged through this service.
 */
@Service
public class AuditService {

    // Status constants
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    // Resource type constants
    public static final String RESOURCE_FILE = "FILE";
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_SYSTEM = "SYSTEM";
    public static final String RESOURCE_ACCESS_REQUEST = "ACCESS_REQUEST";

    // Action type constants (structured action names)
    public static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_SIGNUP = "SIGNUP";
    public static final String ACTION_UPLOAD = "UPLOAD";
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_SHARE = "SHARE";
    public static final String ACTION_PERMISSION_UPDATE = "PERMISSION_UPDATE";
    public static final String ACTION_ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ACTION_ACCESS_REQUEST = "ACCESS_REQUEST";
    public static final String ACTION_ACCESS_GRANT = "ACCESS_GRANT";
    public static final String ACTION_ACCESS_DENY = "ACCESS_DENY";
    public static final String ACTION_VIEW_AUDIT_LOGS = "VIEW_AUDIT_LOGS";
    public static final String ACTION_VIEW_FILE_METADATA = "VIEW_FILE_METADATA";
    public static final String ACTION_VIEW_USER_METADATA = "VIEW_USER_METADATA";
    public static final String ACTION_VISIBILITY_UPDATE = "VISIBILITY_UPDATE";
    public static final String ACTION_USER_DISABLED = "USER_DISABLED";
    public static final String ACTION_USER_ENABLED = "USER_ENABLED";
    public static final String ACTION_ADMIN_FILE_ACCESS = "ADMIN_FILE_ACCESS";

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ==================== PRIMARY LOGGING METHOD ====================

    /**
     * Centralized audit logging method.
     * Automatically extracts username from SecurityContext, IP address, and
     * User-Agent.
     *
     * @param actionType  The type of action (use constants: ACTION_UPLOAD,
     *                    ACTION_DOWNLOAD, etc.)
     * @param entityType  The type of entity (FILE, USER, SYSTEM)
     * @param entityId    The ID of the entity (file ID, user ID, etc.)
     * @param fileName    The file name (for file-related actions, null otherwise)
     * @param description Human-readable description of the action
     * @param status      SUCCESS or FAILURE
     */
    public void logAction(String actionType, String entityType, Long entityId,
            String fileName, String description, String status) {
        AuditLog log = new AuditLog();
        log.setAction(actionType);
        log.setResourceType(entityType);
        log.setResourceId(entityId);
        log.setFileName(fileName);
        log.setDetails(description);
        log.setStatus(status != null ? status : STATUS_SUCCESS);
        log.setTimestamp(LocalDateTime.now());

        // Extract authenticated user from SecurityContext
        extractAndSetUserInfo(log);

        // Extract IP address and User-Agent from HttpServletRequest
        extractAndSetRequestInfo(log);

        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Audit logging must not break main application flow
            System.err.println("Audit log save failed: " + e.getMessage());
        }
    }

    /**
     * Convenience method for successful actions.
     */
    public void logSuccess(String actionType, String entityType, Long entityId,
            String fileName, String description) {
        logAction(actionType, entityType, entityId, fileName, description, STATUS_SUCCESS);
    }

    /**
     * Convenience method for failed actions.
     */
    public void logFailure(String actionType, String entityType, Long entityId,
            String fileName, String description) {
        logAction(actionType, entityType, entityId, fileName, description, STATUS_FAILURE);
    }

    // ==================== LEGACY METHODS (Backward Compatibility)
    // ====================

    /**
     * Legacy method - maintained for backward compatibility.
     * 
     * @deprecated Use logAction(actionType, entityType, entityId, fileName,
     *             description, status) instead
     */
    @Deprecated
    public void logAction(User user, String action, String details) {
        logAction(user, action, details, null, null, null);
    }

    /**
     * Legacy method with file context - maintained for backward compatibility.
     * 
     * @deprecated Use logAction(actionType, entityType, entityId, fileName,
     *             description, status) instead
     */
    @Deprecated
    public void logAction(User user, String action, String details, Long fileId, Long fileOwnerId, Long targetUserId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setDetails(details);
        log.setFileId(fileId);
        log.setFileOwnerId(fileOwnerId);
        log.setTargetUserId(targetUserId);
        log.setTimestamp(LocalDateTime.now());
        log.setStatus(STATUS_SUCCESS);

        if (user != null) {
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
            log.setRole(user.getRole());
        }

        // Determine resource type from context
        if (fileId != null) {
            log.setResourceType(RESOURCE_FILE);
            log.setResourceId(fileId);
        } else if (targetUserId != null) {
            log.setResourceType(RESOURCE_USER);
            log.setResourceId(targetUserId);
        } else {
            log.setResourceType(RESOURCE_SYSTEM);
        }

        extractAndSetRequestInfo(log);

        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Audit log save failed: " + e.getMessage());
        }
    }

    /**
     * Log event with explicit HttpServletRequest and Authentication context.
     * Used by controllers that need to log auditor access.
     */
    public void logEvent(HttpServletRequest request, Authentication authentication,
            String action, String resourceType, String resourceId,
            String status, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId != null && !resourceId.isEmpty() ? Long.parseLong(resourceId) : null);
        log.setStatus(status != null ? status : STATUS_SUCCESS);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());

        // Extract user info from Authentication
        if (authentication != null && authentication.getPrincipal() != null
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                log.setUsername(userDetails.getUsername());
                String role = userDetails.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(a -> a != null && a.startsWith("ROLE_"))
                        .findFirst()
                        .orElse("ROLE_USER");
                log.setRole(role);
            } else {
                log.setUsername(principal.toString());
            }
        }

        // Extract IP and User-Agent from request
        if (request != null) {
            log.setIpAddress(getClientIp(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }

        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Audit log save failed: " + e.getMessage());
        }
    }

    /**
     * Log authentication event (login/logout/signup) with all context.
     * Used by OtpAuthService for login events.
     */
    public void logAuthEvent(Long userId, String username, String role, String action,
            String resourceType, String resourceId, String status,
            String ipAddress, String userAgent, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setRole(role);
        log.setAction(action);
        log.setResourceType(resourceType != null ? resourceType : RESOURCE_USER);
        log.setResourceId(resourceId != null && !resourceId.isEmpty() ? Long.parseLong(resourceId) : null);
        log.setStatus(status != null ? status : STATUS_SUCCESS);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());

        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Audit log save failed: " + e.getMessage());
        }
    }

    /**
     * Async logging (fire and forget).
     */
    @Async
    public void logActionAsync(String actionType, String entityType, Long entityId,
            String fileName, String description, String status) {
        logAction(actionType, entityType, entityId, fileName, description, status);
    }

    // ==================== QUERY METHODS ====================

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    public Page<AuditLog> getLogsPaginated(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    public Page<AuditLog> searchLogsPaginated(
            Long userId,
            String username,
            String action,
            String status,
            String resourceType,
            String fileName,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return auditLogRepository.searchLogsAdvanced(userId, username, action, status,
                resourceType, fileName, startDate, endDate, pageable);
    }

    public AuditLog getLogById(Long id) {
        return auditLogRepository.findById(id).orElse(null);
    }

    /**
     * Legacy search method for backward compatibility.
     */
    public List<AuditLog> searchLogs(Long actorUserId, Long fileId, Long fileOwnerId, String action,
            LocalDateTime from, LocalDateTime to) {
        return auditLogRepository.search(actorUserId, fileId, fileOwnerId, action, from, to);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Extract user info from SecurityContext and set on AuditLog.
     * Uses proper authentication check to avoid "Unknown" unless truly
     * anonymous/system.
     */
    private void extractAndSetUserInfo(AuditLog log) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {

                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails userDetails) {
                    log.setUsername(userDetails.getUsername());
                    // Extract role
                    String role = userDetails.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .filter(a -> a != null && a.startsWith("ROLE_"))
                            .findFirst()
                            .orElse(null);
                    log.setRole(role);
                } else if (principal != null && !"anonymousUser".equals(principal.toString())) {
                    log.setUsername(principal.toString());
                }
            }
            // If still no username, leave as null (will show as SYSTEM for true system
            // events)
        } catch (Exception e) {
            // Don't fail if security context is unavailable
        }
    }

    /**
     * Extract IP address and User-Agent from current HTTP request.
     */
    private void extractAndSetRequestInfo(AuditLog log) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                log.setIpAddress(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // Don't fail if request context is unavailable
        }
    }

    /**
     * Extract client IP address with proxy support.
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
