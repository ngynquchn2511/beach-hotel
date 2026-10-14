package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Danh gia cua khach sau khi tra phong - moi don chi duoc danh gia 1 lan
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // So sao tu 1 den 5
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    // Admin an danh gia (spam, ngon tu khong phu hop...). Kieu Boolean cho phep null = dang hien,
    // de ddl-auto=update them cot vao bang da co du lieu khong bi loi
    @Column(name = "hidden")
    private Boolean hidden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Anh / video dinh kem (toi da 5), theo thu tu khach chon
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private java.util.List<ReviewMedia> media = new java.util.ArrayList<>();

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Ten hien thi cong khai: chi lay 2 chu cuoi cua ho ten (VD "Nguyen Quoc Hoan" -> "Quoc Hoan")
    public String getDisplayName() {
        String name = customer != null && customer.getFullName() != null ? customer.getFullName().trim() : "";
        if (name.isEmpty()) {
            return "Khách hàng";
        }
        String[] parts = name.split("\\s+");
        return parts.length <= 2 ? name : parts[parts.length - 2] + " " + parts[parts.length - 1];
    }
}
