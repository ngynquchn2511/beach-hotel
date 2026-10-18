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

    // Dem luot dung (unit test khoi tao bang constructor nen co the chua co - khi do bo qua gioi han luot)
    private com.hotel.repository.BookingRepository bookingRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setBookingRepository(com.hotel.repository.BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public long usageCount(DiscountCode dc) {
        if (bookingRepository == null || dc.getId() == null) {
            return 0;
        }
        return bookingRepository.countSingleUses(dc.getId()) + bookingRepository.countGroupUses(dc.getId());
    }

    // Kiem tra thoi han / so luot / gia tri don toi thieu (khong kiem tra loai khach, ma da dung - noi goi tu lo)
    public void checkUsable(DiscountCode dc, BigDecimal subtotal, java.time.LocalDate today) {
        if (dc.isNotStarted(today)) {
            throw BusinessException.of("err.discountNotYet", dc.getValidFrom().format(DAY));
        }
        if (dc.isExpired(today)) {
            throw BusinessException.of("err.discountExpired", dc.getValidTo().format(DAY));
        }
        if (dc.getMaxUses() != null && usageCount(dc) >= dc.getMaxUses()) {
            throw BusinessException.of("err.discountExhausted");
        }
        if (dc.getMinOrderAmount() != null && subtotal != null && subtotal.compareTo(dc.getMinOrderAmount()) < 0) {
            throw BusinessException.of("err.discountMinOrder", com.hotel.util.Money.format(dc.getMinOrderAmount()));
        }
    }

    // Ma dang bat, trong thoi han va con luot (chatbot / gioi thieu cho khach)
    public boolean isAvailableNow(DiscountCode dc) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return dc.isActive() && !dc.isNotStarted(today) && !dc.isExpired(today)
                && (dc.getMaxUses() == null || usageCount(dc) < dc.getMaxUses());
    }

    // Thong ke 1 ma cho admin
    public record UsageStats(DiscountCode code, long uses, BigDecimal discountTotal, BigDecimal revenue, List<com.hotel.entity.Booking> bookings) {
    }

    public UsageStats stats(Long id) {
        DiscountCode dc = findById(id);
        List<com.hotel.entity.Booking> bookings = bookingRepository == null ? List.of() : bookingRepository.findByDiscountCodeIdForStats(id);
        List<com.hotel.entity.Booking> counted = bookings.stream()
                .filter(b -> b.getStatus() != com.hotel.entity.BookingStatus.CANCELLED).toList();
        return new UsageStats(dc, usageCount(dc),
                counted.stream().map(b -> b.getDiscountAmount() != null ? b.getDiscountAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add),
                counted.stream().map(com.hotel.entity.Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add), bookings);
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static void applyLimits(DiscountCode dc, DiscountCodeRequest request) {
        dc.setValidFrom(request.getValidFrom());
        dc.setValidTo(request.getValidTo());
        dc.setMaxUses(request.getMaxUses());
        dc.setMinOrderAmount(request.getMinOrderAmount() != null && request.getMinOrderAmount().signum() > 0 ? request.getMinOrderAmount() : null);
        // Tran giam chi co nghia voi ma %
        dc.setMaxDiscountAmount(request.getDiscountType() == DiscountType.PERCENTAGE ? request.getMaxDiscountAmount() : null);
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
        applyLimits(discountCode, request);
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
        applyLimits(discountCode, request);
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
        checkUsable(discount, totalAmount, java.time.LocalDate.now());
        return discount.computeDiscount(totalAmount);
    }
}
