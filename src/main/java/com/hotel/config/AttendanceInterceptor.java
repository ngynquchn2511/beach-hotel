package com.hotel.config;

import com.hotel.security.CustomUserDetails;
import com.hotel.service.StaffShiftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

// Cham cong tu dong: nhan vien noi bo mo / thao tac tren cong quan tri -> ghi gio vao (lan dau trong ngay) va day gio ra.
// Moi phien chi ghi CSDL toi da 5 phut / lan. Loi cham cong khong lam hong thao tac chinh
public class AttendanceInterceptor implements HandlerInterceptor {

    static final String SESSION_KEY = "attendance.lastTouch";
    static final long MIN_INTERVAL_MS = 5 * 60 * 1000;
    private static final Logger log = LoggerFactory.getLogger(AttendanceInterceptor.class);

    private final ObjectProvider<StaffShiftService> service;

    public AttendanceInterceptor(ObjectProvider<StaffShiftService> service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails user) || !user.getUser().getRole().isBackOffice()) {
            return true;
        }
        HttpSession session = request.getSession();
        long nowMs = System.currentTimeMillis();
        Object last = session.getAttribute(SESSION_KEY);
        if (last instanceof Long l && nowMs - l < MIN_INTERVAL_MS) {
            return true;
        }
        StaffShiftService s = service.getIfAvailable();
        if (s == null) {
            return true;
        }
        try {
            s.touch(user.getUser().getId(), LocalDateTime.now());
            session.setAttribute(SESSION_KEY, nowMs);
        } catch (RuntimeException ex) {
            // 2 yeu cau cung luc tao ban ghi (trung khoa) -> lan sau ghi tiep
            log.debug("Cham cong loi: {}", ex.toString());
        }
        return true;
    }
}
