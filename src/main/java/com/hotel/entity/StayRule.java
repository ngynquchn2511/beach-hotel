package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Luat ban phong theo mua: so dem toi thieu (tinh theo ngay nhan phong) va / hoac dong ban (moi dem trong khoang).
// roomType null = ap cho moi loai phong. Chi ap cho dat online / doan / khach tu doi ngay - dat tai quay khong bi chan
@Entity
@Table(name = "stay_rules", indexes = @Index(name = "idx_stay_rule_dates", columnList = "start_date, end_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StayRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_type_id")
    private RoomType roomType;

    // null = khong gioi han so dem
    @Column(name = "min_nights")
    private Integer minNights;

    // true = khong nhan dat cho cac dem trong khoang
    @Column(name = "stop_sell", nullable = false)
    private Boolean stopSell;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    public boolean isClosed() {
        return Boolean.TRUE.equals(stopSell);
    }

    public boolean appliesTo(RoomType type) {
        return roomType == null || (type != null && roomType.getId().equals(type.getId()));
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stopSell == null) {
            stopSell = false;
        }
    }
}
