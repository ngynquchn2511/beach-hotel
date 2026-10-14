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

        // Bam "Dat tour nay" khi chua dang nhap -> dang nhap xong quay lai dung buoc chon don dat tour
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && "GET".equals(saved.getMethod())
                && java.net.URI.create(saved.getRedirectUrl()).getPath().startsWith("/customer/services/tours/")) {
            requestCache.removeRequest(request, response);
            getRedirectStrategy().sendRedirect(request, response, saved.getRedirectUrl());
            return;
        }
        getRedirectStrategy().sendRedirect(request, response, "/customer/rooms");
    }
}
