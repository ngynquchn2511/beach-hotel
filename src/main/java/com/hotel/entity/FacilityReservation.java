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
import java.time.LocalTime;

// Dat lich spa / ban nha hang kem don phong. Spa: le tan xac nhan -> tien vao phu phi don; nha hang: chi giu ban
@Entity
@Table(name = "facility_reservations", indexes = @Index(name = "idx_facility_slot", columnList = "kind, reserved_date, reserved_time"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacilityReservation {

    public enum Kind {
        SPA, RESTAURANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    // Chi voi spa
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spa_package_id")
    private SpaPackage spaPackage;

    @Column(name = "reserved_date", nullable = false)
    private LocalDate reservedDate;

    @Column(name = "reserved_time", nullable = false)
    private LocalTime reservedTime;

    @Column(nullable = false)
    private Integer people;

    // Spa: thoi luong chot luc dat (admin doi thoi luong goi sau do khong anh huong lich da dat)
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // Spa: gia chot luc dat; nha hang: null (tra tai nha hang)
    @Column(name = "total_price", precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private BookingCharge charge;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Danh gia cua khach sau khi dung dich vu (1-5 sao, 1 lan)
    private Integer rating;

    @Column(length = 500)
    private String feedback;

    @Column(name = "rated_at")
    private LocalDateTime ratedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ServiceStatus.PENDING;
        }
    }

    public LocalDateTime getStartAt() {
        return LocalDateTime.of(reservedDate, reservedTime);
    }

    // Spa: chiem giuong den het thoi luong goi
    public LocalDateTime getEndAt() {
        if (!isSpa()) {
            return getStartAt();
        }
        int minutes = durationMinutes != null ? durationMinutes : spaPackage != null ? spaPackage.getMinutes() : 0;
        return getStartAt().plusMinutes(minutes);
    }

    // Danh gia duoc: lich da xac nhan, chua danh gia, buoi hen da xong (nha hang: sau 1 tieng),
    // va chua qua RATE_DAYS ngay sau ngay tra phong
    public static final int RATE_DAYS = 7;

    public boolean isRateable(LocalDateTime now) {
        LocalDateTime done = isSpa() ? getEndAt() : getStartAt().plusHours(1);
        return status == ServiceStatus.CONFIRMED && rating == null && !now.isBefore(done)
                && !now.toLocalDate().isAfter(booking.getCheckOutDate().plusDays(RATE_DAYS));
    }

    public boolean isRated() {
        return rating != null;
    }

    public boolean isSpa() {
        return kind == Kind.SPA;
    }

    public String getSummary() {
        return isSpa() ? "Spa: " + spaPackage.getName() + " - " + people + " người" : "Bàn nhà hàng - " + people + " khách";
    }
}
