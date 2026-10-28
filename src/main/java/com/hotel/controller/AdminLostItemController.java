package com.hotel.controller;

import com.hotel.entity.LostItem;
import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.LostItemService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;

// So do that lac (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/lost-found")
public class AdminLostItemController {

    private final LostItemService lostItemService;
    private final RoomRepository roomRepository;

    public AdminLostItemController(LostItemService lostItemService, RoomRepository roomRepository) {
        this.lostItemService = lostItemService;
        this.roomRepository = roomRepository;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        Pager<LostItem> pager = Pager.of(lostItemService.all(), page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("items", pager.items());
        model.addAttribute("rooms", roomRepository.findAll().stream().sorted(Comparator.comparing(Room::getRoomNumber)).toList());
        model.addAttribute("keepDays", LostItemService.KEEP_DAYS);
        model.addAttribute("linkDays", LostItemService.LINK_DAYS);
        return "admin/lost-found/index";
    }

    @PostMapping
    public String record(@RequestParam(required = false) Long roomId, @RequestParam(required = false) Long bookingId,
                         @RequestParam(required = false) String description, @RequestParam(required = false) String storageLocation,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> {
            LostItem item = lostItemService.record(roomId, bookingId, description, storageLocation,
                    user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage", item.getBooking() != null
                    ? "Đã ghi nhận, gắn với đơn #" + item.getBooking().getId() + " (" + item.getBooking().getGuestName() + ")"
                    : "Đã ghi nhận (chưa gắn được đơn khách nào)");
        }, redirectAttributes);
    }

    @PostMapping("/{id}/notify")
    public String notifyGuest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> {
            lostItemService.notifyGuest(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi email báo khách");
        }, redirectAttributes);
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, @RequestParam(required = false) LostItem.Status status,
                        @RequestParam(required = false) String note, RedirectAttributes redirectAttributes) {
        return run(() -> {
            LostItem item = lostItemService.close(id, status, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật: " + item.getStatus().getVietnameseLabel());
        }, redirectAttributes);
    }

    private static String run(Runnable action, RedirectAttributes redirectAttributes) {
        try {
            action.run();
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/lost-found";
    }
}
