package com.hotel.service;

import com.hotel.entity.PromotionRule;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PromotionRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// Khuyen mai tu dong theo luat (o dai ngay / dat som / gio chot) - ap tren tien phong, chi 1 luat co loi nhat
@Service
public class PromotionService {

    public static final int MAX_PERCENT = 50;
    public static final int MAX_THRESHOLD = 365;

    private final PromotionRuleRepository repository;

    public PromotionService(PromotionRuleRepository repository) {
        this.repository = repository;
    }

    // Khuyen mai duoc ap: ten luc ap (luat co the bi sua / xoa sau), % va so tien giam
    public record Applied(String name, String nameEn, int percent, BigDecimal amount) {
    }

    public List<PromotionRule> findAll() {
        return repository.findAllByOrderByActiveDescDiscountPercentDesc();
    }

    public List<PromotionRule> findActive() {
        return repository.findByActiveTrueOrderByDiscountPercentDesc();
    }

    public static boolean qualifies(PromotionRule rule, LocalDate checkIn, LocalDate checkOut, LocalDate bookedOn) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        long daysAhead = ChronoUnit.DAYS.between(bookedOn, checkIn);
        return switch (rule.getType()) {
            case LONG_STAY -> nights >= rule.getThreshold();
            case EARLY_BIRD -> daysAhead >= rule.getThreshold();
            case LAST_MINUTE -> daysAhead >= 0 && daysAhead <= rule.getThreshold();
        };
    }

    // Luat giam nhieu nhat ma don thoa (bookedOn = ngay khach dat)
    @Transactional(readOnly = true)
    public Optional<Applied> best(BigDecimal roomAmount, LocalDate checkIn, LocalDate checkOut, LocalDate bookedOn) {
        if (roomAmount == null || roomAmount.signum() <= 0 || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return Optional.empty();
        }
        return findActive().stream()
                .filter(r -> qualifies(r, checkIn, checkOut, bookedOn))
                .findFirst()
                .map(r -> new Applied(r.getName(), r.getNameEn(), r.getDiscountPercent(),
                        roomAmount.multiply(BigDecimal.valueOf(r.getDiscountPercent()))
                                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)));
    }

    @Transactional
    public PromotionRule create(String name, String nameEn, PromotionRule.Type type, Integer threshold, Integer percent) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty() || text.length() > 100) {
            throw new BusinessException("Vui lòng nhập tên khuyến mãi (tối đa 100 ký tự)");
        }
        if (type == null) {
            throw new BusinessException("Vui lòng chọn loại khuyến mãi");
        }
        int min = type == PromotionRule.Type.LAST_MINUTE ? 0 : 1;
        if (threshold == null || threshold < min || threshold > MAX_THRESHOLD) {
            throw new BusinessException("Điều kiện (số đêm / số ngày) phải từ " + min + " đến " + MAX_THRESHOLD);
        }
        if (percent == null || percent < 1 || percent > MAX_PERCENT) {
            throw new BusinessException("Mức giảm phải từ 1% đến " + MAX_PERCENT + "%");
        }
        String en = nameEn == null || nameEn.isBlank() ? null : nameEn.trim();
        return repository.save(PromotionRule.builder().name(text).nameEn(en).type(type)
                .threshold(threshold).discountPercent(percent).active(true).build());
    }

    @Transactional
    public void toggle(Long id) {
        PromotionRule rule = repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy khuyến mãi"));
        rule.setActive(!Boolean.TRUE.equals(rule.getActive()));
        repository.save(rule);
    }

    // Don da dat giu nguyen khuyen mai da ap (ten + so tien luu tren don)
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
