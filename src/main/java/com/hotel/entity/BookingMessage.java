package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Tin nhan giua khach va le tan tren 1 don (khach gui tu trang don, le tan tra loi o trang don admin / hop tin nhan)
@Entity
@Table(name = "booking_messages", indexes = @Index(name = "idx_booking_message", columnList = "booking_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // true = khach gui, false = le tan tra loi
    @Column(name = "from_guest", nullable = false)
    private Boolean fromGuest;

    @Column(nullable = false, length = 1000)
    private String body;

    // Ten nhan vien tra loi (null voi tin cua khach)
    @Column(name = "staff_name", length = 100)
    private String staffName;

    // Anh khach dinh kem (VD chup cho hong can sua) - duong dan /uploads/messages/<uuid>.jpg
    @Column(name = "image_url", length = 200)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Tin cua khach: le tan da doc luc nao; tin cua le tan: khach da doc luc nao
    @Column(name = "read_at")
    private LocalDateTime readAt;

    public boolean isFromGuest() {
        return Boolean.TRUE.equals(fromGuest);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
