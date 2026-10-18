package com.hotel.service;

import com.hotel.entity.MonthlyBudget;
import com.hotel.exception.BusinessException;
import com.hotel.repository.MonthlyBudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Ke hoach thang + so sanh thuc te / ke hoach. Thang hien tai / tuong lai: doanh thu phong la so "da co tren so"
// (don da xac nhan ca cac dem chua den), nen % dat la tien do, chua phai ket qua cuoi thang
@Service
public class BudgetService {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000000");

    private final MonthlyBudgetRepository repository;

    public BudgetService(MonthlyBudgetRepository repository) {
        this.repository = repository;
    }

    public Optional<MonthlyBudget> of(YearMonth m) {
        return repository.findById(m.atDay(1)).filter(b -> !b.isEmpty());
    }

    // 12 thang cua nam (thang chua nhap -> ban ghi rong de hien form)
    public List<MonthlyBudget> year(int year) {
        Map<LocalDate, MonthlyBudget> saved = new LinkedHashMap<>();
        repository.findAllById(months(year)).forEach(b -> saved.put(b.getMonth(), b));
        List<MonthlyBudget> list = new ArrayList<>();
        for (LocalDate d : months(year)) {
            list.add(saved.getOrDefault(d, MonthlyBudget.builder().month(d).build()));
        }
        return list;
    }

    private static List<LocalDate> months(int year) {
        List<LocalDate> list = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            list.add(LocalDate.of(year, m, 1));
        }
        return list;
    }

    @Transactional
    public void save(YearMonth m, BigDecimal revenue, BigDecimal cost, Integer occupancy, BigDecimal adr) {
        check(revenue, "Doanh thu kế hoạch");
        check(cost, "Chi phí kế hoạch");
        check(adr, "ADR kế hoạch");
        if (occupancy != null && (occupancy < 0 || occupancy > 100)) {
            throw new BusinessException("Công suất kế hoạch tháng " + m.getMonthValue() + " phải từ 0 đến 100%");
        }
        MonthlyBudget b = repository.findById(m.atDay(1)).orElse(MonthlyBudget.builder().month(m.atDay(1)).build());
        b.setRevenueTarget(nz(revenue));
        b.setCostBudget(nz(cost));
        b.setOccupancyTarget(occupancy == null || occupancy == 0 ? null : occupancy);
        b.setAdrTarget(nz(adr));
        if (b.isEmpty()) {
            repository.findById(m.atDay(1)).ifPresent(repository::delete);
        } else {
            repository.save(b);
        }
    }

    private static void check(BigDecimal v, String label) {
        if (v != null && (v.signum() < 0 || v.compareTo(MAX_AMOUNT) > 0)) {
            throw new BusinessException(label + " không hợp lệ");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null || v.signum() == 0 ? null : v.setScale(0, RoundingMode.HALF_UP);
    }

    // ===== Thuc te / ke hoach =====

    public record Variance(MonthlyBudget budget, Double revenuePercent, Double costPercent, Double occupancyPoints, Double adrPercent,
                           BigDecimal revenueGap, boolean inProgress) {
        // Con thieu bao nhieu doanh thu de dat ke hoach (0 neu da dat / khong co ke hoach)
        public BigDecimal getRevenueShortfall() {
            return revenueGap == null || revenueGap.signum() >= 0 ? BigDecimal.ZERO : revenueGap.negate();
        }
    }

    public Optional<Variance> variance(ProfitService.MonthPnl actual) {
        return of(actual.month()).map(b -> variance(b, actual, !actual.month().isBefore(YearMonth.now())));
    }

    static Variance variance(MonthlyBudget b, ProfitService.MonthPnl a, boolean inProgress) {
        return new Variance(b,
                achieved(a.totalRevenue(), b.getRevenueTarget()),
                achieved(a.totalCost(), b.getCostBudget()),
                b.getOccupancyTarget() == null ? null : Math.round((a.occupancy() - b.getOccupancyTarget()) * 10) / 10.0,
                achieved(a.adr(), b.getAdrTarget()),
                b.getRevenueTarget() == null ? null : a.totalRevenue().subtract(b.getRevenueTarget()),
                inProgress);
    }

    // % thuc te / ke hoach (100 = dung ke hoach)
    static Double achieved(BigDecimal actual, BigDecimal target) {
        if (target == null || target.signum() == 0) {
            return null;
        }
        return actual.multiply(BigDecimal.valueOf(1000)).divide(target, 0, RoundingMode.HALF_UP).doubleValue() / 10;
    }
}
