package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.HousekeepingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

// Bang don phong cho le tan / buong phong (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/housekeeping")
public class AdminHousekeepingController {

    private final HousekeepingService housekeepingService;

    public AdminHousekeepingController(HousekeepingService housekeepingService) {
        this.housekeepingService = housekeepingService;
    }

    @GetMapping
    public String board(Model model) {
        model.addAttribute("board", housekeepingService.board(LocalDate.now()));
        return "admin/housekeeping/index";
    }

    @PostMapping("/{roomId}/start")
    public String start(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails user,
                        RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.start(roomId, staffName(user)), "Đã nhận dọn phòng", redirectAttributes);
    }

    @PostMapping("/{roomId}/release")
    public String release(@PathVariable Long roomId, RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.release(roomId), "Đã trả phòng về danh sách chờ dọn", redirectAttributes);
    }

    @PostMapping("/{roomId}/finish")
    public String finish(@PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails user,
                         RedirectAttributes redirectAttributes) {
        return run(() -> housekeepingService.finish(roomId, staffName(user)), "Phòng đã sạch, sẵn sàng đón khách",
                redirectAttributes);
    }

    private static String staffName(CustomUserDetails user) {
        return user != null ? user.getUser().getFullName() : null;
    }

    private static String run(Runnable action, String success, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/housekeeping";
    }
}
