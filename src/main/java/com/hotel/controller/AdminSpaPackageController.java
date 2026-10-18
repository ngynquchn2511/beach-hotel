package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.SpaPackageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

// Quan ly goi spa: them, sua gia / thoi luong, tam ngung (chi ADMIN - SecurityConfig)
@Controller
@RequestMapping("/admin/spa-packages")
public class AdminSpaPackageController {

    private final SpaPackageService spaPackageService;
    private final com.hotel.service.FacilityReservationService facilityService;

    public AdminSpaPackageController(SpaPackageService spaPackageService, com.hotel.service.FacilityReservationService facilityService) {
        this.spaPackageService = spaPackageService;
        this.facilityService = facilityService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("packages", spaPackageService.all());
        model.addAttribute("ratings", facilityService.spaRatings());
        model.addAttribute("minMinutes", SpaPackageService.MIN_MINUTES);
        model.addAttribute("maxMinutes", SpaPackageService.MAX_MINUTES);
        return "admin/spa-packages/index";
    }

    @PostMapping
    public String create(@RequestParam(required = false) String name, @RequestParam(required = false) String nameEn,
                         @RequestParam(required = false) Integer minutes, @RequestParam(required = false) BigDecimal price,
                         RedirectAttributes redirectAttributes) {
        return run(() -> spaPackageService.create(name, nameEn, minutes, price), "Đã thêm gói spa", redirectAttributes);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam(required = false) String name,
                         @RequestParam(required = false) String nameEn, @RequestParam(required = false) Integer minutes,
                         @RequestParam(required = false) BigDecimal price, RedirectAttributes redirectAttributes) {
        return run(() -> spaPackageService.update(id, name, nameEn, minutes, price),
                "Đã lưu gói spa. Giá mới áp dụng cho lịch đặt từ bây giờ", redirectAttributes);
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> spaPackageService.toggle(id), "Đã cập nhật trạng thái gói", redirectAttributes);
    }

    private static String run(Runnable action, String success, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/spa-packages";
    }
}
