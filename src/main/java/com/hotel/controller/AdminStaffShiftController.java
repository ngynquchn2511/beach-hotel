package com.hotel.controller;

import com.hotel.entity.Attendance;
import com.hotel.entity.DutyShift;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.StaffShiftService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

// Lich truc ca (moi nhan vien xem, ADMIN xep), ai dang truc, cham cong tu dong (nhan vien xem cua minh, ADMIN xem / sua tat ca)
@Controller
public class AdminStaffShiftController {

    // Thu 2 .. Chu nhat (theo DayOfWeek.getValue() - 1)
    static final List<String> DOW_LABELS = List.of("T2", "T3", "T4", "T5", "T6", "T7", "CN");

    private final StaffShiftService service;

    public AdminStaffShiftController(StaffShiftService service) {
        this.service = service;
    }

    @GetMapping("/admin/duty-roster")
    public String roster(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, Model model) {
        LocalDate day = week != null ? week : LocalDate.now();
        model.addAttribute("week", service.week(day));
        model.addAttribute("onDuty", service.onDuty(LocalDateTime.now()));
        model.addAttribute("shifts", DutyShift.Shift.values());
        model.addAttribute("staffList", service.staffList().stream().filter(u -> !Boolean.TRUE.equals(u.getLocked())).toList());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("weekdays", DayOfWeek.values());
        model.addAttribute("dowLabels", DOW_LABELS);
        return "admin/staff/roster";
    }

    @PostMapping("/admin/duty-roster/assign")
    public String assign(@RequestParam Long staffId, @RequestParam DutyShift.Shift shift,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) List<DayOfWeek> weekdays, @RequestParam(required = false) String note,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        try {
            int n = service.assign(staffId, from, to, shift, weekdays, note, user.getUser().getFullName());
            ra.addFlashAttribute("successMessage", n == 0 ? "Các ngày đã chọn đều đã có ca này" : "Đã xếp " + n + " ca");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/duty-roster?week=" + from;
    }

    @PostMapping("/admin/duty-roster/{id}/delete")
    public String remove(@PathVariable Long id, @RequestParam(required = false) String week, RedirectAttributes ra) {
        try {
            service.remove(id);
            ra.addFlashAttribute("successMessage", "Đã bỏ ca trực");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/duty-roster" + (week != null && week.matches("\\d{4}-\\d{2}-\\d{2}") ? "?week=" + week : "");
    }

    @PostMapping("/admin/duty-roster/copy")
    public String copy(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        LocalDate target = week.plusWeeks(1);
        try {
            int n = service.copyWeek(week, target, user.getUser().getFullName());
            ra.addFlashAttribute("successMessage", "Đã chép " + n + " ca sang tuần sau");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/duty-roster?week=" + target;
    }

    // Cham cong cua chinh minh (thang dang chon)
    @GetMapping("/admin/attendance/me")
    public String mine(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @AuthenticationPrincipal CustomUserDetails user, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        List<Attendance> list = service.attendances(m.atDay(1), m.atEndOfMonth(), user.getUser().getId());
        model.addAttribute("month", m);
        model.addAttribute("list", list);
        model.addAttribute("summary", StaffShiftService.summarize(list).get(user.getUser().getId()));
        model.addAttribute("me", user.getUser());
        model.addAttribute("todayAttendance", service.current(user.getUser().getId(), LocalDateTime.now()).orElse(null));
        return "admin/staff/my-attendance";
    }

    @PostMapping("/admin/attendance/end")
    public String end(@AuthenticationPrincipal CustomUserDetails user, RedirectAttributes ra) {
        try {
            Attendance a = service.endShift(user.getUser().getId(), LocalDateTime.now());
            ra.addFlashAttribute("successMessage", "Đã kết thúc ca lúc " + a.getCheckOutAt().toLocalTime().withNano(0)
                    + " - làm " + a.getWorkedText());
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/attendance/me";
    }

    // Bang cham cong toan bo nhan vien (ADMIN)
    @GetMapping("/admin/attendance")
    public String all(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                      @RequestParam(required = false) Long staffId, @RequestParam(defaultValue = "1") int page, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        List<Attendance> list = service.attendances(m.atDay(1), m.atEndOfMonth(), staffId);
        Pager<Attendance> pager = Pager.of(list, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("month", m);
        model.addAttribute("staffId", staffId);
        model.addAttribute("pager", pager);
        model.addAttribute("list", pager.items());
        model.addAttribute("summaries", StaffShiftService.summarize(list).values());
        model.addAttribute("staffList", service.staffList());
        model.addAttribute("shifts", DutyShift.Shift.values());
        model.addAttribute("today", LocalDate.now());
        return "admin/staff/attendance";
    }

    @PostMapping("/admin/attendance/{id}/adjust")
    public String adjust(@PathVariable Long id, @RequestParam(required = false) BigDecimal units, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String month, RedirectAttributes ra) {
        try {
            service.adjust(id, units, note);
            ra.addFlashAttribute("successMessage", "Đã cập nhật chấm công");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/attendance" + monthParam(month);
    }

    @PostMapping("/admin/attendance/manual")
    public String manual(@RequestParam Long staffId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                         @RequestParam DutyShift.Shift shift, @RequestParam(required = false) BigDecimal units,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        try {
            service.addManual(staffId, day, shift, units, note);
            ra.addFlashAttribute("successMessage", "Đã thêm công");
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/attendance?month=" + YearMonth.from(day);
    }

    private static String monthParam(String month) {
        return month != null && month.matches("\\d{4}-\\d{2}") ? "?month=" + month : "";
    }
}
