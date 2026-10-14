package com.hotel.controller;

import com.hotel.dto.DiscountCodeRequest;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountCode;
import com.hotel.entity.DiscountType;
import com.hotel.exception.BusinessException;
import com.hotel.service.DiscountCodeService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/discount-codes")
public class AdminDiscountCodeController {

    private final DiscountCodeService discountCodeService;

    public AdminDiscountCodeController(DiscountCodeService discountCodeService) {
        this.discountCodeService = discountCodeService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<DiscountCode> all = discountCodeService.findAll();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("discountCodes", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "admin/discount-codes/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("discountCodeRequest", new DiscountCodeRequest());
        addEnums(model);
        model.addAttribute("isEdit", false);
        return "admin/discount-codes/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("discountCodeRequest") DiscountCodeRequest request,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addEnums(model);
            model.addAttribute("isEdit", false);
            return "admin/discount-codes/form";
        }
        try {
            discountCodeService.create(request);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addEnums(model);
            model.addAttribute("isEdit", false);
            return "admin/discount-codes/form";
        }
        return "redirect:/admin/discount-codes";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var discountCode = discountCodeService.findById(id);
        DiscountCodeRequest request = new DiscountCodeRequest();
        request.setCode(discountCode.getCode());
        request.setDescription(discountCode.getDescription());
        request.setDescriptionEn(discountCode.getDescriptionEn());
        request.setDiscountType(discountCode.getDiscountType());
        request.setDiscountValue(discountCode.getDiscountValue());
        request.setApplicableCustomerType(discountCode.getApplicableCustomerType());

        model.addAttribute("discountCodeRequest", request);
        model.addAttribute("discountCodeId", id);
        addEnums(model);
        model.addAttribute("isEdit", true);
        return "admin/discount-codes/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("discountCodeRequest") DiscountCodeRequest request,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("discountCodeId", id);
            addEnums(model);
            model.addAttribute("isEdit", true);
            return "admin/discount-codes/form";
        }
        try {
            discountCodeService.update(id, request);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("discountCodeId", id);
            addEnums(model);
            model.addAttribute("isEdit", true);
            return "admin/discount-codes/form";
        }
        return "redirect:/admin/discount-codes";
    }

    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable Long id) {
        discountCodeService.toggleActive(id);
        return "redirect:/admin/discount-codes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        discountCodeService.delete(id);
        return "redirect:/admin/discount-codes";
    }

    private void addEnums(Model model) {
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("customerTypes", CustomerType.values());
    }
}
