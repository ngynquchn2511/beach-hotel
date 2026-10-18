package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "discount_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    // Mo ta tieng Anh (tuy chon) - chatbot tra loi tieng Anh dung ban nay, bo trong thi dung ban tieng Viet
    @Column(name = "description_en", length = 500)
    private String descriptionEn;

    public String getLocalizedDescription() {
        return Texts.pick(description, descriptionEn);
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    // Neu discountType = PERCENTAGE thi day la % (VD: 10 = giam 10%)
    // Neu discountType = FIXED_AMOUNT thi day la so tien VND giam truc tiep
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    // Ap dung cho loai khach hang nao - de trong (null) nghia la ap dung cho tat ca
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_customer_type", length = 20)
    private CustomerType applicableCustomerType;

    @Column(nullable = false)
    private boolean active;

    // ===== Gioi han dung ma (null = khong gioi han) =====
    // Hieu luc theo ngay khach dat (khong phai ngay nhan phong)
    @Column(name = "valid_from")
    private java.time.LocalDate validFrom;

    @Column(name = "valid_to")
    private java.time.LocalDate validTo;

    // Tong so luot dung toi da (don le tinh 1, ca doan tinh 1; don da huy khong tinh)
    @Column(name = "max_uses")
    private Integer maxUses;

    // Tien don (phong + combo) toi thieu de dung ma
    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    // Ma %: so tien giam toi da
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    // So tien giam cho don co tien (phong + combo) = subtotal, da ap tran giam toi da
    public BigDecimal computeDiscount(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (discountType == DiscountType.PERCENTAGE) {
            BigDecimal amount = subtotal.multiply(discountValue).divide(BigDecimal.valueOf(100));
            return maxDiscountAmount != null && maxDiscountAmount.signum() > 0 ? amount.min(maxDiscountAmount) : amount;
        }
        return discountValue.min(subtotal);
    }

    public boolean isExpired(java.time.LocalDate today) {
        return validTo != null && today.isAfter(validTo);
    }

    public boolean isNotStarted(java.time.LocalDate today) {
        return validFrom != null && today.isBefore(validFrom);
    }
}
