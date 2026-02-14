package com.securefilesharing.dto;

import java.time.LocalDateTime;

/**
 * DTO for user metadata viewing by auditors.
 * Contains ONLY non-sensitive user information.
 * NO password, JWT, or sensitive fields exposed.
 */
public class AuditUserMetadataDto {

    private Long userId;
    private String username;
    private String role;
    private String accountStatus;
    private LocalDateTime createdDate;
    private String email; // Masked or partial for privacy

    public AuditUserMetadataDto() {
    }

    public AuditUserMetadataDto(Long userId, String username, String role,
                                String accountStatus, LocalDateTime createdDate, String email) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.accountStatus = accountStatus;
        this.createdDate = createdDate;
        this.email = email;
    }

    // Getters and Setters

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

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
