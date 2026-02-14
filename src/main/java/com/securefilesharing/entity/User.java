package com.securefilesharing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    // Nullable for smooth schema evolution: existing DB rows may have NULL after
    // ddl-auto=update.
    // Treat NULL as active=true in isActive().
    @Column(nullable = true)
    private Boolean active = false;

    @Column(nullable = false)
    private String status = "PENDING";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role == null || role.isBlank() ? "ROLE_USER" : role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active == null ? true : active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getStatus() {
        return status == null ? "APPROVED" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
