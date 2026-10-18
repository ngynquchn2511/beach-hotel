package com.hotel.controller;

import com.hotel.entity.UserRole;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.StaffNotificationService;
import com.hotel.service.StaffNotificationService.Audience;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Luong thong bao tuc thoi: le tan (ADMIN / STAFF) va buong phong (HOUSEKEEPER) - SecurityConfig
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final StaffNotificationService notificationService;

    public AdminNotificationController(StaffNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @AuthenticationPrincipal CustomUserDetails user) {
        return notificationService.subscribe(parse(lastEventId), audiences(user), userId(user));
    }

    // Du phong cho trinh duyet khong co EventSource / test: thong bao sau id `after`
    @GetMapping("/recent")
    public List<StaffNotificationService.Notice> recent(@RequestParam(defaultValue = "0") long after,
                                                        @AuthenticationPrincipal CustomUserDetails user) {
        return notificationService.recent(after, audiences(user), userId(user));
    }

    private static Set<Audience> audiences(CustomUserDetails user) {
        return user != null && user.getUser().getRole() == UserRole.HOUSEKEEPER
                ? EnumSet.of(Audience.HOUSEKEEPING) : EnumSet.of(Audience.FRONT_DESK);
    }

    private static Long userId(CustomUserDetails user) {
        return user != null ? user.getUser().getId() : null;
    }

    private static Long parse(String id) {
        try {
            return id == null || id.isBlank() ? null : Long.valueOf(id.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
