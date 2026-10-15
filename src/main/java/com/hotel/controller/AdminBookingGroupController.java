package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.exception.BusinessException;
import com.hotel.service.BookingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

// Doan dat nhieu phong: xem cac don, ghi nhan coc gop, huy ca doan (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/booking-groups")
public class AdminBookingGroupController {

    private final BookingService bookingService;

    public AdminBookingGroupController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        List<Booking> bookings = bookingService.groupBookings(id);
        model.addAttribute("group", bookingService.findGroup(id));
        model.addAttribute("bookings", bookings);
        model.addAttribute("deposit", bookings.stream().filter(Booking::isAwaitingDeposit)
                .map(Booking::getDepositAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "admin/booking-groups/detail";
    }

    @PostMapping("/{id}/confirm-deposit")
    public String confirmDeposit(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            int n = bookingService.confirmGroupDeposit(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận cọc và xác nhận " + n + " phòng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/booking-groups/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean fullRefund,
                         RedirectAttributes redirectAttributes) {
        try {
            int n = bookingService.cancelGroupByStaff(id, fullRefund);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy " + n + " phòng của đoàn");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/booking-groups/" + id;
    }
}
