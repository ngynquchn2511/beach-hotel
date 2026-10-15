package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Phieu bao hong phong (dieu hoa, nuoc, dien...): Moi -> Dang sua -> Xong. blocksRoom = khoa phong (trang thai Bao tri)
@Entity
@Table(name = "maintenance_tickets", indexes = @Index(name = "idx_mt_status", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceTicket {

    public enum Priority {
        NORMAL, URGENT;

        public String getVietnameseLabel() {
            return this == URGENT ? "Khẩn" : "Bình thường";
        }
    }

    public enum Status {
        OPEN, IN_PROGRESS, DONE;

        public String getVietnameseLabel() {
            return switch (this) {
                case OPEN -> "Mới báo";
                case IN_PROGRESS -> "Đang sửa";
                case DONE -> "Đã xong";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    // Khoa phong khong cho dat / nhan phong trong luc sua
    @Column(name = "blocks_room", nullable = false)
    private Boolean blocksRoom;

    @Column(name = "expected_done_date")
    private LocalDate expectedDoneDate;

    @Column(name = "reported_by", length = 100)
    private String reportedBy;

    @Column(length = 100)
    private String assignee;

    @Column(name = "resolution_note", length = 300)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public boolean isOpen() {
        return status != Status.DONE;
    }

    public boolean isBlocking() {
        return isOpen() && Boolean.TRUE.equals(blocksRoom);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
