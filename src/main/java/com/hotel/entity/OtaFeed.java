package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Link lich iCal xuat tu kenh OTA (Booking.com / Airbnb) cho 1 phong - he thong doc dinh ky de khoa ngay da ban tren kenh
@Entity
@Table(name = "ota_feeds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaFeed {

    public enum Channel {
        BOOKING("Booking.com"), AIRBNB("Airbnb"), OTHER("Kênh khác");

        private final String label;

        Channel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 600)
    private String url;

    @Column(nullable = false)
    private Boolean active;

    // Lan dong bo gan nhat: thoi diem, thanh cong hay khong, thong bao, so su kien doc duoc
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_ok")
    private Boolean lastOk;

    @Column(name = "last_message", length = 300)
    private String lastMessage;

    @Column(name = "event_count")
    private Integer eventCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isOpen() {
        return Boolean.TRUE.equals(active);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }
}
