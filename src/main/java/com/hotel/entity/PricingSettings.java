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

    public static PricingSettings defaults() {
        return PricingSettings.builder()
                .id(SINGLETON_ID)
                .weekendSurchargePercent(0)
                .depositPercent(0)
                .depositDeadlineHours(24)
                .build();
    }
}
