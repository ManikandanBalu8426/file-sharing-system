package com.securefilesharing.service;

import com.securefilesharing.dto.AccessRequestDto;
import com.securefilesharing.dto.CreateAccessRequestDto;
import com.securefilesharing.entity.*;
import com.securefilesharing.repository.FileAccessRequestRepository;
import com.securefilesharing.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccessRequestService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_AUDITOR = "ROLE_AUDITOR";

    @Autowired
    private FileAccessRequestRepository fileAccessRequestRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AuditService auditService;

    @Value("${app.protected-access.ttlSeconds:3600}")
    private long protectedAccessTtlSeconds;

    @Transactional
    public AccessRequestDto createRequest(Long fileId, CreateAccessRequestDto body, User requester) {
        if (!ROLE_ADMIN.equals(requester.getRole())) {
            throw new RuntimeException("Only ADMIN can request access");
        }
        if (fileId == null) {
            throw new RuntimeException("fileId is required");
        }
        if (body == null || body.getAccessType() == null) {
            throw new RuntimeException("Access type is required");
        }
        if (body.getPurpose() == null || body.getPurpose().trim().isEmpty()) {
            throw new RuntimeException("Purpose is required");
        }

        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (file.getVisibilityType() != VisibilityType.PROTECTED) {
            throw new RuntimeException("Access requests are only allowed for PROTECTED files");
        }
        if (file.getOwner() != null && file.getOwner().getId() != null && file.getOwner().getId().equals(requester.getId())) {
            throw new RuntimeException("Owner does not need to request access");
        }

        FileAccessRequest req = new FileAccessRequest();
        req.setFile(file);
        req.setRequester(requester);
        req.setAccessType(body.getAccessType());
        req.setStatus(AccessRequestStatus.PENDING);
        req.setPurpose(body.getPurpose().trim());
        req.setCreatedAt(LocalDateTime.now());

        FileAccessRequest saved = fileAccessRequestRepository.save(req);
        auditService.logSuccess(AuditService.ACTION_ACCESS_REQUEST, AuditService.RESOURCE_ACCESS_REQUEST, saved.getId(),
            file.getFileName(), "Requested " + saved.getAccessType() + " access for file: " + file.getFileName());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDto> getInbox(User owner) {
        if (ROLE_AUDITOR.equals(owner.getRole())) {
            return List.of();
        }
        return fileAccessRequestRepository
                .findByFileOwnerIdAndStatusOrderByCreatedAtDesc(owner.getId(), AccessRequestStatus.PENDING)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDto> getMyRequests(User requester) {
        if (ROLE_AUDITOR.equals(requester.getRole())) {
            return List.of();
        }
        return fileAccessRequestRepository
                .findByRequesterIdOrderByCreatedAtDesc(requester.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AccessRequestDto approve(Long requestId, User approver) {
        if (requestId == null) {
            throw new RuntimeException("requestId is required");
        }
        FileAccessRequest req = fileAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (ROLE_AUDITOR.equals(approver.getRole())) {
            throw new RuntimeException("Auditor cannot approve access");
        }
        if (req.getFile() == null || req.getFile().getOwner() == null || req.getFile().getOwner().getId() == null) {
            throw new RuntimeException("Invalid request owner");
        }
        if (!req.getFile().getOwner().getId().equals(approver.getId())) {
            throw new RuntimeException("Only the file owner can approve access");
        }
        if (req.getStatus() != AccessRequestStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be approved");
        }

        req.setStatus(AccessRequestStatus.APPROVED);
        req.setDecidedAt(LocalDateTime.now());
        req.setExpiresAt(LocalDateTime.now().plusSeconds(Math.max(60, protectedAccessTtlSeconds)));

        FileAccessRequest saved = fileAccessRequestRepository.save(req);
        String fileName = saved.getFile() != null ? saved.getFile().getFileName() : null;
        auditService.logSuccess(AuditService.ACTION_ACCESS_GRANT, AuditService.RESOURCE_ACCESS_REQUEST, saved.getId(),
            fileName, "Approved access request for file: " + fileName);
        return toDto(saved);
    }

    @Transactional
    public AccessRequestDto reject(Long requestId, User approver) {
        if (requestId == null) {
            throw new RuntimeException("requestId is required");
        }
        FileAccessRequest req = fileAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (ROLE_AUDITOR.equals(approver.getRole())) {
            throw new RuntimeException("Auditor cannot reject access");
        }
        if (req.getFile() == null || req.getFile().getOwner() == null || req.getFile().getOwner().getId() == null) {
            throw new RuntimeException("Invalid request owner");
        }
        if (!req.getFile().getOwner().getId().equals(approver.getId())) {
            throw new RuntimeException("Only the file owner can reject access");
        }
        if (req.getStatus() != AccessRequestStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be rejected");
        }

        req.setStatus(AccessRequestStatus.REJECTED);
        req.setDecidedAt(LocalDateTime.now());
        req.setExpiresAt(null);

        FileAccessRequest saved = fileAccessRequestRepository.save(req);
        String fileName = saved.getFile() != null ? saved.getFile().getFileName() : null;
        auditService.logSuccess(AuditService.ACTION_ACCESS_DENY, AuditService.RESOURCE_ACCESS_REQUEST, saved.getId(),
            fileName, "Rejected access request for file: " + fileName);
        return toDto(saved);
    }

    private AccessRequestDto toDto(FileAccessRequest r) {
        AccessRequestDto dto = new AccessRequestDto();
        dto.setId(r.getId());
        dto.setFileId(r.getFile() != null ? r.getFile().getId() : null);
        dto.setFileName(r.getFile() != null ? r.getFile().getFileName() : null);
        dto.setOwnerUsername(r.getFile() != null && r.getFile().getOwner() != null ? r.getFile().getOwner().getUsername() : null);
        dto.setRequesterUsername(r.getRequester() != null ? r.getRequester().getUsername() : null);
        dto.setAccessType(r.getAccessType());
        dto.setStatus(r.getStatus());
        dto.setPurpose(r.getPurpose());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setDecidedAt(r.getDecidedAt());
        dto.setExpiresAt(r.getExpiresAt());
        return dto;
    }
}
