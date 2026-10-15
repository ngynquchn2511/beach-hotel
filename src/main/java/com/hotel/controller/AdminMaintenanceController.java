package com.hotel.controller;

import com.hotel.entity.MaintenanceTicket;
import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.repository.RoomRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.MaintenanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;

// Bao hong / bao tri phong (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/maintenance")
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final RoomRepository roomRepository;

    public AdminMaintenanceController(MaintenanceService maintenanceService, RoomRepository roomRepository) {
        this.maintenanceService = maintenanceService;
        this.roomRepository = roomRepository;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Long roomId, Model model) {
        model.addAttribute("tickets", maintenanceService.open());
        model.addAttribute("resolved", maintenanceService.resolvedRecently());
        model.addAttribute("atRisk", maintenanceService.arrivalsInBlockedRooms(LocalDate.now()));
        model.addAttribute("rooms", roomRepository.findAll().stream().sorted(Comparator.comparing(Room::getRoomNumber)).toList());
        model.addAttribute("priorities", MaintenanceTicket.Priority.values());
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("warnDays", MaintenanceService.WARN_DAYS);
        return "admin/maintenance/index";
    }

    @PostMapping
    public String report(@RequestParam(required = false) Long roomId, @RequestParam(required = false) String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) MaintenanceTicket.Priority priority,
                         @RequestParam(defaultValue = "false") boolean blocksRoom,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDoneDate,
                         @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        if (roomId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn phòng");
            return "redirect:/admin/maintenance";
        }
        return run(() -> maintenanceService.report(roomId, title, description, priority, blocksRoom, expectedDoneDate, name(user)),
                blocksRoom ? "Đã ghi phiếu và khóa phòng (Bảo trì)" : "Đã ghi phiếu báo hỏng", redirectAttributes);
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> maintenanceService.start(id, name(user)), "Đã nhận sửa", redirectAttributes);
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id, @RequestParam(required = false) String note,
                          @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> maintenanceService.resolve(id, note, name(user)),
                "Đã sửa xong. Phòng khóa để sửa sẽ chuyển sang Chờ dọn", redirectAttributes);
    }

    private static String name(CustomUserDetails user) {
        return user != null ? user.getUser().getFullName() : null;
    }

    private static String run(Runnable action, String success, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/maintenance";
    }
}
