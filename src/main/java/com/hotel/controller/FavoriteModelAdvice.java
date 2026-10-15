package com.hotel.controller;

import com.hotel.entity.UserRole;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.FavoriteRoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

// Trang co nut trai tim "Yeu thich": bom san danh sach phong khach da thich + dia chi trang hien tai (de quay lai sau khi bam)
@ControllerAdvice(assignableTypes = {RoomController.class, CustomerController.class, CustomerFavoriteController.class})
public class FavoriteModelAdvice {

    private final FavoriteRoomService favoriteRoomService;

    public FavoriteModelAdvice(FavoriteRoomService favoriteRoomService) {
        this.favoriteRoomService = favoriteRoomService;
    }

    @ModelAttribute("favoriteRoomIds")
    public Set<Long> favoriteRoomIds(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser().getRole() != UserRole.CUSTOMER) {
            return Set.of();
        }
        return favoriteRoomService.roomIds(userDetails.getUser().getId());
    }

    @ModelAttribute("currentUrl")
    public String currentUrl(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return request.getQueryString() == null ? path : path + "?" + request.getQueryString();
    }
}
