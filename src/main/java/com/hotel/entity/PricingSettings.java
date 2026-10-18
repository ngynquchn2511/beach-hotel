package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cai dat gia & dat coc dung chung cho ca resort - chi co 1 dong (id = 1), admin sua o trang "Giá & đặt cọc".
// Chua co dong nao thi dung gia tri mac dinh (khong phu thu, khong dat coc) -> he thong chay nhu truoc.
@Entity
@Table(name = "pricing_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    // Phu thu (%) cho dem thu 6 va dem thu 7 so voi gia ngay thuong cua phong
    @Column(name = "weekend_surcharge_percent", nullable = false)
    private Integer weekendSurchargePercent;

    // Ty le dat coc (%) tren tong tien don khi khach dat online. 0 = khong yeu cau coc
    @Column(name = "deposit_percent", nullable = false)
    private Integer depositPercent;

    // So gio khach phai chuyen coc ke tu luc dat, qua han ma chua coc thi don tu huy
    @Column(name = "deposit_deadline_hours", nullable = false)
    private Integer depositDeadlineHours;

    // Chinh sach hoan coc khi khach huy (tinh theo so gio con lai truoc gio nhan phong):
    //   con >= refundFullHours        -> hoan 100% coc
    //   con >= refundPartialHours     -> hoan refundPartialPercent % coc
    //   con lai                       -> khong hoan
    // Cot cho phep null (dong cai dat tao truoc khi co tinh nang nay) -> dung gia tri mac dinh ben duoi
    public static final int DEFAULT_REFUND_FULL_HOURS = 72;
    public static final int DEFAULT_REFUND_PARTIAL_HOURS = 24;
    public static final int DEFAULT_REFUND_PARTIAL_PERCENT = 50;

    @Column(name = "refund_full_hours")
    private Integer refundFullHours;

    @Column(name = "refund_partial_hours")
    private Integer refundPartialHours;

    @Column(name = "refund_partial_percent")
    private Integer refundPartialPercent;

    // Ma bi mat trong link lich iCal (.ics) cua phong - Google Calendar / Booking.com doc link nay khong can dang nhap.
    // null = chua tao (tu tao khi admin mo trang "Dong bo lich"); admin doi ma neu lo link
    @Column(name = "ical_token", length = 40)
    private String icalToken;

    // Ty gia hien gia quy doi USD cho khach xem trang tieng Anh (VND / 1 USD). null = mac dinh
    public static final int DEFAULT_USD_RATE = 25_400;

    @Column(name = "usd_rate")
    private Integer usdRate;

    // Khach dat online truoc toi da bao nhieu ngay (null = khong gioi han)
    @Column(name = "max_advance_days")
    private Integer maxAdvanceDays;

    public int getEffectiveUsdRate() {
        return usdRate != null && usdRate > 0 ? usdRate : DEFAULT_USD_RATE;
    }

    public Integer getRefundFullHours() {
        return refundFullHours != null ? refundFullHours : DEFAULT_REFUND_FULL_HOURS;
    }

    public Integer getRefundPartialHours() {
        return refundPartialHours != null ? refundPartialHours : DEFAULT_REFUND_PARTIAL_HOURS;
    }

    public Integer getRefundPartialPercent() {
        return refundPartialPercent != null ? refundPartialPercent : DEFAULT_REFUND_PARTIAL_PERCENT;
    }

    // Gia thanh vien: khach dang nhap khi dat online duoc giam them % tien phong theo hang (null = 0, tat)
    @Column(name = "member_discount_new")
    private Integer memberDiscountNew;

    @Column(name = "member_discount_regular")
    private Integer memberDiscountRegular;

    @Column(name = "member_discount_vip")
    private Integer memberDiscountVip;

    public int memberDiscountFor(CustomerType type) {
        Integer pct = switch (type == null ? CustomerType.NEW : type) {
            case NEW -> memberDiscountNew;
            case REGULAR -> memberDiscountRegular;
            case VIP -> memberDiscountVip;
        };
        return pct == null ? 0 : pct;
    }

    // Muc cao nhat (de moi khach vang lai dang nhap / dang ky)
    public int getMaxMemberDiscount() {
        return Math.max(memberDiscountFor(CustomerType.NEW), Math.max(memberDiscountFor(CustomerType.REGULAR),
                memberDiscountFor(CustomerType.VIP)));
    }

    public static PricingSettings defaults() {
        return PricingSettings.builder()
                .id(SINGLETON_ID)
                .weekendSurchargePercent(0)
                .depositPercent(0)
                .depositDeadlineHours(24)
                .refundFullHours(DEFAULT_REFUND_FULL_HOURS)
                .refundPartialHours(DEFAULT_REFUND_PARTIAL_HOURS)
                .refundPartialPercent(DEFAULT_REFUND_PARTIAL_PERCENT)
                .build();
    }
}
