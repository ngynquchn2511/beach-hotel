package com.hotel.config;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Tu dong huy don dat phong con o trang thai PENDING (chua duoc nhan vien/admin xac nhan) khi:
// - da can qua it hon 6 tieng so voi gio nhan phong (dat phong that bai), hoac
// - don yeu cau dat coc ma qua han chuyen coc van chua nhan duoc tien (giai phong phong cho khach khac)
@Component
public class BookingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationScheduler.class);
    private static final int CONFIRM_DEADLINE_HOURS = 6;

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    public BookingExpirationScheduler(BookingRepository bookingRepository, EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.emailService = emailService;
    }

    // Chay moi 5 phut de kiem tra
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expireUnconfirmedBookings() {
        List<Booking> pendingBookings = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;
        int depositExpiredCount = 0;

        for (Booking booking : pendingBookings) {
            LocalDateTime checkInDateTime = LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
            LocalDateTime confirmDeadline = checkInDateTime.minusHours(CONFIRM_DEADLINE_HOURS);
            boolean depositOverdue = booking.isAwaitingDeposit()
                    && booking.getDepositDeadline() != null && now.isAfter(booking.getDepositDeadline());

            if (now.isAfter(confirmDeadline) || depositOverdue) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
                if (depositOverdue) {
                    // Bao cho khach biet don bi huy vi chua chuyen coc (khach co the dat lai)
                    emailService.sendBookingConfirmation(booking);
                    depositExpiredCount++;
                } else {
                    expiredCount++;
                }
            }
        }

        if (expiredCount > 0) {
            log.info("Da tu dong huy {} don dat phong vi khong duoc xac nhan truoc {} tieng so voi gio nhan phong",
                    expiredCount, CONFIRM_DEADLINE_HOURS);
        }
        if (depositExpiredCount > 0) {
            log.info("Da tu dong huy {} don dat phong vi qua han chuyen coc", depositExpiredCount);
        }
    }
}
