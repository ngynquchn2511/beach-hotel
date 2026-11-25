package com.hotel.config;

import com.hotel.service.HousekeepingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Moi phut: phong cho don chua co nguoi (hoac nguoi duoc giao da het ca) -> giao cho nguoi dang trong ca;
// yeu cau kiem phong truoc khi tra qua 3 phut chua ai nhan -> chuyen nguoi khac / bao le tan tu kiem
@Component
public class HousekeepingScheduler {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingScheduler.class);

    private final HousekeepingService housekeepingService;

    public HousekeepingScheduler(HousekeepingService housekeepingService) {
        this.housekeepingService = housekeepingService;
    }

    @Scheduled(fixedDelayString = "${app.housekeeping.dispatch-ms:60000}", initialDelayString = "${app.housekeeping.dispatch-initial-ms:60000}")
    public void dispatch() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int moved = housekeepingService.escalateRoomChecks(now);
            int assigned = housekeepingService.dispatchRooms(now, null);
            if (moved + assigned > 0) {
                log.info("Buong phong: tu giao {} phong, chuyen {} yeu cau kiem phong", assigned, moved);
            }
        } catch (RuntimeException ex) {
            log.warn("Tu phan viec buong phong loi: {}", ex.getMessage());
        }
    }
}
