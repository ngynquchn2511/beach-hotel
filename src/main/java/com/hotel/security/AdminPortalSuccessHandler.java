package com.hotel.security;

import com.hotel.entity.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import java.io.IOException;

// Cong /admin chi danh cho STAFF va ADMIN, khong cho CUSTOMER dang nhap vao day
public class AdminPortalSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UserRole role = userDetails.getUser().getRole();

        if (role != UserRole.ADMIN && role != UserRole.STAFF) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            SecurityContextHolder.clearContext();
            response.sendRedirect("/admin?error=wrong_portal");
            return;
        }

        // STAFF gio co day du quyen quan tri nhu ADMIN (tru khu vuc quan ly nhan vien), nen dua vao chung 1 dashboard
        getRedirectStrategy().sendRedirect(request, response, "/admin/dashboard");
    }
}
