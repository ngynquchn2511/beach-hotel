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

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<User> all = customerManagementService.findAllCustomers();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("customers", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        model.addAttribute("customerTypes", CustomerType.values());
        return "admin/customers/list";
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
