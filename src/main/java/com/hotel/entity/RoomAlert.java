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

// Khach muon dat phong nhung ngay da kin -> de lai email, phong trong lai (don huy / doi ngay / het bao tri)
// thi he thong gui email bao kem link dat dung ngay do. Moi yeu cau chi bao 1 lan.
@Entity
@Table(name = "room_alerts", indexes = @Index(name = "idx_room_alert_pending", columnList = "notified_at, check_in"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Room room;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column
    private Integer guests;

    // Ngon ngu khach dang dung luc dang ky ("vi" / "en") de viet email cung ngon ngu
    @Column(length = 5)
    private String language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Thoi diem da gui email bao phong trong (null = dang cho)
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
