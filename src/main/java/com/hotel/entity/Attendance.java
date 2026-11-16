package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Cham cong tu dong: lan dau nhan vien mo cong quan tri trong ngay lam viec = gio vao; moi lan thao tac sau do day gio ra
// (lan thao tac cuoi). Ngay lam viec cua ca dem tinh theo ngay bat dau ca. Quan tri sua tay duoc (so cong, ghi chu)
@Entity
@Table(name = "staff_attendances",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_day", columnNames = {"staff_id", "work_date"}),
        indexes = @Index(name = "idx_attendance_date", columnList = "work_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    // Lam tu 4 tieng tro len = 1 cong, it hon = nua cong
    public static final long FULL_DAY_MINUTES = 4 * 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    // Ca theo lich truc (null = di lam ngoai lich)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DutyShift.Shift shift;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at", nullable = false)
    private LocalDateTime checkOutAt;

    // Da bam "Ket thuc ca"
    @Column(name = "ended")
    private Boolean ended;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    // Quan tri sua so cong (null = tinh tu dong)
    @Column(name = "manual_units", precision = 3, scale = 1)
    private BigDecimal manualUnits;

    @Column(length = 300)
    private String note;

    public long getWorkedMinutes() {
        return Math.max(0, Duration.between(checkInAt, checkOutAt).toMinutes());
    }

    public String getWorkedText() {
        long m = getWorkedMinutes();
        return (m / 60) + "h" + String.format("%02d", m % 60);
    }

    public BigDecimal getAutoUnits() {
        return getWorkedMinutes() >= FULL_DAY_MINUTES ? BigDecimal.ONE : new BigDecimal("0.5");
    }

    public BigDecimal getUnits() {
        return manualUnits != null ? manualUnits : getAutoUnits();
    }

    public boolean isEnded() {
        return Boolean.TRUE.equals(ended);
    }

    public boolean isLate() {
        return lateMinutes != null && lateMinutes > 0;
    }
}
