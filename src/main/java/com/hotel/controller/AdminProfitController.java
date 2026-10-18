package com.hotel.controller;

import com.hotel.entity.ChargeType;
import com.hotel.entity.Expense;
import com.hotel.service.ProfitService;
import com.hotel.util.TrendChartSvg;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// Bao cao lai lo theo thang + xu huong 12 thang (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/pnl")
public class AdminProfitController {

    private final ProfitService profitService;
    private final com.hotel.service.BudgetService budgetService;

    public AdminProfitController(ProfitService profitService, com.hotel.service.BudgetService budgetService) {
        this.profitService = profitService;
        this.budgetService = budgetService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        ProfitService.Report report = profitService.report(m);
        List<ProfitService.MonthPnl> trend = report.trend();
        List<String> labels = trend.stream().map(p -> p.month().getMonthValue() + "/" + (p.month().getYear() % 100)).toList();
        model.addAttribute("month", m);
        model.addAttribute("report", report);
        model.addAttribute("chargeTypes", ChargeType.values());
        model.addAttribute("expenseCategories", Expense.Category.values());
        model.addAttribute("revenueCostSvg", TrendChartSvg.groupedBars(labels,
                trend.stream().map(ProfitService.MonthPnl::totalRevenue).toList(), "Doanh thu",
                trend.stream().map(ProfitService.MonthPnl::totalCost).toList(), "Chi phí", "Doanh thu và chi phí 12 tháng"));
        model.addAttribute("profitSvg", TrendChartSvg.signedBars(labels,
                trend.stream().map(ProfitService.MonthPnl::operatingProfit).toList(), "Lãi vận hành", "Lãi vận hành 12 tháng"));
        model.addAttribute("blue", TrendChartSvg.BLUE);
        model.addAttribute("orange", TrendChartSvg.ORANGE);
        model.addAttribute("red", TrendChartSvg.RED);
        model.addAttribute("hasCosts", trend.stream().anyMatch(p -> p.totalCost().signum() > 0));
        budgetService.variance(report.current()).ifPresent(v -> model.addAttribute("variance", v));
        return "admin/accounting/pnl";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.now();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lai-lo-" + m + ".xlsx\"")
                .body(profitService.excel(m));
    }

    // Dung trong template: so tien theo khoa, khong co = 0
    public static BigDecimal of(java.util.Map<?, BigDecimal> map, Object key) {
        BigDecimal v = map.get(key);
        return v == null ? BigDecimal.ZERO : v;
    }
}
