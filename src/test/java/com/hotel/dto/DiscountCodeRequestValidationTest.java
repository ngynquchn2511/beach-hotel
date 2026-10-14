package com.hotel.dto;

import com.hotel.entity.DiscountType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DiscountCodeRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void blankCode_isRejected() {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("");
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.TEN);
        Set<ConstraintViolation<DiscountCodeRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("code")));
    }

    @Test
    void negativeOrZeroDiscountValue_isRejected() {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("SALE10");
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.ZERO);
        Set<ConstraintViolation<DiscountCodeRequest>> violations = validator.validate(r);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discountValue")));
    }

    // BR: ma giam gia theo PHAN TRAM khong duoc vuot qua 100% - neu khong he thong co the luu mot ma
    // "giam 150%" khien tien hang luon bi am ve 0 (BookingService da chan am o tang tinh tien, nhung du lieu
    // luu vao CSDL van la mot gia tri vo nghia / sai nghiep vu). Can rang buoc chan tu luc nhap lieu.
    @Test
    void percentageDiscountOver100_isRejected() {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("SUPERSALE");
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.valueOf(150));
        Set<ConstraintViolation<DiscountCodeRequest>> violations = validator.validate(r);
        assertFalse(violations.isEmpty(), "Mã giảm giá PERCENTAGE > 100 phải bị từ chối ở tầng validate");
    }

    @Test
    void percentageDiscountExactly100_isAccepted() {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("FREE100");
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.valueOf(100));
        Set<ConstraintViolation<DiscountCodeRequest>> violations = validator.validate(r);
        assertTrue(violations.isEmpty());
    }

    @Test
    void fixedAmountOver100_isStillAccepted() {
        // FIXED_AMOUNT khong bi gioi han boi tran 100 (do la gioi han rieng cua PERCENTAGE)
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("FIX200K");
        r.setDiscountType(DiscountType.FIXED_AMOUNT);
        r.setDiscountValue(BigDecimal.valueOf(200000));
        Set<ConstraintViolation<DiscountCodeRequest>> violations = validator.validate(r);
        assertTrue(violations.isEmpty());
    }
}
