package com.hotel.controller;

import com.hotel.entity.RoomServiceItem;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.RoomServiceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

// Le tan xu ly khach goi mon len phong (ADMIN + STAFF); sua thuc don chi ADMIN (SecurityConfig)
@Controller
@RequestMapping("/admin/room-service")
public class AdminRoomServiceController {

    private final RoomServiceService roomServiceService;

    public AdminRoomServiceController(RoomServiceService roomServiceService) {
        this.roomServiceService = roomServiceService;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "1") int pendingPage, @RequestParam(defaultValue = "1") int deliveredPage,
                       @RequestParam(defaultValue = "1") int menuPage, Model model) {
        var pending = roomServiceService.pending();
        var delivered = roomServiceService.deliveredToday();
        var items = roomServiceService.allItems();
        model.addAttribute("pendingOrders", pending);
        model.addAttribute("deliveredToday", delivered);
        model.addAttribute("menuItems", items);
        // 3 danh sach tren cung trang -> moi danh sach 1 tham so trang rieng
        model.addAttribute("pendingPager", com.hotel.util.Pager.of(pending, pendingPage, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE, "pendingPage"));
        model.addAttribute("deliveredPager", com.hotel.util.Pager.of(delivered, deliveredPage, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE, "deliveredPage"));
        model.addAttribute("menuPager", com.hotel.util.Pager.of(items, menuPage, com.hotel.util.PaginationUtil.DEFAULT_PAGE_SIZE, "menuPage"));
        model.addAttribute("categories", RoomServiceItem.Category.values());
        return "admin/room-service/index";
    }

    @PostMapping("/orders/{id}/deliver")
    public String deliver(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> roomServiceService.deliver(id, staffName(user)), "Đã giao, tiền đã cộng vào hóa đơn của khách", redirectAttributes);
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        return run(() -> roomServiceService.cancelByStaff(id, staffName(user)), "Đã hủy yêu cầu", redirectAttributes);
    }

    @PostMapping("/menu")
    public String createItem(@RequestParam(required = false) String name, @RequestParam(required = false) String nameEn,
                             @RequestParam(required = false) RoomServiceItem.Category category,
                             @RequestParam(required = false) BigDecimal price, RedirectAttributes redirectAttributes) {
        return run(() -> roomServiceService.createItem(name, nameEn, category, price), "Đã thêm món vào thực đơn", redirectAttributes)
                + "#thuc-don";
    }

    @PostMapping("/menu/{id}/toggle")
    public String toggleItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> roomServiceService.toggleItem(id), "Đã cập nhật món", redirectAttributes) + "#thuc-don";
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
        return "redirect:/admin/room-service";
    }
}
