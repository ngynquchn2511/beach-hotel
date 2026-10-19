package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 1 dot phong da ban tren kenh OTA (doc tu lich iCal cua kenh): khoa phong tren website trong [startDate, endDate).
// booking != null: le tan da ghi nhan thanh don cua he thong -> don do giu phong, dot nay khong khoa them.
// removedAt != null: kenh da xoa dot nay (khach huy tren kenh) nhung con don lien ket -> can le tan kiem tra
@Entity
@Table(name = "ota_blocks", uniqueConstraints = @UniqueConstraint(name = "uk_ota_block_uid", columnNames = {"feed_id", "uid"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    private OtaFeed feed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 255)
    private String uid;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 200)
    private String summary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public long getNights() {
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }

    public boolean isBlocking() {
        return booking == null && removedAt == null;
    }
}
