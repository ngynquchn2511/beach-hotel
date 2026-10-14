package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Khach dat 1 tour lan kem don phong. Gia chot tai thoi diem dat (doi gia tour sau do khong anh huong)
@Entity
@Table(name = "tour_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    private DivingTour tour;

    @Column(name = "tour_date", nullable = false)
    private LocalDate tourDate;

    @Column(nullable = false)
    private Integer participants;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    // Phu phi tao ra khi le tan xac nhan (xoa khi huy) - nho vay tien tour vao hoa don / check-out / doanh thu
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private BookingCharge charge;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ServiceStatus.PENDING;
        }
    }

    // Gio tour bat dau = ngay tour + gio khoi hanh cua tour
    public LocalDateTime getStartAt() {
        return LocalDateTime.of(tourDate, tour.getDepartureTime());
    }
}
