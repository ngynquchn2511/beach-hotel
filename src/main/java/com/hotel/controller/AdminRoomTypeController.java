package com.hotel.controller;

import com.hotel.dto.RoomTypeRequest;
import com.hotel.entity.RoomType;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.RoomTypeService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/room-types")
public class AdminRoomTypeController {

    private final RoomTypeService roomTypeService;

    public AdminRoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<RoomType> all = roomTypeService.findAll();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("roomTypes", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "admin/room-types/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("roomTypeRequest", new RoomTypeRequest());
        model.addAttribute("isEdit", false);
        return "admin/room-types/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("roomTypeRequest") RoomTypeRequest request,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/room-types/form";
        }
        roomTypeService.create(request);
        return "redirect:/admin/room-types";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var roomType = roomTypeService.findById(id);
        RoomTypeRequest request = new RoomTypeRequest();
        request.setName(roomType.getName());
        request.setBasePrice(roomType.getBasePrice());
        request.setMaxGuests(roomType.getMaxGuests());
        request.setArea(roomType.getArea());
        request.setDescription(roomType.getDescription());
        request.setAmenities(roomType.getAmenities());
        request.setNameEn(roomType.getNameEn());
        request.setDescriptionEn(roomType.getDescriptionEn());
        request.setAmenitiesEn(roomType.getAmenitiesEn());

        model.addAttribute("roomTypeRequest", request);
        model.addAttribute("roomTypeId", id);
        model.addAttribute("isEdit", true);
        return "admin/room-types/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("roomTypeRequest") RoomTypeRequest request,
                          BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        // Chi ADMIN duoc doi gia - STAFF gui len gia nao cung giu nguyen gia cu
        if (!currentUser.isAdmin()) {
            request.setBasePrice(roomTypeService.findById(id).getBasePrice());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypeId", id);
            model.addAttribute("isEdit", true);
            return "admin/room-types/form";
        }
        roomTypeService.update(id, request);
        return "redirect:/admin/room-types";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomTypeService.delete(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/room-types";
    }
}
