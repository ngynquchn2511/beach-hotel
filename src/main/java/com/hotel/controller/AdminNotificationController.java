package com.hotel.controller;

import com.hotel.service.StaffNotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// Luong thong bao tuc thoi cho le tan (ADMIN + STAFF theo SecurityConfig /admin/**)
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final StaffNotificationService notificationService;

    public AdminNotificationController(StaffNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return notificationService.subscribe(parse(lastEventId));
    }

    // Du phong cho trinh duyet khong co EventSource / test: thong bao sau id `after`
    @GetMapping("/recent")
    public List<StaffNotificationService.Notice> recent(@RequestParam(defaultValue = "0") long after) {
        return notificationService.recent(after);
    }

    private static Long parse(String id) {
        try {
            return id == null || id.isBlank() ? null : Long.valueOf(id.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
