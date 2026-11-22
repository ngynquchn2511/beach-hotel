package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Phu phi phat sinh cua 1 don (minibar, giat ui, hu hong, tra phong muon...) - cong vao tien thanh toan khi check-out
@Entity
@Table(name = "booking_charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 20)
    private ChargeType type;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Chi dung cho loai EXTENDED_STAY: khoang dem o them [stayFrom, stayTo) - de hoan tac dung lan gia han gan nhat
    @Column(name = "stay_from")
    private LocalDate stayFrom;

    @Column(name = "stay_to")
    private LocalDate stayTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Ten hien thi tren hoa don: "Minibar - 2 lon bia" hoac chi "Minibar"
    public String getLabel() {
        return description == null || description.isBlank()
                ? type.getVietnameseLabel()
                : type.getVietnameseLabel() + " - " + description;
    }
}
