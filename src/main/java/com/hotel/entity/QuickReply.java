package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Mau tra loi nhanh cho le tan khi nhan tin voi khach (admin quan ly o /admin/quick-replies).
// Noi dung co the chua bien {ten_khach}, {ma_don}, {so_phong}, {ngay_den}, {ngay_di}, {gio_nhan}, {gio_tra}
@Entity
@Table(name = "quick_replies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean active;

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
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
