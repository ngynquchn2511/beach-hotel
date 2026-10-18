package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Loi moi nang hang phong gui khach truoc ngay den (tu dong) hoac le tan gui tay: khach dong y -> doi phong + cong tien
@Entity
@Table(name = "upgrade_offers", indexes = @Index(name = "idx_upgrade_booking", columnList = "booking_id, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpgradeOffer {

    public enum Status {
        OFFERED, ACCEPTED, DECLINED, EXPIRED;

        public String getVietnameseLabel() {
            return switch (this) {
                case OFFERED -> "Đang mời";
                case ACCEPTED -> "Khách đồng ý";
                case DECLINED -> "Khách từ chối";
                case EXPIRED -> "Hết hạn";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // Phong cu the duoc giu cho loi moi (kiem tra lai con trong khi khach dong y)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Chenh lech tien phong theo bang gia va gia moi (da tru uu dai)
    @Column(name = "full_difference", nullable = false, precision = 12, scale = 2)
    private BigDecimal fullDifference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public boolean isOpen(LocalDateTime now) {
        return status == Status.OFFERED && expiresAt.isAfter(now);
    }

    public BigDecimal getSaving() {
        return fullDifference.subtract(price).max(BigDecimal.ZERO);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.OFFERED;
        }
    }
}
