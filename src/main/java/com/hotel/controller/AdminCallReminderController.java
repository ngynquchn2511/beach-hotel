package com.hotel.controller;

import com.hotel.entity.CallReminder;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.CallReminderService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

// Goi dien nhac khach (STAFF + ADMIN): danh sach khach can goi + ghi ket qua cuoc goi + lich su goi
@Controller
@RequestMapping("/admin/call-reminders")
public class AdminCallReminderController {

    private final CallReminderService service;

    public AdminCallReminderController(CallReminderService service) {
        this.service = service;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("tasks", service.due(LocalDateTime.now()));
        Pager<CallReminder> pager = Pager.of(service.history(), page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("results", CallReminder.Result.values());
        model.addAttribute("arrivalDays", CallReminderService.ARRIVAL_DAYS);
        model.addAttribute("depositHours", CallReminderService.DEPOSIT_HOURS);
        model.addAttribute("noteMax", CallReminderService.NOTE_MAX);
        return "admin/call-reminders/index";
    }

    // back: quay lai trang chi tiet don neu ghi tu do
    @PostMapping("/{bookingId}")
    public String record(@PathVariable Long bookingId, @RequestParam(required = false) CallReminder.Purpose purpose,
                         @RequestParam(required = false) CallReminder.Result result, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String back,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            CallReminder call = service.record(bookingId, purpose, result, note, user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ghi cuộc gọi đơn #" + bookingId + ": " + call.getResult().getLabel());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "booking".equals(back) ? "redirect:/admin/bookings/" + bookingId : "redirect:/admin/call-reminders";
    }
}
