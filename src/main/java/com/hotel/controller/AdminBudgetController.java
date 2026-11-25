package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.BudgetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Year;
import java.time.YearMonth;
import java.util.Map;

// Ke hoach doanh thu / chi phi / cong suat / ADR 12 thang (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/budget")
public class AdminBudgetController {

    private final BudgetService budgetService;
    private com.hotel.service.ProfitService profitService;

    public AdminBudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setProfitService(com.hotel.service.ProfitService profitService) {
        this.profitService = profitService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Integer year, Model model) {
        int y = year != null ? year : Year.now().getValue();
        model.addAttribute("year", y);
        model.addAttribute("budgets", budgetService.year(y));
        // Thuc te tung thang (chi cac thang da bat dau) de ve ke hoach so voi thuc te
        java.util.List<BigDecimal> actualRevenue = new java.util.ArrayList<>();
        java.util.List<BigDecimal> actualCost = new java.util.ArrayList<>();
        java.util.List<Double> actualOccupancy = new java.util.ArrayList<>();
        YearMonth now = YearMonth.now();
        java.util.List<com.hotel.service.ProfitService.MonthPnl> trend = profitService == null || YearMonth.of(y, 1).isAfter(now)
                ? java.util.List.of() : profitService.report(YearMonth.of(y, 12)).trend();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(y, m);
            com.hotel.service.ProfitService.MonthPnl p = ym.isAfter(now) ? null
                    : trend.stream().filter(t -> t.month().equals(ym)).findFirst().orElse(null);
            actualRevenue.add(p == null ? null : p.totalRevenue());
            actualCost.add(p == null ? null : p.totalCost());
            actualOccupancy.add(p == null ? null : p.occupancy());
        }
        model.addAttribute("actualRevenue", actualRevenue);
        model.addAttribute("actualCost", actualCost);
        model.addAttribute("actualOccupancy", actualOccupancy);
        model.addAttribute("currentMonth", y == now.getYear() ? now.getMonthValue() : (y < now.getYear() ? 12 : 0));
        return "admin/accounting/budget";
    }

    // Truong form: revenue_M, cost_M, occupancy_M, adr_M voi M = 1..12
    @PostMapping
    public String save(@RequestParam int year, @RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        try {
            for (int m = 1; m <= 12; m++) {
                budgetService.save(YearMonth.of(year, m), amount(params.get("revenue_" + m)), amount(params.get("cost_" + m)),
                        integer(params.get("occupancy_" + m)), amount(params.get("adr_" + m)));
            }
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu kế hoạch năm " + year);
        } catch (NumberFormatException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số liệu kế hoạch không hợp lệ - chỉ nhập số");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/budget?year=" + year;
    }

    private static BigDecimal amount(String v) {
        return v == null || v.isBlank() ? null : new BigDecimal(v.trim().replace(".", "").replace(",", ""));
    }

    private static Integer integer(String v) {
        return v == null || v.isBlank() ? null : Integer.valueOf(v.trim());
    }
}
