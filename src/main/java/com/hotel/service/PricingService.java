package com.hotel.service;

import com.hotel.entity.PricingSettings;
import com.hotel.entity.SpecialRate;
import com.hotel.exception.BusinessException;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.SpecialRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Tinh gia phong theo tung dem: ngay thuong = gia phong, dem thu 6/thu 7 + phu thu cuoi tuan,
// dem nam trong giai doan le/Tet + phu thu rieng (uu tien hon cuoi tuan). Kem tinh tien coc.
@Service
public class PricingService {

    public static final int MAX_SURCHARGE_PERCENT = 300;
    public static final int MAX_DEPOSIT_DEADLINE_HOURS = 168;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final PricingSettingsRepository settingsRepository;
    private final SpecialRateRepository specialRateRepository;

    public PricingService(PricingSettingsRepository settingsRepository, SpecialRateRepository specialRateRepository) {
        this.settingsRepository = settingsRepository;
        this.specialRateRepository = specialRateRepository;
    }

    // Gia 1 dem: night = ngay bat dau dem (o tu night den night + 1), label de hien thi cho khach (tieng Viet).
    // specialName (theo ngon ngu khach chon) + surchargePercent de giao dien song ngu tu ghep nhan
    // (specialName = null: cuoi tuan / ngay thuong)
    public record NightPrice(LocalDate night, BigDecimal price, String label, String specialName, int surchargePercent) {

        // "Thứ 6", "Chủ nhật"... - tu viet de khong phu thuoc locale cua may chu
        public String dayName() {
            DayOfWeek day = night.getDayOfWeek();
            return day == DayOfWeek.SUNDAY ? "Chủ nhật" : "Thứ " + (day.getValue() + 1);
        }
    }

    public PricingSettings getSettings() {
        return settingsRepository.findById(PricingSettings.SINGLETON_ID).orElseGet(PricingSettings::defaults);
    }

    public List<NightPrice> nightlyPrices(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut) {
        List<NightPrice> nights = new ArrayList<>();
        if (basePrice == null || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return nights;
        }
        int weekendPercent = getSettings().getWeekendSurchargePercent();
        List<SpecialRate> rates = specialRateRepository.findOverlapping(checkIn, checkOut.minusDays(1));

        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            SpecialRate special = null;
            for (SpecialRate rate : rates) {
                if (rate.covers(night) && (special == null || rate.getSurchargePercent() > special.getSurchargePercent())) {
                    special = rate;
                }
            }
            int percent;
            String label;
            if (special != null) {
                percent = special.getSurchargePercent();
                label = special.getName();
            } else if (isWeekendNight(night) && weekendPercent > 0) {
                percent = weekendPercent;
                label = "Cuối tuần";
            } else {
                percent = 0;
                label = "Ngày thường";
            }
            if (percent > 0) {
                label += " (+" + percent + "%)";
            }
            nights.add(new NightPrice(night, applySurcharge(basePrice, percent), label,
                    special != null ? special.getLocalizedName() : null, percent));
        }
        return nights;
    }

    public BigDecimal roomAmount(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut) {
        return nightlyPrices(basePrice, checkIn, checkOut).stream()
                .map(NightPrice::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Tien coc = tong tien x ty le coc, lam tron LEN hang nghin cho de chuyen khoan; khong vuot qua tong tien
    public BigDecimal depositFor(BigDecimal total) {
        int percent = getSettings().getDepositPercent();
        if (total == null || total.signum() <= 0 || percent <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal deposit = total.multiply(BigDecimal.valueOf(percent))
                .divide(HUNDRED.multiply(THOUSAND), 0, RoundingMode.CEILING)
                .multiply(THOUSAND);
        return deposit.min(total.setScale(0, RoundingMode.HALF_UP));
    }

    // Dem thu 6 va dem thu 7 (khach o qua dem sang thu 7 / chu nhat)
    public static boolean isWeekendNight(LocalDate night) {
        DayOfWeek day = night.getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
    }

    private static BigDecimal applySurcharge(BigDecimal basePrice, int percent) {
        if (percent == 0) {
            return basePrice;
        }
        return basePrice.multiply(BigDecimal.valueOf(100L + percent)).divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    // ===== Quan tri (chi ADMIN) =====

    @Transactional
    public PricingSettings updateSettings(Integer weekendSurchargePercent, Integer depositPercent, Integer depositDeadlineHours) {
        if (weekendSurchargePercent == null || weekendSurchargePercent < 0 || weekendSurchargePercent > MAX_SURCHARGE_PERCENT) {
            throw new BusinessException("Phụ thu cuối tuần phải từ 0 đến " + MAX_SURCHARGE_PERCENT + "%");
        }
        if (depositPercent == null || depositPercent < 0 || depositPercent > 100) {
            throw new BusinessException("Tỷ lệ đặt cọc phải từ 0 đến 100%");
        }
        if (depositDeadlineHours == null || depositDeadlineHours < 1 || depositDeadlineHours > MAX_DEPOSIT_DEADLINE_HOURS) {
            throw new BusinessException("Hạn chuyển cọc phải từ 1 đến " + MAX_DEPOSIT_DEADLINE_HOURS + " giờ");
        }
        PricingSettings settings = settingsRepository.findById(PricingSettings.SINGLETON_ID)
                .orElseGet(PricingSettings::defaults);
        settings.setWeekendSurchargePercent(weekendSurchargePercent);
        settings.setDepositPercent(depositPercent);
        settings.setDepositDeadlineHours(depositDeadlineHours);
        return settingsRepository.save(settings);
    }

    public List<SpecialRate> findAllSpecialRates() {
        return specialRateRepository.findAllByOrderByStartDateDesc();
    }

    @Transactional
    public SpecialRate createSpecialRate(String name, LocalDate startDate, LocalDate endDate, Integer surchargePercent) {
        return createSpecialRate(name, null, startDate, endDate, surchargePercent);
    }

    // nameEn: ten tieng Anh tuy chon (bo trong thi khach tieng Anh thay ten tieng Viet)
    @Transactional
    public SpecialRate createSpecialRate(String name, String nameEn, LocalDate startDate, LocalDate endDate,
                                         Integer surchargePercent) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new BusinessException("Tên dịp lễ không được để trống và tối đa 100 ký tự");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException("Vui lòng chọn ngày bắt đầu và ngày kết thúc");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu");
        }
        if (surchargePercent == null || surchargePercent < 1 || surchargePercent > MAX_SURCHARGE_PERCENT) {
            throw new BusinessException("Phụ thu ngày lễ phải từ 1 đến " + MAX_SURCHARGE_PERCENT + "%");
        }
        return specialRateRepository.save(SpecialRate.builder()
                .name(trimmed)
                .nameEn(nameEn == null || nameEn.isBlank() ? null : nameEn.trim())
                .startDate(startDate)
                .endDate(endDate)
                .surchargePercent(surchargePercent)
                .build());
    }

    @Transactional
    public void deleteSpecialRate(Long id) {
        SpecialRate rate = specialRateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giai đoạn giá"));
        specialRateRepository.delete(rate);
    }
}
