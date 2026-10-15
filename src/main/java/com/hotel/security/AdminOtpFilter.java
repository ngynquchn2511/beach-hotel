package com.hotel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Phien dang cho ma OTP (moi dung mat khau) chi vao duoc trang nhap ma va dang xuat
public class AdminOtpFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean allowed = path.equals("/admin/otp") || path.equals("/admin/otp/resend") || path.equals("/admin/logout")
                || path.equals("/admin") || path.equals("/admin/login");
        if (!allowed && AdminOtpService.isPending(request.getSession(false))) {
            response.sendRedirect(request.getContextPath() + "/admin/otp");
            return;
        }
        chain.doFilter(request, response);
    }
}
