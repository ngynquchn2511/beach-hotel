package com.hotel.controller;

import com.hotel.entity.ChargeType;
import com.hotel.entity.Expense;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ExpenseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Chi phi van hanh + gia von dich vu / phi thanh toan (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/expenses")
public class AdminExpenseController {

    // Loai phu phi co gia von (dich vu ban kem)
    static final List<ChargeType> COSTED_TYPES = List.of(ChargeType.ROOM_SERVICE, ChargeType.MINIBAR, ChargeType.LAUNDRY,
            ChargeType.SPA, ChargeType.DIVING_TOUR, ChargeType.AIRPORT_TRANSFER, ChargeType.EARLY_CHECKIN, ChargeType.LATE_CHECKOUT,
            ChargeType.DAMAGE, ChargeType.OTHER);

    private final ExpenseService expenseService;

    public AdminExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        List<Expense> expenses = expenseService.forMonth(m);
        model.addAttribute("month", m);
        model.addAttribute("expenses", expenses);
        model.addAttribute("expensePager", com.hotel.util.Pager.of(expenses, page, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("totals", expenseService.totalsByCategory(m));
        model.addAttribute("total", expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("categories", Expense.Category.values());
        model.addAttribute("costTypes", COSTED_TYPES);
        // Khoa chuoi (ten enum): SpEL khong tra duoc EnumMap bang khoa enum
        Map<String, Integer> rates = new java.util.HashMap<>();
        expenseService.costRates().forEach((k, v) -> rates.put(k.name(), v));
        Map<String, Integer> suggested = new java.util.HashMap<>();
        ExpenseService.SUGGESTED_COST_PERCENT.forEach((k, v) -> suggested.put(k.name(), v));
        model.addAttribute("costRates", rates);
        model.addAttribute("suggested", suggested);
        model.addAttribute("paymentFeePercent", expenseService.paymentFeePercent());
        model.addAttribute("variableCostPerNight", expenseService.variableCostPerNight());
        model.addAttribute("today", LocalDate.now());
        return "admin/accounting/expenses";
    }

    @PostMapping
    public String create(@RequestParam(required = false) Expense.Category category, @RequestParam(required = false) String description,
                         @RequestParam(required = false) BigDecimal amount, @RequestParam(required = false) Expense.Recurrence recurrence,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth startMonth,
                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth endMonth,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        YearMonth back = recurrence == Expense.Recurrence.MONTHLY && startMonth != null ? startMonth
                : expenseDate != null ? YearMonth.from(expenseDate) : YearMonth.now();
        try {
            expenseService.create(category, description, amount, recurrence, expenseDate, startMonth, endMonth,
                    user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm khoản chi");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + back;
    }

    @PostMapping("/{id}/stop")
    public String stop(@PathVariable Long id, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       RedirectAttributes redirectAttributes) {
        try {
            expenseService.stopMonthly(id, month.minusMonths(1));
            redirectAttributes.addFlashAttribute("successMessage", "Đã dừng khoản chi hàng tháng từ tháng " + month.getMonthValue() + "/" + month.getYear());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + month;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                         RedirectAttributes redirectAttributes) {
        try {
            expenseService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khoản chi");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + month;
    }

    // Ty le gia von tung dich vu (cost_<TYPE>), phi cong thanh toan, chi phi bien doi moi phong-dem
    @PostMapping("/cost-settings")
    public String costSettings(@RequestParam Map<String, String> params, @RequestParam(required = false) BigDecimal paymentFeePercent,
                               @RequestParam(required = false) BigDecimal variableCostPerNight,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                               RedirectAttributes redirectAttributes) {
        Map<ChargeType, Integer> percents = new EnumMap<>(ChargeType.class);
        try {
            for (ChargeType t : COSTED_TYPES) {
                String v = params.get("cost_" + t.name());
                percents.put(t, v == null || v.isBlank() ? 0 : Integer.valueOf(v.trim()));
            }
            expenseService.updateCostSettings(percents, paymentFeePercent, variableCostPerNight);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu giá vốn và chi phí biến đổi");
        } catch (NumberFormatException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tỷ lệ giá vốn phải là số nguyên");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses" + (month != null ? "?month=" + month : "") + "#gia-von";
    }
}
