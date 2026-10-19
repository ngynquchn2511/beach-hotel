package com.hotel.config;

import com.hotel.service.OtaSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Doc lich Booking.com / Airbnb dinh ky (mac dinh 15 phut / lan; tat bang OTA_SYNC_ENABLED=false)
@Component
public class OtaSyncScheduler {

    private final OtaSyncService syncService;

    @Value("${app.ota-sync.enabled:true}")
    private boolean enabled;

    public OtaSyncScheduler(OtaSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${app.ota-sync.interval-ms:900000}", initialDelayString = "${app.ota-sync.initial-delay-ms:120000}")
    public void run() {
        if (enabled) {
            syncService.syncAll();
        }
    }
}
