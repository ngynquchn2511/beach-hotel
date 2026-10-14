package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // Dung de xac dinh khach lan dau / khach quen / khach VIP khi ap ma giam gia
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    // Token dat lai mat khau (quen mat khau) - chi ton tai trong thoi gian ngan, xoa sau khi dung
    @Column(name = "reset_token", length = 64, unique = true)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // Tai khoan bi khoa thi khong dang nhap duoc. De kieu Boolean (cho phep null = khong khoa) de khi
    // ddl-auto=update them cot moi vao bang da co du lieu, cac tai khoan cu khong bi khoa nham
    @Column(name = "locked")
    private Boolean locked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.customerType == null) {
            this.customerType = CustomerType.NEW;
        }
    }

    public boolean isLocked() {
        return Boolean.TRUE.equals(locked);
    }
}
