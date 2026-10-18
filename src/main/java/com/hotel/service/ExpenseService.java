package com.hotel.service;

import com.hotel.entity.ChargeType;
import com.hotel.entity.Expense;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.ServiceCostRate;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ExpenseRepository;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.ServiceCostRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Chi phi van hanh (ke toan nhap) + gia von dich vu theo % + phi cong thanh toan + chi phi bien doi moi phong-dem
@Service
public class ExpenseService {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000000");
    public static final BigDecimal MAX_PAYMENT_FEE_PERCENT = new BigDecimal("10");

    // Muc goi y (tham khao nganh khach san) - hien tren form, khong tu ap
    public static final Map<ChargeType, Integer> SUGGESTED_COST_PERCENT = Map.of(
            ChargeType.ROOM_SERVICE, 35, ChargeType.MINIBAR, 50, ChargeType.LAUNDRY, 30, ChargeType.SPA, 40,
            ChargeType.DIVING_TOUR, 60, ChargeType.AIRPORT_TRANSFER, 70);

    private final ExpenseRepository repository;
    private final ServiceCostRateRepository costRateRepository;
    private final PricingService pricingService;
    private final PricingSettingsRepository settingsRepository;

    public ExpenseService(ExpenseRepository repository, ServiceCostRateRepository costRateRepository, PricingService pricingService,
                          PricingSettingsRepository settingsRepository) {
        this.repository = repository;
        this.costRateRepository = costRateRepository;
        this.pricingService = pricingService;
        this.settingsRepository = settingsRepository;
    }

    // Cac khoan chi tinh vao thang m
    public List<Expense> forMonth(YearMonth m) {
        LocalDate first = m.atDay(1);
        return repository.findTouching(first, m.atEndOfMonth(), first).stream().filter(e -> e.appliesTo(m)).toList();
    }

    public Map<Expense.Category, BigDecimal> totalsByCategory(YearMonth m) {
        Map<Expense.Category, BigDecimal> totals = new EnumMap<>(Expense.Category.class);
        for (Expense e : forMonth(m)) {
            totals.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }
        return totals;
    }

    @Transactional
    public Expense create(Expense.Category category, String description, BigDecimal amount, Expense.Recurrence recurrence,
                          LocalDate expenseDate, YearMonth startMonth, YearMonth endMonth, String createdBy) {
        if (category == null || recurrence == null) {
            throw new BusinessException("Vui lòng chọn nhóm chi phí và loại khoản chi");
        }
        String text = description == null ? "" : description.trim();
        if (text.isEmpty() || text.length() > 200) {
            throw new BusinessException("Vui lòng nhập nội dung khoản chi (tối đa 200 ký tự)");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException("Số tiền phải lớn hơn 0");
        }
        Expense.ExpenseBuilder b = Expense.builder().category(category).description(text)
                .amount(amount.setScale(0, RoundingMode.HALF_UP)).recurrence(recurrence).createdBy(createdBy);
        if (recurrence == Expense.Recurrence.ONE_OFF) {
            if (expenseDate == null) {
                throw new BusinessException("Vui lòng chọn ngày phát sinh");
            }
            b.expenseDate(expenseDate);
        } else {
            if (startMonth == null) {
                throw new BusinessException("Vui lòng chọn tháng bắt đầu áp dụng");
            }
            if (endMonth != null && endMonth.isBefore(startMonth)) {
                throw new BusinessException("Tháng kết thúc phải từ tháng bắt đầu trở đi");
            }
            b.startMonth(startMonth.atDay(1)).endMonth(endMonth != null ? endMonth.atDay(1) : null);
        }
        return repository.save(b.build());
    }

    // Ngung khoan chi hang thang tu thang sau thang `lastMonth` (giu lich su cac thang truoc)
    @Transactional
    public void stopMonthly(Long id, YearMonth lastMonth) {
        Expense e = repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy khoản chi"));
        if (!e.isMonthly()) {
            throw new BusinessException("Chỉ dừng được khoản chi hàng tháng");
        }
        if (lastMonth.isBefore(YearMonth.from(e.getStartMonth()))) {
            repository.delete(e);
            return;
        }
        e.setEndMonth(lastMonth.atDay(1));
        repository.save(e);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy khoản chi")));
    }

    // ===== Gia von / phi =====

    public Map<ChargeType, Integer> costRates() {
        Map<ChargeType, Integer> map = new EnumMap<>(ChargeType.class);
        for (ServiceCostRate r : costRateRepository.findAll()) {
            map.put(r.getChargeType(), r.getPercent());
        }
        return map;
    }

    public int costPercent(ChargeType type) {
        return costRateRepository.findById(type).map(ServiceCostRate::getPercent).orElse(0);
    }

    @Transactional
    public void updateCostSettings(Map<ChargeType, Integer> percents, BigDecimal paymentFeePercent, BigDecimal variableCostPerNight) {
        for (Map.Entry<ChargeType, Integer> e : percents.entrySet()) {
            Integer p = e.getValue();
            if (p == null || p == 0) {
                costRateRepository.deleteById(e.getKey());
                continue;
            }
            if (p < 0 || p > 100) {
                throw new BusinessException("Tỷ lệ giá vốn từ 0 đến 100%");
            }
            costRateRepository.save(ServiceCostRate.builder().chargeType(e.getKey()).percent(p).build());
        }
        if (paymentFeePercent != null && (paymentFeePercent.signum() < 0 || paymentFeePercent.compareTo(MAX_PAYMENT_FEE_PERCENT) > 0)) {
            throw new BusinessException("Phí cổng thanh toán từ 0 đến 10%");
        }
        if (variableCostPerNight != null && variableCostPerNight.signum() < 0) {
            throw new BusinessException("Chi phí mỗi phòng-đêm không được âm");
        }
        PricingSettings s = pricingService.getSettings();
        s.setPaymentFeePercent(paymentFeePercent != null && paymentFeePercent.signum() > 0 ? paymentFeePercent : null);
        s.setVariableCostPerNight(variableCostPerNight != null && variableCostPerNight.signum() > 0
                ? variableCostPerNight.setScale(0, RoundingMode.HALF_UP) : null);
        settingsRepository.save(s);
    }

    public BigDecimal paymentFeePercent() {
        BigDecimal p = pricingService.getSettings().getPaymentFeePercent();
        return p == null ? BigDecimal.ZERO : p;
    }

    public BigDecimal variableCostPerNight() {
        BigDecimal v = pricingService.getSettings().getVariableCostPerNight();
        return v == null ? BigDecimal.ZERO : v;
    }
}
