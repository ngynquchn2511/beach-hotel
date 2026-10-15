package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// So do that lac: do khach bo quen (gan voi phong / don cua khach), dang cat -> da tra khach / da thanh ly
@Entity
@Table(name = "lost_items", indexes = @Index(name = "idx_lost_status", columnList = "status, found_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LostItem {

    public enum Status {
        STORED, RETURNED, DISPOSED;

        public String getVietnameseLabel() {
            return switch (this) {
                case STORED -> "Đang cất giữ";
                case RETURNED -> "Đã trả khách";
                case DISPOSED -> "Đã thanh lý";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room room;

    // Don cua khach de quen (co the null neu khong ro)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Booking booking;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "storage_location", length = 100)
    private String storageLocation;

    @Column(name = "found_by", length = 100)
    private String foundBy;

    @Column(name = "found_at", nullable = false)
    private LocalDateTime foundAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "guest_notified_at")
    private LocalDateTime guestNotifiedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "close_note", length = 200)
    private String closeNote;

    public boolean isStored() {
        return status == Status.STORED;
    }

    @PrePersist
    void onCreate() {
        if (foundAt == null) {
            foundAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.STORED;
        }
    }
}
