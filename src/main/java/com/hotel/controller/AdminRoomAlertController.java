package com.hotel.controller;

import com.hotel.entity.RoomAlert;
import com.hotel.exception.BusinessException;
import com.hotel.service.RoomAlertService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

// "Khach dang cho phong": yeu cau "Bao toi khi phong trong lai" chua duoc bao - biet phong nao dang duoc san (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/room-alerts")
public class AdminRoomAlertController {

    private final RoomAlertService roomAlertService;

    public AdminRoomAlertController(RoomAlertService roomAlertService) {
        this.roomAlertService = roomAlertService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        List<RoomAlert> alerts = roomAlertService.pending(LocalDate.now());
        Pager<RoomAlert> pager = Pager.of(alerts, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("demand", RoomAlertService.demand(alerts));
        model.addAttribute("pager", pager);
        model.addAttribute("alerts", pager.items());
        return "admin/room-alerts/index";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            roomAlertService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa yêu cầu");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/room-alerts";
    }
}
