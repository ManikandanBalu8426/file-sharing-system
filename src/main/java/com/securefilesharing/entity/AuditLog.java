package com.securefilesharing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_status", columnList = "status"),
    @Index(name = "idx_audit_resource_type", columnList = "resourceType"),
    @Index(name = "idx_audit_username", columnList = "username"),
    @Index(name = "idx_audit_filename", columnList = "fileName")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Store userId directly (no FK constraint for write-once logs)
    @Column(name = "user_id", nullable = true)
    private Long userId;

    @Column(nullable = true)
    private String username;

    @Column(nullable = true)
    private String role;

    // ActionType: LOGIN_SUCCESS, LOGIN_FAILED, UPLOAD, DOWNLOAD, DELETE, SHARE, PERMISSION_UPDATE, ROLE_UPDATE, etc.
    @Column(nullable = false)
    private String action;

    @Column(nullable = true)
    private String resourceType; // FILE, USER, SYSTEM

    @Column(nullable = true)
    private Long resourceId;

    // File name for file-related actions (for display without joins)
    @Column(nullable = true, length = 512)
    private String fileName;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILURE

    @Column(nullable = true)
    private String ipAddress;

    @Column(nullable = true)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String details;

    // Legacy fields for backward compatibility
    @Column(nullable = true)
    private Long fileId;

    @Column(nullable = true)
    private Long fileOwnerId;

    @Column(nullable = true)
    private Long targetUserId;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getFileOwnerId() {
        return fileOwnerId;
    }

    public void setFileOwnerId(Long fileOwnerId) {
        this.fileOwnerId = fileOwnerId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
