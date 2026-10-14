package com.hotel.repository;

import com.hotel.entity.AuditAction;
import com.hotel.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Moi tham so de null nghia la khong loc theo tieu chi do
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityType IS NULL OR a.entityType = :entityType)
            AND (:action IS NULL OR a.action = :action)
            AND (:actorRole IS NULL OR a.actorRole = :actorRole)
            AND (:from IS NULL OR a.createdAt >= :from)
            AND (:to IS NULL OR a.createdAt < :to)
            AND (:keyword IS NULL
                 OR LOWER(a.actorEmail) LIKE :keyword
                 OR LOWER(a.actorName) LIKE :keyword
                 OR a.entityId = :rawKeyword)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> search(@Param("entityType") String entityType,
                          @Param("action") AuditAction action,
                          @Param("actorRole") String actorRole,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("keyword") String keyword,
                          @Param("rawKeyword") String rawKeyword,
                          Pageable pageable);
}
