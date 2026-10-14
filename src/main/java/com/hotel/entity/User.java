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

    // Xac thuc email: false = chua kich hoat (tu dang ky chua bam link, hoac tai khoan tu tao khi dat phong
    // khong dang nhap) -> chua dang nhap duoc. null = tai khoan cu / admin / nhan vien -> coi nhu da xac thuc
    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "email_verify_token", length = 64)
    private String emailVerifyToken;

    @Column(name = "email_verify_expiry")
    private LocalDateTime emailVerifyExpiry;

    // Chong do mat khau (LoginAttemptService): so lan sai lien tiep va thoi diem het khoa tam.
    // Cho phep null (tai khoan cu) = chua sai lan nao / khong bi khoa tam
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    @Column(name = "login_locked_until")
    private LocalDateTime loginLockedUntil;

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

    public boolean isEmailVerified() {
        return !Boolean.FALSE.equals(emailVerified);
    }

    // Chu email da chung minh so huu (bam link kich hoat / dat lai mat khau qua email)
    public void markEmailVerified() {
        emailVerified = true;
        emailVerifyToken = null;
        emailVerifyExpiry = null;
    }

    // Dang bi khoa tam vi nhap sai mat khau nhieu lan (tu mo khi het han)
    public boolean isTemporarilyLocked() {
        return loginLockedUntil != null && loginLockedUntil.isAfter(LocalDateTime.now());
    }

    public int getFailedLoginCount() {
        return failedLoginAttempts == null ? 0 : failedLoginAttempts;
    }

    // Nut Khoa / Mo khoa cua admin: dang khoa tam (sai mat khau) -> mo ngay; con lai doi trang thai khoa,
    // mo khoa thi xoa luon dem sai mat khau
    public void toggleLockByAdmin() {
        if (!isLocked() && isTemporarilyLocked()) {
            clearLoginFailures();
            return;
        }
        locked = !isLocked();
        if (!locked) {
            clearLoginFailures();
        }
    }

    // Xoa dem sai mat khau + khoa tam (dang nhap dung, admin mo khoa, dat lai mat khau qua email)
    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        loginLockedUntil = null;
    }
}
