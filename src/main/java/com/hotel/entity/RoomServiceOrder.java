package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Khach dang o goi mon len phong. Le tan bam "Da giao" -> tien cong vao hoa don tra phong (booking_charges)
@Entity
@Table(name = "room_service_orders", indexes = @Index(name = "idx_rs_order_status", columnList = "status, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomServiceOrder {

    public enum Status {
        PENDING, DELIVERED, CANCELLED;

        public String getVietnameseLabel() {
            return switch (this) {
                case PENDING -> "Chờ giao";
                case DELIVERED -> "Đã giao";
                case CANCELLED -> "Đã hủy";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 200)
    private String note;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "handled_by", length = 100)
    private String handledBy;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<RoomServiceOrderLine> lines = new ArrayList<>();

    // "Phở bò x2, Nước cam x1"
    public String getSummary() {
        return lines.stream().map(l -> l.getItemName() + " x" + l.getQuantity()).collect(Collectors.joining(", "));
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
