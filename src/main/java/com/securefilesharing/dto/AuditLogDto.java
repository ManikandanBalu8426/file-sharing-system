package com.securefilesharing.dto;

import java.time.LocalDateTime;

/**
 * DTO for audit log entries - used by auditor module.
 * Contains all log fields except sensitive internals.
 */
public class AuditLogDto {

    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String fileName;
    private String status;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
    private String details;

    public AuditLogDto() {
    }

    public AuditLogDto(Long id, Long userId, String username, String role, String action,
                       String resourceType, Long resourceId, String fileName, String status,
                       String ipAddress, String userAgent, LocalDateTime timestamp, String details) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.fileName = fileName;
        this.status = status;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestamp = timestamp;
        this.details = details;
    }

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
}
