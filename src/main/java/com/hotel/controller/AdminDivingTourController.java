package com.hotel.controller;

import com.hotel.dto.DivingTourRequest;
import com.hotel.entity.DivingTour;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.DivingTourService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// Danh muc tour lan bien - them/xoa chi ADMIN (SecurityConfig), STAFF xem/sua mo ta, an/hien
@Controller
@RequestMapping("/admin/diving-tours")
public class AdminDivingTourController {

    private final DivingTourService divingTourService;

    public AdminDivingTourController(DivingTourService divingTourService) {
        this.divingTourService = divingTourService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<DivingTour> all = divingTourService.findAll();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("tours", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "admin/diving-tours/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("tourRequest", new DivingTourRequest());
        model.addAttribute("isEdit", false);
        return "admin/diving-tours/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("tourRequest") DivingTourRequest request, BindingResult bindingResult,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/diving-tours/form";
        }
        divingTourService.create(request, imageFile);
        return "redirect:/admin/diving-tours";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        DivingTour tour = divingTourService.findById(id);
        model.addAttribute("tourRequest", divingTourService.toRequest(tour));
        model.addAttribute("tourId", id);
        model.addAttribute("currentImageUrl", tour.getImageUrl());
        model.addAttribute("isEdit", true);
        return "admin/diving-tours/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("tourRequest") DivingTourRequest request, BindingResult bindingResult,
                         @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                         @AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        // Chi ADMIN duoc doi gia va so cho - STAFF gui len gia tri nao cung giu nguyen
        if (!currentUser.isAdmin()) {
            DivingTour current = divingTourService.findById(id);
            request.setPricePerPerson(current.getPricePerPerson());
            request.setMaxParticipants(current.getMaxParticipants());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("tourId", id);
            model.addAttribute("currentImageUrl", divingTourService.findById(id).getImageUrl());
            model.addAttribute("isEdit", true);
            return "admin/diving-tours/form";
        }
        divingTourService.update(id, request, imageFile);
        return "redirect:/admin/diving-tours";
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id) {
        divingTourService.toggleActive(id);
        return "redirect:/admin/diving-tours";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            divingTourService.delete(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/diving-tours";
    }
}
