package com.hotel.service;

import com.hotel.entity.ChargeType;
import com.hotel.entity.Expense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Nhan dinh theo quy tac voi so lieu dung san
class InsightServiceTest {

    private static ProfitService.MonthPnl pnl(YearMonth m, long room, long service, long payroll, long sold, long available) {
        return new ProfitService.MonthPnl(m, BigDecimal.valueOf(room),
                service > 0 ? Map.of(ChargeType.SPA, BigDecimal.valueOf(service)) : Map.of(), BigDecimal.ZERO,
                Map.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                payroll > 0 ? Map.of(Expense.Category.PAYROLL, BigDecimal.valueOf(payroll)) : Map.of(), sold, available);
    }

    private static ProfitService.Report report(ProfitService.MonthPnl current, ProfitService.MonthPnl previous, List<ProfitService.MonthPnl> before) {
        List<ProfitService.MonthPnl> trend = new ArrayList<>(before);
        trend.add(current);
        return new ProfitService.Report(current, previous, previous, ProfitService.delta(current, previous), ProfitService.delta(current, previous), trend);
    }

    @Test
    void lossLowOccupancyLowServicesHighCancel_areFlagged_sortedBySeverity() {
        YearMonth m = YearMonth.of(2025, 5);
        var prev = pnl(m.minusMonths(1), 60_000_000, 0, 50_000_000, 120, 300);
        var cur = pnl(m, 40_000_000, 1_000_000, 50_000_000, 90, 310);
        List<InsightService.Insight> list = InsightService.analyze(report(cur, prev, List.of(prev, prev, prev)), 25);
        assertThat(list.get(0).level()).isEqualTo(InsightService.Level.CRITICAL);
        assertThat(list.get(0).title()).startsWith("Tháng đang lỗ");
        assertThat(list).extracting(InsightService.Insight::title)
                .anySatisfy(t -> assertThat(t).startsWith("Doanh thu giảm"))
                .anySatisfy(t -> assertThat(t).startsWith("Công suất thấp"))
                .anySatisfy(t -> assertThat(t).startsWith("Dịch vụ chỉ chiếm"))
                .anySatisfy(t -> assertThat(t).startsWith("Tỷ lệ hủy 25"));
    }

    @Test
    void expenseSpike_andHealthyMonth() {
        YearMonth m = YearMonth.of(2025, 8);
        var normal = pnl(m.minusMonths(1), 100_000_000, 20_000_000, 20_000_000, 270, 300);
        var spiked = pnl(m, 100_000_000, 20_000_000, 35_000_000, 270, 310);
        List<InsightService.Insight> list = InsightService.analyze(report(spiked, normal, List.of(normal, normal, normal)), 5);
        assertThat(list).extracting(InsightService.Insight::title).anySatisfy(t -> assertThat(t).startsWith("Lương & bảo hiểm tăng 75"));
        assertThat(list).extracting(InsightService.Insight::level).contains(InsightService.Level.GOOD);
        assertThat(list).noneMatch(i -> i.title().startsWith("Tỷ lệ hủy"));
    }

    @Test
    void noRevenue_singleInfo() {
        YearMonth m = YearMonth.of(2025, 1);
        var empty = pnl(m, 0, 0, 0, 0, 300);
        assertThat(InsightService.analyze(report(empty, empty, List.of()), 0)).singleElement()
                .satisfies(i -> assertThat(i.level()).isEqualTo(InsightService.Level.INFO));
    }
}
