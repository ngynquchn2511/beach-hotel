package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.PayrollService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;

// Bang luong theo ngay cong (ADMIN + ke toan); dat luong ngay / huy phieu luong chi ADMIN
@Controller
@RequestMapping("/admin/payroll")
public class AdminPayrollController {

    private final PayrollService payrollService;
    private final com.hotel.service.PunctualityService punctuality;

    public AdminPayrollController(PayrollService payrollService, com.hotel.service.PunctualityService punctuality) {
        this.payrollService = payrollService;
        this.punctuality = punctuality;
    }

    @GetMapping
    public String sheet(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        model.addAttribute("sheet", payrollService.sheet(m));
        model.addAttribute("month", m);
        model.addAttribute("canPay", !m.isAfter(YearMonth.now()));
        // Chuyen can thang (di muon, vang) -> goi y khau tru / thuong, dien san vao form tra luong
        model.addAttribute("punct", punctuality.month(m, null, java.time.LocalDateTime.now()));
        return "admin/staff/payroll";
    }

    @PostMapping("/wage")
    public String wage(@RequestParam Long staffId, @RequestParam(required = false) BigDecimal dailyWage,
                       @RequestParam(required = false) String month, RedirectAttributes ra) {
        try {
            payrollService.setDailyWage(staffId, dailyWage);
            ra.addFlashAttribute("successMessage", "Đã lưu lương ngày");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(month);
    }

    @PostMapping("/pay")
    public String pay(@RequestParam Long staffId, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                      @RequestParam(required = false) BigDecimal bonus, @RequestParam(required = false) BigDecimal deduction,
                      @RequestParam(required = false) String note, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        try {
            var p = payrollService.pay(staffId, month, bonus, deduction, note, user.getUser().getFullName());
            ra.addFlashAttribute("successMessage", "Đã trả lương " + p.getStaff().getFullName() + ": "
                    + String.format("%,d", p.getAmount().longValue()).replace(',', '.') + " đ (ghi vào chi phí lương)");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(month.toString());
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String month, RedirectAttributes ra) {
        try {
            payrollService.cancel(id);
            ra.addFlashAttribute("successMessage", "Đã hủy phiếu lương và khoản chi tương ứng");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(month);
    }

    private static String redirect(String month) {
        return "redirect:/admin/payroll" + (month != null && month.matches("\\d{4}-\\d{2}") ? "?month=" + month : "");
    }
}
