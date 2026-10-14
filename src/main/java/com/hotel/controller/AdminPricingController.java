package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.PricingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

// Cai dat gia theo ngay (phu thu cuoi tuan, ngay le) va chinh sach dat coc - chi ADMIN (xem SecurityConfig)
@Controller
@RequestMapping("/admin/pricing")
public class AdminPricingController {

    private final PricingService pricingService;

    public AdminPricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("settings", pricingService.getSettings());
        model.addAttribute("specialRates", pricingService.findAllSpecialRates());
        model.addAttribute("today", LocalDate.now());
        return "admin/pricing/index";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam(required = false) Integer weekendSurchargePercent,
                                 @RequestParam(required = false) Integer depositPercent,
                                 @RequestParam(required = false) Integer depositDeadlineHours,
                                 RedirectAttributes redirectAttributes) {
        try {
            pricingService.updateSettings(weekendSurchargePercent, depositPercent, depositDeadlineHours);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu cài đặt. Áp dụng cho các đơn đặt từ bây giờ.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing";
    }

    @PostMapping("/special-rates")
    public String createSpecialRate(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String nameEn,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                    @RequestParam(required = false) Integer surchargePercent,
                                    RedirectAttributes redirectAttributes) {
        try {
            pricingService.createSpecialRate(name, nameEn, startDate, endDate, surchargePercent);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm giai đoạn giá ngày lễ");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#ngay-le";
    }

    @PostMapping("/special-rates/{id}/delete")
    public String deleteSpecialRate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pricingService.deleteSpecialRate(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa giai đoạn giá");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#ngay-le";
    }
}
