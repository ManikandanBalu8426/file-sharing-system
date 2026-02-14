package com.securefilesharing.controller;

import com.securefilesharing.entity.AuditLog;
import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.User;
import com.securefilesharing.service.AdminService;
import com.securefilesharing.service.AuditService;
import com.securefilesharing.service.FileService;
import com.securefilesharing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin")
// 1. Role Enforcement for entire controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    // 3. Dashboard Page Stats
    @GetMapping("/dashboard-summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    // 4. Pending Users Management
    @GetMapping("/pending-users")
    public ResponseEntity<List<User>> getPendingUsers() {
        return ResponseEntity.ok(adminService.getPendingUsers());
    }

    @PutMapping("/approve/{userId}")
    public ResponseEntity<?> approveUser(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().body("Role is required");
        }
        adminService.approveUser(userId, role);
        return ResponseEntity.ok(Map.of("message", "User approved successfully"));
    }

    @PutMapping("/reject/{userId}")
    public ResponseEntity<?> rejectUser(@PathVariable Long userId) {
        adminService.rejectUser(userId);
        return ResponseEntity.ok(Map.of("message", "User rejected successfully"));
    }

    // 5. All Users Management
    @GetMapping("/all-users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/change-role/{userId}")
    public ResponseEntity<?> changeUserRole(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().body("Role is required");
        }
        adminService.changeUserRole(userId, role);
        return ResponseEntity.ok(Map.of("message", "Role updated successfully"));
    }

    @PutMapping("/toggle-status/{userId}")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId) {
        adminService.toggleUserStatus(userId);
        return ResponseEntity.ok(Map.of("message", "User status toggled successfully"));
    }

    // 6. File Management (Global View)
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, Object>>> getAllFiles() {
        return ResponseEntity.ok(adminService.getAllFiles());
    }

    @GetMapping("/file/download/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable Long fileId, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Long adminId = null;
            if (userDetails instanceof com.securefilesharing.security.services.UserDetailsImpl) {
                adminId = ((com.securefilesharing.security.services.UserDetailsImpl) userDetails).getId();
            } else {
                // Try to resolve user by username
                adminId = userRepository.findByUsername(userDetails.getUsername()).map(User::getId).orElse(null);
            }

            // Log access via AdminService helper which calls AuditService
            adminService.recordAdminFileAccess(fileId, adminId);

            // Use FileService to actually get bytes (decrypted)
            // We need a User entity to pass to FileService.downloadFile.
            // Since we are ADMIN, logic allows it.
            User adminUser = new User();
            adminUser.setId(adminId);
            adminUser.setUsername(userDetails.getUsername());
            adminUser.setRole("ROLE_ADMIN");

            byte[] data = fileService.downloadFile(fileId, adminUser);
            FileEntity file = fileService.getFile(fileId);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            file.getContentType() != null ? file.getContentType() : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error downloading file: " + e.getMessage());
        }
    }

    @PutMapping("/file/soft-delete/{fileId}")
    public ResponseEntity<?> softDeleteFile(@PathVariable Long fileId) {
        adminService.softDeleteFile(fileId);
        return ResponseEntity.ok(Map.of("message", "File soft deleted successfully"));
    }

    @PutMapping("/file/revoke-sharing/{fileId}")
    public ResponseEntity<?> revokeSharing(@PathVariable Long fileId) {
        adminService.revokeSharing(fileId);
        return ResponseEntity.ok(Map.of("message", "All sharing permissions revoked successfully"));
    }

    // 8. Audit Logs View
    @GetMapping("/audits")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }
}
