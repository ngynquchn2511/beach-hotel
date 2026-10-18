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

    public AdminBudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Integer year, Model model) {
        int y = year != null ? year : Year.now().getValue();
        model.addAttribute("year", y);
        model.addAttribute("budgets", budgetService.year(y));
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
