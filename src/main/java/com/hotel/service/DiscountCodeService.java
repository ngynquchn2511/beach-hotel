package com.hotel.service;

import com.hotel.dto.DiscountCodeRequest;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.exception.BusinessException;
import com.hotel.repository.DiscountCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class DiscountCodeService {

    private final DiscountCodeRepository discountCodeRepository;

    public DiscountCodeService(DiscountCodeRepository discountCodeRepository) {
        this.discountCodeRepository = discountCodeRepository;
    }

    public List<DiscountCode> findAll() {
        return discountCodeRepository.findAll();
    }

    public DiscountCode findById(Long id) {
        return discountCodeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy mã giảm giá"));
    }

    @Transactional
    public DiscountCode create(DiscountCodeRequest request) {
        // So trung theo dung gia tri DA CHUAN HOA (uppercase) se duoc luu, neu khong "duptest" se lot qua
        // buoc kiem tra trung voi ma "DUPTEST" da ton tai, roi vo o rang buoc unique cua DB (loi 500 xau xi)
        if (discountCodeRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new BusinessException("Mã giảm giá này đã tồn tại");
        }
        DiscountCode discountCode = DiscountCode.builder()
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .descriptionEn(request.getDescriptionEn() == null || request.getDescriptionEn().isBlank() ? null : request.getDescriptionEn().trim())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .applicableCustomerType(request.getApplicableCustomerType())
                .active(true)
                .build();
        return discountCodeRepository.save(discountCode);
    }

    @Transactional
    public DiscountCode update(Long id, DiscountCodeRequest request) {
        DiscountCode discountCode = findById(id);
        if (!discountCode.getCode().equalsIgnoreCase(request.getCode())
                && discountCodeRepository.existsByCode(request.getCode().toUpperCase())) {
            throw new BusinessException("Mã giảm giá này đã tồn tại");
        }
        discountCode.setCode(request.getCode().toUpperCase());
        discountCode.setDescription(request.getDescription());
        discountCode.setDescriptionEn(request.getDescriptionEn() == null || request.getDescriptionEn().isBlank() ? null : request.getDescriptionEn().trim());
        discountCode.setDiscountType(request.getDiscountType());
        discountCode.setDiscountValue(request.getDiscountValue());
        discountCode.setApplicableCustomerType(request.getApplicableCustomerType());
        return discountCodeRepository.save(discountCode);
    }

    @Transactional
    public void toggleActive(Long id) {
        DiscountCode discountCode = findById(id);
        discountCode.setActive(!discountCode.isActive());
        discountCodeRepository.save(discountCode);
    }

    @Transactional
    public void delete(Long id) {
        discountCodeRepository.deleteById(id);
    }

    // Kiem tra ma co hop le voi loai khach hang hien tai khong, tra ve so tien duoc giam
    // Se duoc goi that su khi xay dung luong tao booking (Buoc 4)
    public BigDecimal validateAndCalculateDiscount(String code, CustomerType customerType, BigDecimal totalAmount) {
        // Chuan hoa giong BookingService.createBooking (trim + chu hoa) - neu khong, khach go "sale10" se bi bao
        // "khong hop le" o nut Kiem tra ma nhung van duoc giam khi bam Xac nhan dat phong
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        Optional<DiscountCode> discountOpt = discountCodeRepository.findByCodeAndActiveTrue(normalizedCode);
        if (discountOpt.isEmpty()) {
            throw BusinessException.of("err.discountInvalid");
        }
        DiscountCode discount = discountOpt.get();

        if (discount.getApplicableCustomerType() != null && discount.getApplicableCustomerType() != customerType) {
            throw BusinessException.of("err.discountCustomerType");
        }

        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            return totalAmount.multiply(discount.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));
        }
        return discount.getDiscountValue().min(totalAmount);
    }
}
