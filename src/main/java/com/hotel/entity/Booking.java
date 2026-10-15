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

    // Coc tra online qua cong VNPay: ma giao dich phia VNPay (vnp_TransactionNo).
    // null = coc chuyen khoan thuong, nhan vien doi chieu roi xac nhan tay
    @Column(name = "deposit_online_ref", length = 40)
    private String depositOnlineRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // ===== Huy don & hoan coc =====
    // Thoi diem don chuyen sang CANCELLED (null voi don huy truoc khi co tinh nang nay)
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Ty le (%) va so tien coc phai hoan cho khach, chot luc huy theo chinh sach hoan coc.
    // null = don chua coc (khong co gi de hoan); 0 = khach mat coc
    @Column(name = "refund_percent")
    private Integer refundPercent;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    // Thoi diem nhan vien xac nhan da chuyen tra tien hoan cho khach (null = chua hoan)
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // Ghi chu khi hoan (VD: "CK lai VCB 0123..., ma GD FT123")
    @Column(name = "refund_note", length = 255)
    private String refundNote;

    // ===== Email tu dong (GuestEmailScheduler) - ghi thoi diem da gui de khong gui trung =====
    // Email nhac lich truoc ngay nhan phong
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    // Da gui email nhac chuyen coc truoc khi het han (null = chua)
    @Column(name = "deposit_reminder_sent_at")
    private LocalDateTime depositReminderSentAt;

    // Email moi danh gia sau khi tra phong
    @Column(name = "review_invite_sent_at")
    private LocalDateTime reviewInviteSentAt;

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

    // ===== Xuat hoa don cho cong ty (thong tin VAT) - null = hoa don ca nhan =====
    @Column(name = "invoice_company_name", length = 200)
    private String invoiceCompanyName;

    @Column(name = "invoice_tax_code", length = 20)
    private String invoiceTaxCode;

    @Column(name = "invoice_address", length = 255)
    private String invoiceAddress;

    @Column(name = "invoice_email", length = 100)
    private String invoiceEmail;

    // Ke toan da xuat hoa don dien tu (so hoa don + thoi diem)
    @Column(name = "e_invoice_number", length = 30)
    private String eInvoiceNumber;

    @Column(name = "e_invoice_issued_at")
    private LocalDateTime eInvoiceIssuedAt;

    public boolean isCompanyInvoice() {
        return invoiceTaxCode != null;
    }

    // The qua tang dung tru tien don (ma + so tien da tru); null = khong dung
    @Column(name = "gift_voucher_code", length = 20)
    private String giftVoucherCode;

    @Column(name = "gift_voucher_amount", precision = 12, scale = 2)
    private java.math.BigDecimal giftVoucherAmount;

    public java.math.BigDecimal getGiftVoucherAmountOrZero() {
        return giftVoucherAmount != null ? giftVoucherAmount : java.math.BigDecimal.ZERO;
    }

    // Thuoc nhom dat nhieu phong (null = don le)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private BookingGroup group;

    // ===== Le tan doi phong cho khach (phong hong, trung phong, nang hang...) =====
    @Column(name = "moved_from_room", length = 20)
    private String movedFromRoom;

    @Column(name = "room_moved_at")
    private LocalDateTime roomMovedAt;

    @Column(name = "room_move_note", length = 200)
    private String roomMoveNote;

    // ===== Yeu cau nhan som / tra muon (ngoai khung 14:00 - 12:00, co phu phi, le tan duyet) =====
    @Column(name = "requested_check_in_time")
    private java.time.LocalTime requestedCheckInTime;

    @Column(name = "requested_check_out_time")
    private java.time.LocalTime requestedCheckOutTime;

    @Column(name = "time_request_fee", precision = 12, scale = 2)
    private java.math.BigDecimal timeRequestFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_request_status", length = 10)
    private StayTimeRequestStatus timeRequestStatus;

    @Column(name = "time_requested_at")
    private LocalDateTime timeRequestedAt;

    // Ly do le tan tu choi (hien cho khach)
    @Column(name = "time_request_note", length = 200)
    private String timeRequestNote;

    public boolean isTimeRequestPending() {
        return timeRequestStatus == StayTimeRequestStatus.PENDING;
    }

    // Khuyen mai tu dong da ap luc dat (ten luu lai vi luat co the bi sua / xoa sau); null = khong co
    @Column(name = "promotion_name", length = 100)
    private String promotionName;

    @Column(name = "promotion_name_en", length = 100)
    private String promotionNameEn;

    @Column(name = "promotion_percent")
    private Integer promotionPercent;

    @Column(name = "promotion_amount", precision = 12, scale = 2)
    private java.math.BigDecimal promotionAmount;

    public java.math.BigDecimal getPromotionAmountOrZero() {
        return promotionAmount != null ? promotionAmount : java.math.BigDecimal.ZERO;
    }

    public String getLocalizedPromotionName() {
        return com.hotel.util.Texts.pick(promotionName, promotionNameEn);
    }

    // Diem thuong khach dung de tru tien luc dat (pointsDiscount = so tien da tru); null = khong dung
    @Column(name = "points_used")
    private Integer pointsUsed;

    @Column(name = "points_discount", precision = 12, scale = 2)
    private java.math.BigDecimal pointsDiscount;

    // Diem duoc cong khi tra phong (null = chua tra phong / khong tich diem)
    @Column(name = "points_earned")
    private Integer pointsEarned;

    public java.math.BigDecimal getPointsDiscountAmount() {
        return pointsDiscount != null ? pointsDiscount : java.math.BigDecimal.ZERO;
    }

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

    // Kenh dat: online (khach tu dat tren web) / tai quay (le tan dat ho). null = don cu, xem getEffectiveChannel
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private BookingChannel channel;

    // Danh dau khach vua tu doi ngay nhan/tra phong - hien canh bao rieng cho staff, tat khi staff mo xem chi tiet
    @Column(name = "dates_changed", nullable = false)
    @Builder.Default
    private boolean datesChanged = false;

    // Don cu chua ghi kenh: khach co email tam "@khachvanglai.local" (tai khoan le tan tao) -> tai quay, con lai online
    public BookingChannel getEffectiveChannel() {
        if (channel != null) {
            return channel;
        }
        String email = customer != null ? customer.getEmail() : null;
        return email != null && email.endsWith("@khachvanglai.local") ? BookingChannel.WALK_IN : BookingChannel.ONLINE;
    }

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

    // Ma don gui VNPay (vnp_TxnRef) va thoi diem thanh toan (vnp_PayDate, yyyyMMddHHmmss) - API hoan tien cua VNPay bat buoc
    @Column(name = "deposit_online_txn_ref", length = 40)
    private String depositOnlineTxnRef;

    @Column(name = "deposit_online_pay_date", length = 14)
    private String depositOnlinePayDate;

    // Ma giao dich hoan tien qua VNPay (null = hoan bang chuyen khoan tay hoac chua hoan)
    @Column(name = "refund_online_ref", length = 40)
    private String refundOnlineRef;

    // Hoan tu dong qua VNPay duoc khi: coc tra qua VNPay (co du thong tin giao dich) va dang cho hoan
    public boolean isRefundableViaVnpay() {
        return isRefundPending() && isDepositPaidOnline() && depositOnlineTxnRef != null && depositOnlinePayDate != null;
    }

    public boolean isDepositPaidOnline() {
        return depositPaidAt != null && depositOnlineRef != null;
    }

    // Dang cho khach chuyen coc: co yeu cau coc, chua nhan, don van dang cho xac nhan
    public boolean isAwaitingDeposit() {
        return isDepositRequired() && !isDepositPaid() && status == BookingStatus.PENDING;
    }

    // So tien coc da thu (tru vao luc thanh toan khi tra phong)
    public BigDecimal getDepositPaidAmount() {
        return isDepositRequired() && isDepositPaid() ? depositAmount : BigDecimal.ZERO;
    }

    // Don da huy, khach duoc hoan coc nhung nhan vien chua chuyen tra
    public boolean isRefundPending() {
        return status == BookingStatus.CANCELLED && refundAmount != null && refundAmount.signum() > 0 && refundedAt == null;
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    // Tien coc resort giu lai khi khach huy (coc da thu - tien hoan).
    // Don huy truoc khi co chinh sach hoan coc (refundAmount null) -> khong biet da xu ly the nao, tinh 0
    public BigDecimal getForfeitedDepositAmount() {
        if (status != BookingStatus.CANCELLED || !isDepositPaid() || !isDepositRequired() || refundAmount == null) {
            return BigDecimal.ZERO;
        }
        return depositAmount.subtract(refundAmount).max(BigDecimal.ZERO);
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
