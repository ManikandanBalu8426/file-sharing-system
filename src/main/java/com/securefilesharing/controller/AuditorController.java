package com.securefilesharing.controller;

import com.securefilesharing.dto.AuditFileMetadataDto;
import com.securefilesharing.dto.AuditLogDto;
import com.securefilesharing.dto.AuditUserMetadataDto;
import com.securefilesharing.entity.AuditLog;
import com.securefilesharing.entity.FileEntity;
import com.securefilesharing.entity.User;
import com.securefilesharing.repository.AuditLogRepository;
import com.securefilesharing.repository.FileRepository;
import com.securefilesharing.repository.UserRepository;
import com.securefilesharing.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AuditorController - Read-only endpoints for the Auditor role.
 * 
 * Provides access to:
 * - Audit logs (paginated, with filters)
 * - File metadata (NO file content or paths)
 * - User metadata (NO passwords or sensitive data)
 * 
 * All endpoints are GET-only. No POST, PUT, DELETE operations allowed.
 * Audit logs are immutable - no update/delete endpoints.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN', 'SUPER_ADMIN')")
public class AuditorController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    // ==================== AUDIT LOG ENDPOINTS ====================

    /**
     * GET /api/audit - Retrieve paginated audit logs with optional filters.
     * 
     * Query parameters:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20, max: 100)
     * - username: Filter by username (partial match)
     * - action: Filter by action type (exact match)
     * - status: Filter by status (SUCCESS/FAILURE)
     * - fileName: Filter by file name (partial match)
     * - startDate: Filter logs from this date (ISO format)
     * - endDate: Filter logs until this date (ISO format)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication,
            HttpServletRequest request) {

        // Enforce max page size
        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        // Use advanced search with username and fileName filters
        Page<AuditLog> logsPage = auditLogRepository.searchLogsAdvanced(
                null, // userId not used in new API
                normalizeFilter(username),
                normalizeFilter(action),
                normalizeFilter(status),
                null, // resourceType not exposed in simple API
                normalizeFilter(fileName),
                startDate,
                endDate,
                pageable
        );

        // Convert to DTOs
        List<AuditLogDto> logDtos = logsPage.getContent().stream()
                .map(this::toAuditLogDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("logs", logDtos);
        response.put("currentPage", logsPage.getNumber());
        response.put("totalItems", logsPage.getTotalElements());
        response.put("totalPages", logsPage.getTotalPages());
        response.put("pageSize", size);

        // Log the audit access
        logAuditAccess(authentication, request, AuditService.ACTION_VIEW_AUDIT_LOGS, "AUDIT_LOG", null);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/audit/filters - Get available filter options (action types, usernames).
     */
    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("actions", auditLogRepository.findDistinctActions());
        filters.put("usernames", auditLogRepository.findDistinctUsernames());
        filters.put("statuses", List.of("SUCCESS", "FAILURE"));
        return ResponseEntity.ok(filters);
    }

    /**
     * GET /api/audit/export - Export audit logs as CSV.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> exportAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication,
            HttpServletRequest request) {

        // Get all matching logs (limited to 10000 for safety)
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("timestamp").descending());
        Page<AuditLog> logsPage = auditLogRepository.searchLogsAdvanced(
                null,
                normalizeFilter(username),
                normalizeFilter(action),
                normalizeFilter(status),
                null,
                normalizeFilter(fileName),
                startDate,
                endDate,
                pageable
        );

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Timestamp,Username,Action,Status,File Name,Entity ID,IP Address,User Agent,Details\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (AuditLog log : logsPage.getContent()) {
            csv.append(val(log.getId())).append(',')
                    .append(log.getTimestamp() != null ? log.getTimestamp().format(formatter) : "").append(',')
                    .append(csvEsc(log.getUsername())).append(',')
                    .append(csvEsc(log.getAction())).append(',')
                    .append(csvEsc(log.getStatus())).append(',')
                    .append(csvEsc(log.getFileName())).append(',')
                    .append(val(log.getResourceId())).append(',')
                    .append(csvEsc(log.getIpAddress())).append(',')
                    .append(csvEsc(truncate(log.getUserAgent(), 50))).append(',')
                    .append(csvEsc(log.getDetails()))
                    .append('\n');
        }

        logAuditAccess(authentication, request, "EXPORT_AUDIT_LOGS", "AUDIT_LOG", null);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_logs_" + timestamp + ".csv")
                .body(csv.toString());
    }

    /**
     * GET /api/audit/{id} - Retrieve a single audit log by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAuditLogById(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        return auditLogRepository.findById(id)
                .map(log -> {
                    logAuditAccess(authentication, request, "VIEW_AUDIT_LOG_DETAIL", "AUDIT_LOG", id.toString());
                    return ResponseEntity.ok(toAuditLogDto(log));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/audit/stats - Get summary statistics of audit logs.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAuditStats(
            Authentication authentication,
            HttpServletRequest request) {

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", auditLogRepository.count());
        stats.put("successCount", auditLogRepository.countByStatus(AuditService.STATUS_SUCCESS));
        stats.put("failureCount", auditLogRepository.countByStatus(AuditService.STATUS_FAILURE));

        logAuditAccess(authentication, request, "VIEW_AUDIT_STATS", "AUDIT_LOG", null);

        return ResponseEntity.ok(stats);
    }

    // ==================== FILE METADATA ENDPOINTS ====================

    /**
     * GET /api/audit/files - Retrieve file metadata (NO content or paths).
     * 
     * Query parameters:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20, max: 100)
     * - search: Optional search term for filename
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFileMetadata(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            Authentication authentication,
            HttpServletRequest request) {

        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<FileEntity> filesPage = fileRepository.searchByFileName(search, pageable);

        // Convert to DTOs - ONLY metadata, NO paths or content
        List<AuditFileMetadataDto> fileDtos = filesPage.getContent().stream()
                .map(this::toFileMetadataDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("files", fileDtos);
        response.put("currentPage", filesPage.getNumber());
        response.put("totalItems", filesPage.getTotalElements());
        response.put("totalPages", filesPage.getTotalPages());

        logAuditAccess(authentication, request, "VIEW_FILE_METADATA", "FILE", null);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/audit/files/{id} - Retrieve metadata for a single file.
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<?> getFileMetadataById(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        return fileRepository.findById(id)
                .map(file -> {
                    logAuditAccess(authentication, request, "VIEW_FILE_METADATA_DETAIL", "FILE", id.toString());
                    return ResponseEntity.ok(toFileMetadataDto(file));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/audit/files/stats - Get file statistics.
     */
    @GetMapping("/files/stats")
    public ResponseEntity<Map<String, Object>> getFileStats(
            Authentication authentication,
            HttpServletRequest request) {

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", fileRepository.count());
        stats.put("totalSizeBytes", fileRepository.sumAllSizeBytes());

        logAuditAccess(authentication, request, "VIEW_FILE_STATS", "FILE", null);

        return ResponseEntity.ok(stats);
    }

    // ==================== USER METADATA ENDPOINTS ====================

    /**
     * GET /api/audit/users - Retrieve user metadata (NO passwords or sensitive data).
     * 
     * Query parameters:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20, max: 100)
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserMetadata(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest request) {

        if (size > 100) {
            size = 100;
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage = userRepository.findAll(pageable);

        // Convert to DTOs - ONLY non-sensitive metadata
        List<AuditUserMetadataDto> userDtos = usersPage.getContent().stream()
                .map(this::toUserMetadataDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("users", userDtos);
        response.put("currentPage", usersPage.getNumber());
        response.put("totalItems", usersPage.getTotalElements());
        response.put("totalPages", usersPage.getTotalPages());

        logAuditAccess(authentication, request, "VIEW_USER_METADATA", "USER", null);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/audit/users/{id} - Retrieve metadata for a single user.
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserMetadataById(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request) {

        return userRepository.findById(id)
                .map(user -> {
                    logAuditAccess(authentication, request, "VIEW_USER_METADATA_DETAIL", "USER", id.toString());
                    return ResponseEntity.ok(toUserMetadataDto(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/audit/users/stats - Get user statistics.
     */
    @GetMapping("/users/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(
            Authentication authentication,
            HttpServletRequest request) {

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("activeUsers", userRepository.countByActiveTrue());
        stats.put("inactiveUsers", userRepository.findByActiveFalse().size());

        logAuditAccess(authentication, request, "VIEW_USER_STATS", "USER", null);

        return ResponseEntity.ok(stats);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Convert AuditLog entity to DTO with all fields including fileName.
     */
    private AuditLogDto toAuditLogDto(AuditLog log) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        dto.setUsername(log.getUsername() != null ? log.getUsername() : "SYSTEM");
        dto.setRole(log.getRole());
        dto.setAction(log.getAction());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setFileName(log.getFileName());
        dto.setStatus(log.getStatus());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());
        dto.setTimestamp(log.getTimestamp());
        dto.setDetails(log.getDetails());
        return dto;
    }

    /**
     * Convert FileEntity to metadata DTO.
     * EXCLUDES: encryptedPath (file location), actual file content
     */
    private AuditFileMetadataDto toFileMetadataDto(FileEntity file) {
        AuditFileMetadataDto dto = new AuditFileMetadataDto();
        dto.setFileId(file.getId());
        dto.setFileName(file.getFileName());
        dto.setFileSize(file.getSizeBytes());
        dto.setFileType(file.getContentType());
        dto.setUploadedBy(file.getOwner() != null ? file.getOwner().getUsername() : "Unknown");
        dto.setUploadDate(file.getUploadTimestamp());
        dto.setVisibilityLevel(file.getVisibilityType() != null ? file.getVisibilityType().name() : "PRIVATE");
        dto.setCategory(file.getCategory());
        dto.setPurpose(file.getPurpose());
        return dto;
    }

    /**
     * Convert User to metadata DTO.
     * EXCLUDES: password, any JWT tokens, sensitive security data
     * Email is masked for privacy.
     */
    private AuditUserMetadataDto toUserMetadataDto(User user) {
        AuditUserMetadataDto dto = new AuditUserMetadataDto();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        dto.setAccountStatus(user.isActive() ? "ACTIVE" : "INACTIVE");
        dto.setCreatedDate(null); // User entity doesn't have createdDate - can be added later
        dto.setEmail(maskEmail(user.getEmail()));
        return dto;
    }

    /**
     * Mask email for privacy (show first 2 chars and domain).
     * Example: john.doe@example.com -> jo***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }

    /**
     * Log audit access events (auditor viewing data is itself auditable).
     */
    private void logAuditAccess(Authentication authentication, HttpServletRequest request,
                                 String action, String resourceType, String resourceId) {
        try {
            if (authentication != null && authentication.isAuthenticated()) {
                auditService.logEvent(request, authentication, action, resourceType, resourceId,
                        AuditService.STATUS_SUCCESS, null);
            }
        } catch (Exception e) {
            // Don't fail the request if audit logging fails
        }
    }

    /**
     * Normalize filter string - return null for empty/blank strings.
     */
    private String normalizeFilter(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Convert value to string for CSV.
     */
    private String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * Escape string for CSV format.
     */
    private String csvEsc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
