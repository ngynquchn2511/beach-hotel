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
}
