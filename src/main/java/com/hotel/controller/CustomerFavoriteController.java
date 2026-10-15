package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.FavoriteRoomService;
import com.hotel.service.UserMessages;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Phong yeu thich (can dang nhap tai khoan khach - /customer/** da chan o SecurityConfig)
@Controller
public class CustomerFavoriteController {

    private final FavoriteRoomService favoriteRoomService;
    private final UserMessages messages;

    public CustomerFavoriteController(FavoriteRoomService favoriteRoomService, UserMessages messages) {
        this.favoriteRoomService = favoriteRoomService;
        this.messages = messages;
    }

    // So the phong moi trang (luoi 3 cot)
    static final int ROOMS_PER_PAGE = 9;

    @GetMapping("/customer/favorites")
    public String list(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        Pager<Room> pager = Pager.of(favoriteRoomService.rooms(userDetails.getUser().getId()), page, ROOMS_PER_PAGE);
        model.addAttribute("pager", pager);
        model.addAttribute("rooms", pager.items());
        return "customer/favorites";
    }

    // back: trang de quay lai sau khi bam (chi nhan duong dan noi bo, chong chuyen huong ra trang ngoai)
    @PostMapping("/customer/favorites/{roomId}/toggle")
    public String toggle(@PathVariable Long roomId,
                         @RequestParam(required = false) String back,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        try {
            boolean added = favoriteRoomService.toggle(userDetails.getUser().getId(), roomId);
            redirectAttributes.addFlashAttribute("favoriteMessage",
                    messages.get(added ? "favorite.added" : "favorite.removed"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", messages.of(ex));
        }
        return "redirect:" + safeBack(back);
    }

    static String safeBack(String back) {
        if (back == null || !back.startsWith("/") || back.startsWith("//") || back.contains("\\")
                || back.contains("\r") || back.contains("\n")) {
            return "/customer/favorites";
        }
        return back;
    }
}
