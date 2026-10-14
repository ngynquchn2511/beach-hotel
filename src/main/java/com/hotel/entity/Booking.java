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

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    // Gio nhan/tra phong cu the ma khach chon (VD: nhan 14:00, tra 12:00) - khac voi ngay nhan/tra
    @Column(name = "check_in_time", nullable = false)
    private java.time.LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private java.time.LocalTime checkOutTime;

    @Column(name = "number_of_guests")
    private Integer numberOfGuests;

    // Thong tin lien he cua nguoi truc tiep nhan phong (co the khac tai khoan neu dat ho nguoi khac)
    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Tien phong da tinh theo tung dem (gom phu thu cuoi tuan / ngay le) luc dat.
    // Don tao truoc khi co tinh nang nay de null -> dung gia phong x so dem (xem getEffectiveRoomAmount)
    @Column(name = "room_amount", precision = 12, scale = 2)
    private BigDecimal roomAmount;

    // Tien coc khach can chuyen de giu phong (0/null = khong yeu cau coc, VD don dat tai quay)
    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    // Han chot chuyen coc - qua han ma chua coc thi don tu huy
    @Column(name = "deposit_deadline")
    private LocalDateTime depositDeadline;

    // Thoi diem nhan vien xac nhan da nhan coc (null = chua nhan)
    @Column(name = "deposit_paid_at")
    private LocalDateTime depositPaidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // Combo dich vu khach chon (co the null = phong thuong, khong combo)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    // Ma giam gia da ap dung (co the null = khong dung ma)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id")
    private DiscountCode discountCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Ma ngau nhien trong link xem don gui cho khach (khach vang lai khong dang nhap) - khong doan duoc nhu id.
    // Don tao truoc khi co tinh nang nay de null -> chi chu don da dang nhap moi xem duoc
    @Column(name = "access_token", length = 32, unique = true)
    private String accessToken;

    // Danh dau don moi tao (khach vua dat) de hien thi cham do thong bao cho admin/staff - tat khi staff mo xem chi tiet
    @Column(name = "new_booking", nullable = false)
    @Builder.Default
    private boolean newBooking = true;

    // Danh dau khach vua tu doi gio nhan/tra phong - hien canh bao rieng cho staff, tat khi staff mo xem chi tiet
    @Column(name = "check_in_time_changed", nullable = false)
    @Builder.Default
    private boolean checkInTimeChanged = false;

    public long getNights() {
        return java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    public BigDecimal getEffectiveRoomAmount() {
        return roomAmount != null ? roomAmount : room.getPrice().multiply(BigDecimal.valueOf(getNights()));
    }

    public boolean isDepositRequired() {
        return depositAmount != null && depositAmount.signum() > 0;
    }

    public boolean isDepositPaid() {
        return depositPaidAt != null;
    }

    // Dang cho khach chuyen coc: co yeu cau coc, chua nhan, don van dang cho xac nhan
    public boolean isAwaitingDeposit() {
        return isDepositRequired() && !isDepositPaid() && status == BookingStatus.PENDING;
    }

    // So tien coc da thu (tru vao luc thanh toan khi tra phong)
    public BigDecimal getDepositPaidAmount() {
        return isDepositRequired() && isDepositPaid() ? depositAmount : BigDecimal.ZERO;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.accessToken == null) {
            this.accessToken = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        if (this.status == null) {
            this.status = BookingStatus.PENDING;
        }
    }
}
