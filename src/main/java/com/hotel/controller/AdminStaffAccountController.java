package com.hotel.controller;

import com.hotel.dto.StaffAccountRequest;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.StaffAccountService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// Quan ly tai khoan nhan vien - SecurityConfig chi cho ADMIN vao /admin/staff-accounts/**
@Controller
@RequestMapping("/admin/staff-accounts")
public class AdminStaffAccountController {

    private static final List<UserRole> STAFF_ROLES = List.of(UserRole.STAFF, UserRole.HOUSEKEEPER, UserRole.ACCOUNTANT, UserRole.ADMIN);

    private final StaffAccountService staffAccountService;

    public AdminStaffAccountController(StaffAccountService staffAccountService) {
        this.staffAccountService = staffAccountService;
    }

    // Loc theo loai nhan vien (quan tri / le tan / buong phong / ke toan), trang thai khoa, tim ten / email / SDT
    @GetMapping
    public String list(@RequestParam(required = false) UserRole role,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        List<User> everyone = staffAccountService.findAllStaffAccounts();
        // Khoa = ten vai tro (Thymeleaf khong doc duoc EnumMap theo bien enum)
        java.util.Map<String, Long> roleCounts = new java.util.HashMap<>();
        for (UserRole r : STAFF_ROLES) {
            roleCounts.put(r.name(), everyone.stream().filter(u -> u.getRole() == r).count());
        }
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<User> all = everyone.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> !"active".equals(status) || !u.isLocked())
                .filter(u -> !"locked".equals(status) || u.isLocked())
                .filter(u -> needle == null || contains(u.getFullName(), needle) || contains(u.getEmail(), needle)
                        || contains(u.getPhoneNumber(), needle))
                .toList();
        com.hotel.util.Pager<User> pager = com.hotel.util.Pager.of(all, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("accounts", pager.items());
        model.addAttribute("totalCount", everyone.size());
        model.addAttribute("filteredCount", all.size());
        model.addAttribute("roleCounts", roleCounts);
        model.addAttribute("roles", List.of(UserRole.ADMIN, UserRole.STAFF, UserRole.HOUSEKEEPER, UserRole.ACCOUNTANT));
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        return "admin/staff-accounts/list";
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        StaffAccountRequest request = new StaffAccountRequest();
        request.setRole(UserRole.STAFF);
        model.addAttribute("staffAccountRequest", request);
        prepareForm(model, false, null);
        return "admin/staff-accounts/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("staffAccountRequest") StaffAccountRequest request,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, false, null);
            return "admin/staff-accounts/form";
        }
        try {
            staffAccountService.create(request);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            prepareForm(model, false, null);
            return "admin/staff-accounts/form";
        }
        return "redirect:/admin/staff-accounts";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = staffAccountService.findById(id);
        StaffAccountRequest request = new StaffAccountRequest();
        request.setFullName(user.getFullName());
        request.setEmail(user.getEmail());
        request.setPhoneNumber(user.getPhoneNumber());
        request.setRole(user.getRole());
        model.addAttribute("staffAccountRequest", request);
        prepareForm(model, true, id);
        return "admin/staff-accounts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("staffAccountRequest") StaffAccountRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, true, id);
            return "admin/staff-accounts/form";
        }
        try {
            staffAccountService.update(id, request, currentUser.getUser().getId());
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            prepareForm(model, true, id);
            return "admin/staff-accounts/form";
        }
        return "redirect:/admin/staff-accounts";
    }

    @PostMapping("/{id}/toggle-lock")
    public String toggleLock(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             RedirectAttributes redirectAttributes) {
        try {
            staffAccountService.toggleLocked(id, currentUser.getUser().getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/staff-accounts";
    }

    private void prepareForm(Model model, boolean isEdit, Long accountId) {
        model.addAttribute("roles", STAFF_ROLES);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("accountId", accountId);
    }
}
