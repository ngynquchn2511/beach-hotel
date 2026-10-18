package com.hotel.config;

import com.hotel.entity.Booking;
import com.hotel.service.BookingService;
import com.hotel.service.StaffNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// 23h hang ngay: con khach da qua gio nhan phong ma chua den -> nhac le tan goi khach hoac danh dau khong den
@Component
public class NoShowAlertScheduler {

    private final BookingService bookingService;
    private final StaffNotificationService notificationService;

    public NoShowAlertScheduler(BookingService bookingService, StaffNotificationService notificationService) {
        this.bookingService = bookingService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.no-show.alert-cron:0 0 23 * * *}")
    public void remind() {
        List<Booking> late = bookingService.overdueArrivals(LocalDateTime.now());
        if (late.isEmpty()) {
            return;
        }
        String rooms = late.stream().limit(6).map(b -> b.getRoom().getRoomNumber()).collect(Collectors.joining(", "));
        notificationService.notify("no-show", StaffNotificationService.Level.WARNING,
                late.size() + " khách đã quá giờ nhận phòng mà chưa đến",
                "Phòng " + rooms + (late.size() > 6 ? "…" : "") + " - gọi khách hoặc đánh dấu không đến", "/admin/today");
    }
}
