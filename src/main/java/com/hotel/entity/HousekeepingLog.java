package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

// 1 luot don phong: ai don, tu luc nao den luc nao, ket qua kiem (de thong ke nang suat / chat luong buong phong)
@Entity
@Table(name = "housekeeping_logs", indexes = @Index(name = "idx_hk_log_time", columnList = "finished_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HousekeepingLog {

    public enum Result {
        // Sach ngay (khong qua buoc kiem), kiem dat, kiem chua dat (phai lam lai)
        DIRECT, PASSED, FAILED;

        public String getVietnameseLabel() {
            return switch (this) {
                case DIRECT -> "Không qua kiểm";
                case PASSED -> "Kiểm đạt";
                case FAILED -> "Kiểm chưa đạt";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Nhan vien don (null = khong ro, VD tai khoan da xoa)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaner_id")
    private User cleaner;

    @Column(name = "cleaner_name", length = 100)
    private String cleanerName;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Result result;

    @Column(name = "inspected_by", length = 100)
    private String inspectedBy;

    @Column(length = 300)
    private String note;

    // Thoi gian don (phut); null neu khong biet luc bat dau
    public Long getMinutes() {
        return startedAt == null || finishedAt.isBefore(startedAt) ? null : Duration.between(startedAt, finishedAt).toMinutes();
    }
}
