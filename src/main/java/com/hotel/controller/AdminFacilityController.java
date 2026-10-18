package com.hotel.controller;

import com.hotel.entity.FacilityReservation;
import com.hotel.entity.ServiceStatus;
import com.hotel.exception.BusinessException;
import com.hotel.service.FacilityReservationService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

// Lich spa / ban nha hang cua khach dang o (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/facility-reservations")
public class AdminFacilityController {

    private final FacilityReservationService facilityService;

    public AdminFacilityController(FacilityReservationService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) ServiceStatus status,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        LocalDate fromDate = from != null ? from : LocalDate.now();
        Pager<FacilityReservation> pager = Pager.of(facilityService.forStaff(fromDate, status), page, PaginationUtil.DEFAULT_PAGE_SIZE * 2);
        model.addAttribute("from", fromDate);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ServiceStatus.values());
        model.addAttribute("pager", pager);
        model.addAttribute("reservations", pager.items());
        model.addAttribute("restaurantRating", facilityService.restaurantRating());
        var spaRatings = facilityService.spaRatings();
        long spaCount = spaRatings.values().stream().mapToLong(FacilityReservationService.RatingStat::count).sum();
        double spaAvg = spaCount == 0 ? 0 : spaRatings.values().stream().mapToDouble(s -> s.average() * s.count()).sum() / spaCount;
        model.addAttribute("spaRating", new FacilityReservationService.RatingStat(Math.round(spaAvg * 10) / 10.0, spaCount));
        model.addAttribute("recentRatings", facilityService.recentRatings(5));
        return "admin/facility-reservations/list";
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            FacilityReservation r = facilityService.confirm(id);
            redirectAttributes.addFlashAttribute("successMessage", r.isSpa()
                    ? "Đã xác nhận lịch spa, tiền spa đã cộng vào đơn phòng #" + r.getBooking().getId()
                    : "Đã xác nhận đặt bàn cho đơn #" + r.getBooking().getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/facility-reservations";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            facilityService.cancelByStaff(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy lịch");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/facility-reservations";
    }
}
