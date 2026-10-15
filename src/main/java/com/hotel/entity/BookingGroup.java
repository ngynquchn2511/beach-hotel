package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Dat nhieu phong 1 lan (gia dinh / doan): moi phong van la 1 don rieng, gom chung 1 nhom de xem / coc / huy cung nhau
@Entity
@Table(name = "booking_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    @Column(name = "guest_email", length = 100)
    private String guestEmail;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    // Ma truy cap cho khach vang lai xem lai nhom (giong don le)
    @Column(name = "access_token", nullable = false, length = 32, unique = true)
    private String accessToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Ma hien thi, VD "G12"
    public String getCode() {
        return "G" + id;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (accessToken == null) {
            accessToken = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }
}
