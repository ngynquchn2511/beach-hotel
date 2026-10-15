package com.hotel.config;

import com.hotel.service.RoomAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 10 phut 1 lan: phong khach dang cho ("Bao toi khi phong trong lai") trong lai thi gui email bao
@Component
public class RoomAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomAlertScheduler.class);

    private final RoomAlertService roomAlertService;

    public RoomAlertScheduler(RoomAlertService roomAlertService) {
        this.roomAlertService = roomAlertService;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void notifyAvailableRooms() {
        int sent = roomAlertService.notifyAvailable(LocalDate.now());
        if (sent > 0) {
            log.info("Da gui {} email bao phong trong lai", sent);
        }
    }
}
