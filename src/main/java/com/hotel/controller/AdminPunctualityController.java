package com.hotel.controller;

import com.hotel.entity.DutyShift;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.PunctualityService;
import com.hotel.service.StaffShiftService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

// Chuyen can theo thang (quan tri + ke toan): ai di muon, luc nao, ai vang ca -> goi y tru / thuong khi tra luong
@Controller
public class AdminPunctualityController {

    private final PunctualityService service;
    private final StaffShiftService shiftService;

    public AdminPunctualityController(PunctualityService service, StaffShiftService shiftService) {
        this.service = service;
        this.shiftService = shiftService;
    }

    @GetMapping("/admin/punctuality")
    public String index(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                        @RequestParam(required = false) Long staffId, @RequestParam(defaultValue = "false") boolean all, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        LocalDateTime now = LocalDateTime.now();
        List<PunctualityService.Summary> ranking = service.ranking(m, now);
        model.addAttribute("month", m);
        model.addAttribute("ranking", ranking);
        model.addAttribute("staffList", shiftService.staffList());
        model.addAttribute("staffId", staffId);
        model.addAttribute("all", all);
        model.addAttribute("totalLate", ranking.stream().mapToInt(PunctualityService.Summary::getLate).sum());
        model.addAttribute("totalAbsent", ranking.stream().mapToInt(PunctualityService.Summary::getAbsent).sum());
        model.addAttribute("totalEarly", ranking.stream().mapToInt(PunctualityService.Summary::getEarly).sum());
        model.addAttribute("totalDone", ranking.stream().mapToInt(PunctualityService.Summary::getDone).sum());
        model.addAttribute("totalDeduction", ranking.stream().map(PunctualityService.Summary::getDeduction).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (staffId != null) {
            model.addAttribute("person", ranking.stream().filter(s -> s.getStaff().getId().equals(staffId)).findFirst().orElse(null));
        } else {
            // Danh sach vi pham ca thang (moi nhat truoc)
            model.addAttribute("incidents", service.checks(m.atDay(1), m.atEndOfMonth(), null, now).stream()
                    .filter(c -> c.status().isViolation()).sorted((a, b) -> b.duty().getStartAt().compareTo(a.duty().getStartAt())).toList());
        }
        model.addAttribute("policy", new Policy());
        return "admin/staff/punctuality";
    }

    @PostMapping("/admin/punctuality/excuse")
    public String excuse(@RequestParam Long staffId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                         @RequestParam DutyShift.Shift shift, @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        try {
            service.excuse(staffId, day, shift, reason, user.getUser().getFullName());
            ra.addFlashAttribute("successMessage", "Đã ghi nhận nghỉ có phép - ca này không bị trừ tiền");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/punctuality?month=" + YearMonth.from(day) + "&staffId=" + staffId;
    }

    // Quy dinh hien o cuoi trang
    public static class Policy {
        public int getFreeLates() { return PunctualityService.FREE_LATES_PER_MONTH; }
        public int getFreeLateMinutes() { return PunctualityService.FREE_LATE_MAX_MINUTES; }
        public long getGrace() { return StaffShiftService.LATE_GRACE_MINUTES; }
        public long getEarlyMinutes() { return PunctualityService.EARLY_LEAVE_MINUTES; }
        public int getBonusMinShifts() { return PunctualityService.BONUS_MIN_SHIFTS; }
        public BigDecimal getBonus() { return PunctualityService.PERFECT_BONUS; }
        public BigDecimal getMinLate() { return PunctualityService.MIN_LATE_DEDUCTION; }
    }
}
