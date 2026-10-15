package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// Lich su cong / tru diem thuong cua khach (hien o trang Tai khoan). points > 0 = cong, < 0 = tru
@Entity
@Table(name = "point_transactions", indexes = @Index(name = "idx_point_tx_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransaction {

    public enum Type {
        // Tich diem khi tra phong
        EARN,
        // Dung diem tru tien luc dat
        REDEEM,
        // Don da dung diem bi huy -> tra lai diem
        REFUND;

        public String getVietnameseLabel() {
            return switch (this) {
                case EARN -> "Tích điểm";
                case REDEEM -> "Dùng điểm";
                case REFUND -> "Hoàn điểm";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    @Column(nullable = false)
    private Integer points;

    // So du sau giao dich (de hien lich su ma khong phai cong don lai)
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
