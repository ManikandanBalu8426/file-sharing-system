package com.securefilesharing.controller;

import com.securefilesharing.entity.AuditLog;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    // ADMIN + AUDITOR: filter logs by user/file/date/action
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<List<AuditLog>> logs(
            @RequestParam(value = "userId", required = false) Long actorUserId,
            @RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "fileOwnerId", required = false) Long fileOwnerId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(auditService.searchLogs(actorUserId, fileId, fileOwnerId, normalize(action), from, to));
    }

    // AUDITOR: export logs (CSV)
    @GetMapping("/logs/export")
    @PreAuthorize("hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<String> export(
            @RequestParam(value = "userId", required = false) Long actorUserId,
            @RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "fileOwnerId", required = false) Long fileOwnerId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<AuditLog> logs = auditService.searchLogs(actorUserId, fileId, fileOwnerId, normalize(action), from, to);

        StringBuilder csv = new StringBuilder();
        csv.append("id,timestamp,action,actorUserId,fileId,fileOwnerId,targetUserId,details\n");
        for (AuditLog l : logs) {
            csv.append(val(l.getId())).append(',')
                    .append(val(l.getTimestamp())).append(',')
                    .append(csvEsc(l.getAction())).append(',')
                    .append(val(l.getUser() != null ? l.getUser().getId() : null)).append(',')
                    .append(val(l.getFileId())).append(',')
                    .append(val(l.getFileOwnerId())).append(',')
                    .append(val(l.getTargetUserId())).append(',')
                    .append(csvEsc(l.getDetails()))
                    .append('\n');
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audits.csv")
                .body(csv.toString());
    }

    // USER: see audit for their own files (basic access/audit visibility)
    @GetMapping("/my-files")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<AuditLog>> myFilesAudit(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        User current = getCurrentUser();
        return ResponseEntity.ok(auditService.searchLogs(null, null, current.getId(), normalize(action), from, to));
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

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String csvEsc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}
