package com.hotel.security;

import com.hotel.entity.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

// Cong /login chi danh cho CUSTOMER, khong cho STAFF/ADMIN dang nhap vao day
public class CustomerPortalSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UserRole role = userDetails.getUser().getRole();

        if (role != UserRole.CUSTOMER) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            SecurityContextHolder.clearContext();
            response.sendRedirect("/login?error=wrong_portal");
            return;
        }

        // Khach vang lai mo chuc nang can dang nhap (dat phong, link xem don trong email, mua the qua tang...)
        // -> dang nhap xong quay lai dung trang dang do
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && "GET".equals(saved.getMethod()) && isResumable(saved)) {
            requestCache.removeRequest(request, response);
            getRedirectStrategy().sendRedirect(request, response, saved.getRedirectUrl());
            return;
        }
        getRedirectStrategy().sendRedirect(request, response, "/customer/rooms");
    }

    public static boolean isResumable(SavedRequest saved) {
        String path = java.net.URI.create(saved.getRedirectUrl()).getPath();
        return path.startsWith("/customer/") || path.startsWith("/gift-vouchers");
    }

    // Trang dat phong moi (1 phong / nhieu phong) - khach vang lai bi chuyen ve /login khi mo
    public static boolean isBookingPath(String path) {
        return "/customer/bookings/new".equals(path) || "/customer/bookings/group/new".equals(path);
    }
}
