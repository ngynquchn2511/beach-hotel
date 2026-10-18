package com.hotel.controller;

import com.hotel.entity.SpecialRate;
import com.hotel.exception.BusinessException;
import com.hotel.entity.PromotionRule;
import com.hotel.service.PricingService;
import com.hotel.service.PromotionService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
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
    private final PromotionService promotionService;
    private final com.hotel.util.CurrencyDisplay currencyDisplay;

    public AdminPricingController(PricingService pricingService, PromotionService promotionService,
                                  com.hotel.util.CurrencyDisplay currencyDisplay) {
        this.pricingService = pricingService;
        this.promotionService = promotionService;
        this.currencyDisplay = currencyDisplay;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("settings", pricingService.getSettings());
        Pager<SpecialRate> pager = Pager.of(pricingService.findAllSpecialRates(), page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("specialRates", pager.items());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("promotions", promotionService.findAll());
        model.addAttribute("promotionTypes", PromotionRule.Type.values());
        if (stayRuleService != null) {
            model.addAttribute("stayRules", stayRuleService.all());
            model.addAttribute("maxAdvanceDays", stayRuleService.maxAdvanceDays());
            model.addAttribute("roomTypes", roomTypeRepository.findAll());
        }
        return "admin/pricing/index";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam(required = false) Integer weekendSurchargePercent,
                                 @RequestParam(required = false) Integer depositPercent,
                                 @RequestParam(required = false) Integer depositDeadlineHours,
                                 @RequestParam(required = false) Integer refundFullHours,
                                 @RequestParam(required = false) Integer refundPartialHours,
                                 @RequestParam(required = false) Integer refundPartialPercent,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (refundFullHours == null && refundPartialHours == null && refundPartialPercent == null) {
                // Form khong gui chinh sach hoan coc -> giu nguyen chinh sach hien tai
                pricingService.updateSettings(weekendSurchargePercent, depositPercent, depositDeadlineHours);
            } else {
                pricingService.updateSettings(weekendSurchargePercent, depositPercent, depositDeadlineHours,
                        refundFullHours, refundPartialHours, refundPartialPercent);
            }
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

    // ===== Luat ban phong =====

    private com.hotel.service.StayRuleService stayRuleService;
    private com.hotel.repository.RoomTypeRepository roomTypeRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setStayRuleService(com.hotel.service.StayRuleService stayRuleService, com.hotel.repository.RoomTypeRepository roomTypeRepository) {
        this.stayRuleService = stayRuleService;
        this.roomTypeRepository = roomTypeRepository;
    }

    @PostMapping("/stay-rules")
    public String createStayRule(@RequestParam(required = false) String name, @RequestParam(required = false) String nameEn,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                 @RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) Integer minNights,
                                 @RequestParam(defaultValue = "false") boolean stopSell, RedirectAttributes redirectAttributes) {
        try {
            stayRuleService.create(name, nameEn, startDate, endDate, roomTypeId, minNights, stopSell);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm luật bán phòng. Đơn đã đặt không bị ảnh hưởng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#luat-ban-phong";
    }

    @PostMapping("/stay-rules/{id}/delete")
    public String deleteStayRule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            stayRuleService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa luật bán phòng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#luat-ban-phong";
    }

    @PostMapping("/max-advance")
    public String maxAdvance(@RequestParam(required = false) Integer maxAdvanceDays, RedirectAttributes redirectAttributes) {
        try {
            stayRuleService.updateMaxAdvanceDays(maxAdvanceDays);
            redirectAttributes.addFlashAttribute("successMessage", maxAdvanceDays == null || maxAdvanceDays == 0
                    ? "Đã bỏ giới hạn đặt trước" : "Khách chỉ đặt được trước tối đa " + maxAdvanceDays + " ngày");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#luat-ban-phong";
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

    // ===== Khuyen mai tu dong =====

    @PostMapping("/promotions")
    public String createPromotion(@RequestParam(required = false) String name,
                                  @RequestParam(required = false) String nameEn,
                                  @RequestParam(required = false) PromotionRule.Type type,
                                  @RequestParam(required = false) Integer threshold,
                                  @RequestParam(required = false) Integer discountPercent,
                                  RedirectAttributes redirectAttributes) {
        try {
            promotionService.create(name, nameEn, type, threshold, discountPercent);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm khuyến mãi tự động");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#khuyen-mai";
    }

    @PostMapping("/promotions/{id}/toggle")
    public String togglePromotion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            promotionService.toggle(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#khuyen-mai";
    }

    @PostMapping("/promotions/{id}/delete")
    public String deletePromotion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        promotionService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khuyến mãi. Đơn đã đặt giữ nguyên mức giảm");
        return "redirect:/admin/pricing#khuyen-mai";
    }

    @PostMapping("/member-discounts")
    public String memberDiscounts(@RequestParam(required = false) Integer memberNew, @RequestParam(required = false) Integer memberRegular,
                                  @RequestParam(required = false) Integer memberVip, RedirectAttributes redirectAttributes) {
        try {
            pricingService.updateMemberDiscounts(memberNew, memberRegular, memberVip);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu giá thành viên. Áp dụng cho các đơn đặt từ bây giờ.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#gia-thanh-vien";
    }

    @PostMapping("/usd-rate")
    public String updateUsdRate(@RequestParam(required = false) Integer usdRate, RedirectAttributes redirectAttributes) {
        try {
            pricingService.updateUsdRate(usdRate);
            currencyDisplay.evict();
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu tỷ giá USD");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/pricing#ty-gia";
    }
}
