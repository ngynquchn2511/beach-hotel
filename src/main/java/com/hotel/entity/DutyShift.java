package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Lich truc: nhan vien X truc ca Y ngay Z (quan tri xep theo tuan). Ca dem 22h - 6h sang hom sau tinh vao ngay bat dau ca
@Entity
@Table(name = "staff_duty_shifts",
        uniqueConstraints = @UniqueConstraint(name = "uk_duty_shift", columnNames = {"work_date", "shift", "staff_id"}),
        indexes = @Index(name = "idx_duty_date", columnList = "work_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DutyShift {

    public enum Shift {
        MORNING("Ca sáng", 6, 8),
        AFTERNOON("Ca chiều", 14, 8),
        NIGHT("Ca đêm", 22, 8);

        private final String label;
        private final int startHour;
        private final int hours;

        Shift(String label, int startHour, int hours) {
            this.label = label;
            this.startHour = startHour;
            this.hours = hours;
        }

        public String getLabel() {
            return label;
        }

        public LocalTime getStart() {
            return LocalTime.of(startHour, 0);
        }

        // VD "06:00-14:00"
        public String getTimeRange() {
            return String.format("%02d:00-%02d:00", startHour, (startHour + hours) % 24);
        }

        public LocalDateTime startOn(LocalDate day) {
            return day.atTime(startHour, 0);
        }

        public LocalDateTime endOn(LocalDate day) {
            return startOn(day).plusHours(hours);
        }

        // Ca dang dien ra luc t (ca dem sau 0h thuoc ngay hom truoc)
        public static Shift at(LocalTime t) {
            int h = t.getHour();
            return h >= 6 && h < 14 ? MORNING : h >= 14 && h < 22 ? AFTERNOON : NIGHT;
        }

        // Ngay bat dau cua ca dang dien ra luc t
        public static LocalDate workDateAt(LocalDateTime t) {
            return t.getHour() < 6 ? t.toLocalDate().minusDays(1) : t.toLocalDate();
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(length = 200)
    private String note;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getStartAt() {
        return shift.startOn(workDate);
    }

    public LocalDateTime getEndAt() {
        return shift.endOn(workDate);
    }
}
