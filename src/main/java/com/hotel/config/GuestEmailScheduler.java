package com.hotel.config;

import com.hotel.service.GuestEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Moi gio (phut thu 5) gui email nhac lich nhan phong va moi danh gia sau tra phong.
// Chi gui trong 8h-21h (xem GuestEmailService); tat bang app.mail.guest-emails.enabled=false
@Component
public class GuestEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(GuestEmailScheduler.class);

    private final GuestEmailService guestEmailService;

    public GuestEmailScheduler(GuestEmailService guestEmailService) {
        this.guestEmailService = guestEmailService;
    }

    // 15 phut 1 lan: nhac khach chuyen coc khi don sap tu huy vi qua han coc
    @Scheduled(fixedDelay = 900_000, initialDelay = 120_000)
    public void sendDepositReminders() {
        int sent = guestEmailService.sendDepositReminders(LocalDateTime.now());
        if (sent > 0) {
            log.info("Da gui {} email nhac chuyen coc", sent);
        }
    }

    @Scheduled(cron = "0 5 * * * *")
    public void sendGuestEmails() {
        GuestEmailService.Result result = guestEmailService.sendDueEmails(LocalDateTime.now());
        if (result.reminders() > 0 || result.invitations() > 0) {
            log.info("Da gui {} email nhac lich nhan phong, {} email moi danh gia",
                    result.reminders(), result.invitations());
        }
    }
}
