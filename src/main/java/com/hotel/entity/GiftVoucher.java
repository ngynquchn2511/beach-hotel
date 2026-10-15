package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// The qua tang: mua online (VNPay / chuyen khoan) -> ma gui nguoi nhan -> dung tru tien khi dat phong (dung nhieu lan toi het so du)
@Entity
@Table(name = "gift_vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftVoucher {

    public enum Status {
        PENDING_PAYMENT, ACTIVE, CANCELLED;

        public String getVietnameseLabel() {
            return switch (this) {
                case PENDING_PAYMENT -> "Chờ thanh toán";
                case ACTIVE -> "Đang dùng được";
                case CANCELLED -> "Đã hủy";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // VD QT-7K3M-9PXA
    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "purchaser_name", nullable = false, length = 100)
    private String purchaserName;

    @Column(name = "purchaser_email", nullable = false, length = 100)
    private String purchaserEmail;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_email", nullable = false, length = 100)
    private String recipientEmail;

    @Column(length = 300)
    private String message;

    // Thanh toan: VNPay (ma giao dich) hoac chuyen khoan le tan xac nhan (null)
    @Column(name = "online_ref", length = 40)
    private String onlineRef;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Nguoi mua xem lai trang thanh toan
    @Column(name = "access_token", nullable = false, length = 32, unique = true)
    private String accessToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsable() {
        return status == Status.ACTIVE && balance.signum() > 0 && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
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
