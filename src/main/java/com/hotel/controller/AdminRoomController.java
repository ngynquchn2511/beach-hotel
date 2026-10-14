package com.hotel.controller;

import com.hotel.dto.RoomRequest;
import com.hotel.entity.RoomStatus;
import com.hotel.exception.BusinessException;
import com.hotel.entity.Room;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ComboService;
import com.hotel.service.RoomService;
import com.hotel.service.RoomTypeService;
import com.hotel.util.PaginationUtil;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/rooms")
public class AdminRoomController {

    private final RoomService roomService;
    private final RoomTypeService roomTypeService;
    private final ComboService comboService;

    public AdminRoomController(RoomService roomService, RoomTypeService roomTypeService, ComboService comboService) {
        this.roomService = roomService;
        this.roomTypeService = roomTypeService;
        this.comboService = comboService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<Room> all = roomService.findAll();
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("rooms", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        return "admin/rooms/list";
    }

    // Xem chi tiet phong kem lich chon ngay (giong ben client) de nhan vien/admin dat phong ngay tai day
    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id,
                        @RequestParam(required = false) LocalDate checkIn,
                        @RequestParam(required = false) LocalDate checkOut,
                        @RequestParam(required = false) Long comboId,
                        Model model) {
        var room = roomService.findById(id);
        model.addAttribute("room", room);
        model.addAttribute("unavailableDates", roomService.findUnavailableDates(id).stream()
                .map(LocalDate::toString)
                .toList());
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("combos", comboService.findActive());
        model.addAttribute("selectedComboId", comboId);

        if (checkIn != null && checkOut != null) {
            try {
                if (checkIn.isBefore(LocalDate.now())) {
                    throw new BusinessException("Ngày nhận phòng không được ở trong quá khứ");
                }
                if (!checkOut.isAfter(checkIn)) {
                    throw new BusinessException("Ngày trả phòng phải lớn hơn ngày nhận phòng");
                }
                long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
                var roomAmount = room.getPrice().multiply(java.math.BigDecimal.valueOf(nights));

                var comboAmount = java.math.BigDecimal.ZERO;
                if (comboId != null) {
                    var combo = comboService.findById(comboId);
                    comboAmount = combo.getPrice();
                    model.addAttribute("selectedCombo", combo);
                }

                var totalAmount = roomAmount.add(comboAmount);
                boolean available = roomService.isAvailableForDates(id, checkIn, checkOut);

                model.addAttribute("nights", nights);
                model.addAttribute("roomAmount", roomAmount);
                model.addAttribute("comboAmount", comboAmount);
                model.addAttribute("totalAmount", totalAmount);
                model.addAttribute("available", available);
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }

        return "admin/rooms/view";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("roomRequest", new RoomRequest());
        model.addAttribute("roomTypes", roomTypeService.findAll());
        model.addAttribute("isEdit", false);
        return "admin/rooms/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("roomRequest") RoomRequest request,
                          BindingResult bindingResult,
                          @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypes", roomTypeService.findAll());
            model.addAttribute("isEdit", false);
            return "admin/rooms/form";
        }
        try {
            roomService.create(request, imageFiles);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("roomTypes", roomTypeService.findAll());
            model.addAttribute("isEdit", false);
            return "admin/rooms/form";
        }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var room = roomService.findById(id);
        RoomRequest request = new RoomRequest();
        request.setRoomNumber(room.getRoomNumber());
        request.setRoomTypeId(room.getRoomType().getId());
        request.setPrice(room.getPrice());
        request.setDescription(room.getDescription());
        request.setDescriptionEn(room.getDescriptionEn());

        model.addAttribute("roomRequest", request);
        model.addAttribute("roomTypes", roomTypeService.findAll());
        model.addAttribute("roomId", id);
        model.addAttribute("currentImages", roomService.findImages(id));
        model.addAttribute("isEdit", true);
        return "admin/rooms/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("roomRequest") RoomRequest request,
                          BindingResult bindingResult,
                          @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
                          @AuthenticationPrincipal CustomUserDetails currentUser,
                          Model model) {
        // Chi ADMIN duoc doi gia - STAFF gui len gia nao cung giu nguyen gia cu
        if (!currentUser.isAdmin()) {
            request.setPrice(roomService.findById(id).getPrice());
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypes", roomTypeService.findAll());
            model.addAttribute("roomId", id);
            model.addAttribute("currentImages", roomService.findImages(id));
            model.addAttribute("isEdit", true);
            return "admin/rooms/form";
        }
        try {
            roomService.update(id, request, imageFiles);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("roomTypes", roomTypeService.findAll());
            model.addAttribute("roomId", id);
            model.addAttribute("currentImages", roomService.findImages(id));
            model.addAttribute("isEdit", true);
            return "admin/rooms/form";
        }
        return "redirect:/admin/rooms/{id}/edit";
    }

    @PostMapping("/{roomId}/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long roomId, @PathVariable Long imageId,
                               RedirectAttributes redirectAttributes) {
        try {
            roomService.deleteImage(roomId, imageId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms/{roomId}/edit";
    }

    @PostMapping("/{roomId}/images/{imageId}/replace")
    public String replaceImage(@PathVariable Long roomId, @PathVariable Long imageId,
                                @RequestParam("newImageFile") MultipartFile newImageFile,
                                RedirectAttributes redirectAttributes) {
        try {
            roomService.replaceImage(roomId, imageId, newImageFile);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms/{roomId}/edit";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam RoomStatus status) {
        roomService.changeStatus(id, status);
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/mark-ready")
    public String markReady(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomService.markReady(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomService.delete(id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/rooms";
    }
}
