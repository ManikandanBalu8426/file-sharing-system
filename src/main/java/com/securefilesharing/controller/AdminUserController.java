package com.securefilesharing.controller;

import com.securefilesharing.dto.AdminUserDto;
import com.securefilesharing.dto.UpdateUserActiveRequest;
import com.securefilesharing.dto.UpdateUserRoleRequest;
import com.securefilesharing.entity.Role;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    @GetMapping
    public ResponseEntity<List<AdminUserDto>> listUsers() {
        List<AdminUserDto> users = userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/{userId}/active")
    public ResponseEntity<?> setActive(@PathVariable Long userId, @RequestBody UpdateUserActiveRequest body) {
        User actor = getCurrentUser();
        if (body == null) {
            return ResponseEntity.badRequest().body("active is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(body.isActive());
        User saved = userRepository.save(user);

        String action = saved.isActive() ? "USER_ACTIVATED" : "USER_DEACTIVATED";
        auditService.logAction(actor, action,
            "Set active=" + saved.isActive() + " for userId=" + saved.getId() + ", username=" + saved.getUsername(),
            null, null, saved.getId());

        return ResponseEntity.ok(toDto(saved));
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<?> setRole(@PathVariable Long userId, @RequestBody UpdateUserRoleRequest body) {
        User actor = getCurrentUser();
        if (body == null || body.getRole() == null || body.getRole().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("role is required");
        }

        String roleText = body.getRole().trim().toUpperCase();
        if (!roleText.startsWith("ROLE_")) {
            roleText = "ROLE_" + roleText;
        }

        Role newRole;
        try {
            newRole = Role.valueOf(roleText);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid role: " + body.getRole());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setRole(newRole);
        User saved = userRepository.save(user);

        auditService.logAction(actor, "USER_ROLE_CHANGE",
            "Set role=" + saved.getRole() + " for userId=" + saved.getId() + ", username=" + saved.getUsername(),
            null, null, saved.getId());

        return ResponseEntity.ok(toDto(saved));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminUserDto toDto(User user) {
        AdminUserDto dto = new AdminUserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        return dto;
    }
}
