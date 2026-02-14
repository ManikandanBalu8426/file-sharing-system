package com.securefilesharing.repository;

import com.securefilesharing.entity.AccessRequestStatus;
import com.securefilesharing.entity.FileAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileAccessRequestRepository extends JpaRepository<FileAccessRequest, Long> {

    List<FileAccessRequest> findByFileOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, AccessRequestStatus status);

    List<FileAccessRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    Optional<FileAccessRequest> findFirstByFileIdAndRequesterIdAndStatusAndExpiresAtAfterOrderByDecidedAtDesc(
            Long fileId,
            Long requesterId,
            AccessRequestStatus status,
            LocalDateTime now);

    void deleteByFileId(Long fileId);

    long countByFileIdAndStatus(Long fileId, AccessRequestStatus status);
}
