package com.securefilesharing.repository;

import com.securefilesharing.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc();

    boolean existsByAction(String action);

    boolean existsByActionAndTargetUserId(String action, Long targetUserId);

    // Filter methods for auditor
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByStatus(String status, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<AuditLog> findByResourceType(String resourceType, Pageable pageable);

    // Find by username (partial match)
    Page<AuditLog> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    // Find by fileName (partial match)
    Page<AuditLog> findByFileNameContainingIgnoreCase(String fileName, Pageable pageable);

    // Advanced paginated search with all filters including username and fileName
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:username IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
              AND (:action IS NULL OR a.action = :action)
              AND (:status IS NULL OR a.status = :status)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:fileName IS NULL OR LOWER(a.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')))
              AND (:startDate IS NULL OR a.timestamp >= :startDate)
              AND (:endDate IS NULL OR a.timestamp <= :endDate)
            ORDER BY a.timestamp DESC
            """)
    Page<AuditLog> searchLogsAdvanced(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("action") String action,
            @Param("status") String status,
            @Param("resourceType") String resourceType,
            @Param("fileName") String fileName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // Original paginated search with multiple filters (kept for backward compatibility)
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR a.action = :action)
              AND (:status IS NULL OR a.status = :status)
              AND (:resourceType IS NULL OR a.resourceType = :resourceType)
              AND (:startDate IS NULL OR a.timestamp >= :startDate)
              AND (:endDate IS NULL OR a.timestamp <= :endDate)
            ORDER BY a.timestamp DESC
            """)
    Page<AuditLog> searchLogs(
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("status") String status,
            @Param("resourceType") String resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // Legacy search (non-paginated) for backward compatibility
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorUserId IS NULL OR a.userId = :actorUserId)
              AND (:fileId IS NULL OR a.fileId = :fileId)
              AND (:fileOwnerId IS NULL OR a.fileOwnerId = :fileOwnerId)
              AND (:action IS NULL OR a.action = :action)
              AND (:fromTs IS NULL OR a.timestamp >= :fromTs)
              AND (:toTs IS NULL OR a.timestamp <= :toTs)
            ORDER BY a.timestamp DESC
            """)
    List<AuditLog> search(
            @Param("actorUserId") Long actorUserId,
            @Param("fileId") Long fileId,
            @Param("fileOwnerId") Long fileOwnerId,
            @Param("action") String action,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );

    // Count by status for dashboard
    long countByStatus(String status);

    // Count by action type
    long countByAction(String action);

    // Get distinct action types for filter dropdown
    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.action IS NOT NULL ORDER BY a.action")
    List<String> findDistinctActions();

    // Get distinct usernames for filter dropdown
    @Query("SELECT DISTINCT a.username FROM AuditLog a WHERE a.username IS NOT NULL ORDER BY a.username")
    List<String> findDistinctUsernames();
}
