package com.hotel.dto;

import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DiscountCodeRequest {

    @NotBlank(message = "Mã giảm giá không được để trống")
    private String code;

    private String description;

    // Ban tieng Anh (tuy chon)
    private String descriptionEn;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    // De trong = ap dung cho tat ca loai khach
    private CustomerType applicableCustomerType;

    // Gioi han (de trong = khong gioi han)
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate validFrom;

    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate validTo;

    @jakarta.validation.constraints.Min(value = 1, message = "Số lượt tối đa phải từ 1 trở lên")
    private Integer maxUses;

    @DecimalMin(value = "0.0", message = "Giá trị đơn tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", inclusive = false, message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @AssertTrue(message = "Ngày kết thúc phải từ ngày bắt đầu trở đi")
    public boolean isValidityRangeValid() {
        return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    // BR: ma giam gia theo PHAN TRAM khong duoc vuot qua 100%, neu khong se luu duoc gia tri vo nghia
    // (vd. "giam 150%") - FIXED_AMOUNT khong bi rang buoc nay vi don vi la VND, khong phai %.
    @AssertTrue(message = "Giá trị giảm theo phần trăm không được vượt quá 100")
    public boolean isDiscountValueValid() {
        if (discountType == DiscountType.PERCENTAGE && discountValue != null) {
            return discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
        }
        return true;
    }
}
