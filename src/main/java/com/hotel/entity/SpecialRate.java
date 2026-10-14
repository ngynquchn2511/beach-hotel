package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Giai doan gia dac biet (le, Tet, mua cao diem): cac dem tu startDate den endDate (tinh ca 2 dau)
// duoc phu thu surchargePercent so voi gia ngay thuong. Uu tien hon phu thu cuoi tuan.
@Entity
@Table(name = "special_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Ten dip le tieng Anh (tuy chon) hien o trang xac nhan dat phong khi khach chon EN
    @Column(name = "name_en", length = 100)
    private String nameEn;

    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "surcharge_percent", nullable = false)
    private Integer surchargePercent;

    // Dem bat dau tu ngay "night" (o tu night den night + 1) co nam trong giai doan nay khong
    public boolean covers(LocalDate night) {
        return !night.isBefore(startDate) && !night.isAfter(endDate);
    }
}
