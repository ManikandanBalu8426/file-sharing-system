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
        if (requester.getRole() != Role.ROLE_ADMIN) {
            throw new RuntimeException("Only ADMIN can request access");
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
        auditService.logAction(requester, "ACCESS_REQUEST",
            "Requested " + saved.getAccessType() + " for fileId=" + fileId,
            fileId, file.getOwner() != null ? file.getOwner().getId() : null, null);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDto> getInbox(User owner) {
        if (owner.getRole() == Role.ROLE_AUDITOR) {
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
        if (requester.getRole() == Role.ROLE_AUDITOR) {
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
        FileAccessRequest req = fileAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (approver.getRole() == Role.ROLE_AUDITOR) {
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
        auditService.logAction(approver, "ACCESS_APPROVED",
            "Approved requestId=" + saved.getId() + " for fileId=" + saved.getFile().getId(),
            saved.getFile() != null ? saved.getFile().getId() : null,
            saved.getFile() != null && saved.getFile().getOwner() != null ? saved.getFile().getOwner().getId() : null,
            saved.getRequester() != null ? saved.getRequester().getId() : null);
        return toDto(saved);
    }

    @Transactional
    public AccessRequestDto reject(Long requestId, User approver) {
        FileAccessRequest req = fileAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (approver.getRole() == Role.ROLE_AUDITOR) {
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
        auditService.logAction(approver, "ACCESS_REJECTED",
            "Rejected requestId=" + saved.getId() + " for fileId=" + saved.getFile().getId(),
            saved.getFile() != null ? saved.getFile().getId() : null,
            saved.getFile() != null && saved.getFile().getOwner() != null ? saved.getFile().getOwner().getId() : null,
            saved.getRequester() != null ? saved.getRequester().getId() : null);
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
