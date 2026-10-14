package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Nhat ky thao tac: ai (actor*) da lam gi (action) voi doi tuong nao (entityType + entityId), luc nao (createdAt),
// thay doi cu the nhung truong nao (changes). Duoc ghi tu dong boi AuditEntityListener - khong sua/xoa tu giao dien.
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Luu ca id lan email/ten/vai tro tai thoi diem thao tac - neu tai khoan bi doi ten/xoa ve sau thi log van dung
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    // ADMIN / STAFF / CUSTOMER, hoac GUEST (khach vang lai chua dang nhap), SYSTEM (tac vu tu dong)
    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    // Ten class entity, VD: Booking, Room, User
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 50)
    private String entityId;

    // Moi dong 1 truong: "Nhan: gia cu → gia moi"
    @Column(columnDefinition = "TEXT")
    private String changes;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "request_url", length = 255)
    private String requestUrl;

    public String getActorRoleLabel() {
        return switch (actorRole) {
            case "ADMIN" -> "Quản trị viên";
            case "STAFF" -> "Nhân viên";
            case "CUSTOMER" -> "Khách hàng";
            case "GUEST" -> "Khách vãng lai";
            default -> "Hệ thống";
        };
    }

    public String getEntityTypeLabel() {
        return com.hotel.audit.AuditLabels.entityLabel(entityType);
    }
}
