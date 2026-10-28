package com.hotel.controller;

import com.hotel.entity.UpgradeOffer;
import com.hotel.exception.BusinessException;
import com.hotel.service.UpgradeService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

// Nang hang phong: danh sach loi moi + ket qua (ADMIN, STAFF); cai dat uu dai / tu dong gui chi ADMIN
@Controller
@RequestMapping("/admin/upgrades")
public class AdminUpgradeController {

    private final UpgradeService upgradeService;

    public AdminUpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "1") int page, Model model) {
        List<UpgradeOffer> all = upgradeService.all();
        Pager<UpgradeOffer> pager = Pager.of(all, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        List<UpgradeOffer> accepted = all.stream().filter(o -> o.getStatus() == UpgradeOffer.Status.ACCEPTED).toList();
        long bookingsOffered = all.stream().map(o -> o.getBooking().getId()).distinct().count();
        model.addAttribute("pager", pager);
        model.addAttribute("offers", pager.items());
        model.addAttribute("bookingsOffered", bookingsOffered);
        model.addAttribute("acceptedCount", accepted.size());
        model.addAttribute("acceptRate", bookingsOffered == 0 ? 0 : Math.round(100.0 * accepted.size() / bookingsOffered));
        model.addAttribute("revenue", accepted.stream().map(UpgradeOffer::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("discountPercent", upgradeService.discountPercent());
        model.addAttribute("autoEnabled", upgradeService.autoEnabled());
        model.addAttribute("autoDays", UpgradeService.AUTO_DAYS_BEFORE);
        return "admin/upgrades/index";
    }

    @PostMapping("/settings")
    public String settings(@RequestParam(required = false) Integer discountPercent, @RequestParam(defaultValue = "false") boolean auto,
                           RedirectAttributes redirectAttributes) {
        try {
            upgradeService.updateSettings(discountPercent, auto);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu cài đặt nâng hạng");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/upgrades";
    }
}
