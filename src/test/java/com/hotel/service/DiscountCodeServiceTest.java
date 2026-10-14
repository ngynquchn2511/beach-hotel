package com.hotel.service;

import com.hotel.dto.DiscountCodeRequest;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.DiscountCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCodeServiceTest {

    @Mock private DiscountCodeRepository discountCodeRepository;

    @InjectMocks
    private DiscountCodeService discountCodeService;

    @Test
    void create_duplicateCode_throws() {
        DiscountCodeRequest req = new DiscountCodeRequest();
        req.setCode("SALE10");
        when(discountCodeRepository.existsByCode("SALE10")).thenReturn(true);

        assertThrows(BusinessException.class, () -> discountCodeService.create(req));
        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    void create_duplicateCodeDifferentCase_stillDetectedAsDuplicate() {
        // "duptest" (chua chuan hoa) phai duoc nhan dien trung voi "DUPTEST" da ton tai trong DB,
        // khong duoc de lot qua roi vo loi rang buoc unique khi save
        DiscountCodeRequest req = new DiscountCodeRequest();
        req.setCode("duptest");
        when(discountCodeRepository.existsByCode("DUPTEST")).thenReturn(true);

        assertThrows(BusinessException.class, () -> discountCodeService.create(req));
        verify(discountCodeRepository, never()).save(any());
    }

    @Test
    void create_success_upperCasesCodeAndSetsActiveTrue() {
        DiscountCodeRequest req = new DiscountCodeRequest();
        req.setCode("sale10");
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setDiscountValue(BigDecimal.TEN);
        when(discountCodeRepository.existsByCode("SALE10")).thenReturn(false);
        when(discountCodeRepository.save(any(DiscountCode.class))).thenAnswer(inv -> inv.getArgument(0));

        DiscountCode result = discountCodeService.create(req);

        assertEquals("SALE10", result.getCode());
        assertTrue(result.isActive());
    }

    @Test
    void validateAndCalculateDiscount_codeNotFound_throws() {
        when(discountCodeRepository.findByCodeAndActiveTrue("NOPE")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class,
                () -> discountCodeService.validateAndCalculateDiscount("NOPE", CustomerType.NEW, BigDecimal.valueOf(100000)));
    }

    @Test
    void validateAndCalculateDiscount_wrongCustomerType_throws() {
        DiscountCode code = DiscountCode.builder().code("VIPONLY").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).applicableCustomerType(CustomerType.VIP).active(true).build();
        when(discountCodeRepository.findByCodeAndActiveTrue("VIPONLY")).thenReturn(Optional.of(code));

        assertThrows(BusinessException.class,
                () -> discountCodeService.validateAndCalculateDiscount("VIPONLY", CustomerType.NEW, BigDecimal.valueOf(100000)));
    }

    @Test
    void validateAndCalculateDiscount_nullApplicableType_acceptsAnyCustomerType() {
        DiscountCode code = DiscountCode.builder().code("ALL10").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).applicableCustomerType(null).active(true).build();
        when(discountCodeRepository.findByCodeAndActiveTrue("ALL10")).thenReturn(Optional.of(code));

        BigDecimal result = discountCodeService.validateAndCalculateDiscount("ALL10", CustomerType.VIP, BigDecimal.valueOf(200000));

        assertEquals(0, BigDecimal.valueOf(20000).compareTo(result));
    }

    @Test
    void validateAndCalculateDiscount_fixedAmountCappedAtTotal() {
        DiscountCode code = DiscountCode.builder().code("FIX").discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(500000)).applicableCustomerType(null).active(true).build();
        when(discountCodeRepository.findByCodeAndActiveTrue("FIX")).thenReturn(Optional.of(code));

        BigDecimal result = discountCodeService.validateAndCalculateDiscount("FIX", CustomerType.NEW, BigDecimal.valueOf(200000));

        assertEquals(0, BigDecimal.valueOf(200000).compareTo(result));
    }

    @Test
    void toggleActive_flipsCurrentValue() {
        DiscountCode code = DiscountCode.builder().id(1L).code("X").active(true).build();
        when(discountCodeRepository.findById(1L)).thenReturn(Optional.of(code));
        when(discountCodeRepository.save(any(DiscountCode.class))).thenAnswer(inv -> inv.getArgument(0));

        discountCodeService.toggleActive(1L);

        assertFalse(code.isActive());
    }
}
