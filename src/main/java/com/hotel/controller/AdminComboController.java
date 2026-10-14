package com.hotel.controller;

import com.hotel.dto.ComboRequest;
import com.hotel.entity.Combo;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ComboService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/admin/combos")
public class AdminComboController {

    private final ComboService comboService;

    public AdminComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<Combo> all = comboService.findAll();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("combos", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "admin/combos/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("comboRequest", new ComboRequest());
        model.addAttribute("isEdit", false);
        return "admin/combos/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("comboRequest") ComboRequest request,
                          BindingResult bindingResult,
                          @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/combos/form";
        }
        comboService.create(request, imageFile);
        return "redirect:/admin/combos";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var combo = comboService.findById(id);
        ComboRequest request = new ComboRequest();
        request.setName(combo.getName());
        request.setDescription(combo.getDescription());
        request.setNameEn(combo.getNameEn());
        request.setDescriptionEn(combo.getDescriptionEn());
        request.setPrice(combo.getPrice());
        request.setMaxGuests(combo.getMaxGuests());

        model.addAttribute("comboRequest", request);
        model.addAttribute("comboId", id);
        model.addAttribute("currentImageUrl", combo.getImageUrl());
        model.addAttribute("isEdit", true);
        return "admin/combos/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("comboRequest") ComboRequest request,
                          BindingResult bindingResult,
                          @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          Model model) {
        // Chi ADMIN duoc doi gia - STAFF gui len gia nao cung giu nguyen gia cu
        if (!currentUser.isAdmin()) {
            request.setPrice(comboService.findById(id).getPrice());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("comboId", id);
            model.addAttribute("isEdit", true);
            return "admin/combos/form";
        }
        comboService.update(id, request, imageFile);
        return "redirect:/admin/combos";
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id) {
        comboService.toggleActive(id);
        return "redirect:/admin/combos";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        comboService.delete(id);
        return "redirect:/admin/combos";
    }
}
