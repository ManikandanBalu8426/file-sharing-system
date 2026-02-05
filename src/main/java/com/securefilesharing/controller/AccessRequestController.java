package com.securefilesharing.controller;

import com.securefilesharing.dto.AccessRequestDto;
import com.securefilesharing.dto.CreateAccessRequestDto;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.service.AccessRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/files")
public class AccessRequestController {

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/{fileId}/access-requests")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> create(@PathVariable Long fileId, @RequestBody CreateAccessRequestDto body) {
        try {
            User user = getCurrentUser();
            AccessRequestDto dto = accessRequestService.createRequest(fileId, body, user);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/access-requests/inbox")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<AccessRequestDto>> inbox() {
        User user = getCurrentUser();
        return ResponseEntity.ok(accessRequestService.getInbox(user));
    }

    @GetMapping("/access-requests/mine")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<AccessRequestDto>> mine() {
        User user = getCurrentUser();
        return ResponseEntity.ok(accessRequestService.getMyRequests(user));
    }

    @PatchMapping("/access-requests/{requestId}/approve")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<?> approve(@PathVariable Long requestId) {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(accessRequestService.approve(requestId, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PatchMapping("/access-requests/{requestId}/reject")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<?> reject(@PathVariable Long requestId) {
        try {
            User user = getCurrentUser();
            return ResponseEntity.ok(accessRequestService.reject(requestId, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
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
}
