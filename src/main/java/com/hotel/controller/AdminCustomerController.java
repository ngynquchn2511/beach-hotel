package com.hotel.controller;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.service.CustomerManagementService;
import com.hotel.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/customers")
public class AdminCustomerController {

    private final CustomerManagementService customerManagementService;

    public AdminCustomerController(CustomerManagementService customerManagementService) {
        this.customerManagementService = customerManagementService;
    }

    private com.hotel.service.CustomerProfileService profileService;

    @org.springframework.beans.factory.annotation.Autowired
    void setProfileService(com.hotel.service.CustomerProfileService profileService) {
        this.profileService = profileService;
    }

    // Danh sach khach: tim theo ten / email / SDT, loc theo hang; kem so lan dat, so lan o, lan o gan nhat
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) CustomerType type,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        List<com.hotel.service.CustomerProfileService.Row> all = profileService.list(q, type);
        model.addAttribute("pager", com.hotel.util.Pager.of(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("totalElements", all.size());
        // Dem theo hang (theo o tim kiem, bo qua loc hang) cho tab + thanh co cau
        List<com.hotel.service.CustomerProfileService.Row> forCounts = type == null ? all : profileService.list(q, null);
        java.util.Map<String, Long> typeCounts = new java.util.LinkedHashMap<>();
        for (CustomerType ct : CustomerType.values()) {
            typeCounts.put(ct.name(), forCounts.stream().filter(r -> r.customer().getCustomerType() == ct).count());
        }
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("typeTotal", (long) forCounts.size());
        model.addAttribute("customerTypes", CustomerType.values());
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        return "admin/customers/list";
    }

    // Ho so 1 khach: toan bo lich su dat phong + thong ke + tien do len hang
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("p", profileService.profile(id));
        return "admin/customers/detail";
    }

    @PostMapping("/{id}/update-type")
    public String updateType(@PathVariable Long id, @RequestParam CustomerType customerType) {
        customerManagementService.updateCustomerType(id, customerType);
        return "redirect:/admin/customers";
    }

    @PostMapping("/{id}/toggle-lock")
    public String toggleLock(@PathVariable Long id) {
        customerManagementService.toggleLocked(id);
        return "redirect:/admin/customers";
    }
}
