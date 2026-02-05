package com.securefilesharing.repository;

import com.securefilesharing.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByTimestampDesc();

    boolean existsByAction(String action);

    boolean existsByActionAndTargetUserId(String action, Long targetUserId);

        @Query("""
                        select a from AuditLog a
                        where (:actorUserId is null or a.user.id = :actorUserId)
                            and (:fileId is null or a.fileId = :fileId)
                            and (:fileOwnerId is null or a.fileOwnerId = :fileOwnerId)
                            and (:action is null or a.action = :action)
                            and (:fromTs is null or a.timestamp >= :fromTs)
                            and (:toTs is null or a.timestamp <= :toTs)
                        order by a.timestamp desc
                        """)
        List<AuditLog> search(
                        @Param("actorUserId") Long actorUserId,
                        @Param("fileId") Long fileId,
                        @Param("fileOwnerId") Long fileOwnerId,
                        @Param("action") String action,
                        @Param("fromTs") LocalDateTime fromTs,
                        @Param("toTs") LocalDateTime toTs
        );
}
