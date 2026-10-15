package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

// Email tu dong cho khach: nhac lich truoc ngay nhan phong va moi danh gia sau khi tra phong.
// GuestEmailScheduler goi moi gio; moi don chi gui 1 lan (ghi reminderSentAt / reviewInviteSentAt).
@Service
public class GuestEmailService {

    // Nhac lich khi con <= 24h toi gio nhan phong
    public static final int REMINDER_HOURS_BEFORE = 24;
    // Moi danh gia sau gio tra phong it nhat 3h (khach da ve toi noi), toi da 7 ngay (qua lau thi thoi)
    public static final int INVITE_HOURS_AFTER = 3;
    public static final int INVITE_MAX_DAYS_AFTER = 7;
    // Khong gui email vao ban dem
    public static final LocalTime SEND_FROM = LocalTime.of(8, 0);
    public static final LocalTime SEND_UNTIL = LocalTime.of(21, 0);

    public record Result(int reminders, int invitations) {
    }

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final EmailService emailService;
    private final boolean enabled;

    public GuestEmailService(BookingRepository bookingRepository, ReviewRepository reviewRepository,
                             EmailService emailService,
                             @Value("${app.mail.guest-emails.enabled:true}") boolean enabled) {
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.emailService = emailService;
        this.enabled = enabled;
    }

    @Transactional
    public Result sendDueEmails(LocalDateTime now) {
        if (!enabled || now.toLocalTime().isBefore(SEND_FROM) || !now.toLocalTime().isBefore(SEND_UNTIL)) {
            return new Result(0, 0);
        }
        return new Result(sendReminders(now), sendReviewInvitations(now));
    }

    // Nhac chuyen coc: con <= DEPOSIT_REMIND_HOURS gio la het han (don tu huy). Don vua dat duoi 1 tieng thi khong nhac.
    // Khong gioi han gio gui (han coc co the roi vao buoi toi). Tra ve so email da gui
    public static final int DEPOSIT_REMIND_HOURS = 3;

    @Transactional
    public int sendDepositReminders(LocalDateTime now) {
        if (!enabled) {
            return 0;
        }
        int sent = 0;
        for (Booking b : bookingRepository.findDepositDueSoon(now, now.plusHours(DEPOSIT_REMIND_HOURS))) {
            if (b.getCreatedAt() != null && b.getCreatedAt().isAfter(now.minusHours(1))) {
                continue;
            }
            if (emailService.sendDepositReminder(b)) {
                b.setDepositReminderSentAt(now);
                bookingRepository.save(b);
                sent++;
            }
        }
        return sent;
    }

    private int sendReminders(LocalDateTime now) {
        int sent = 0;
        LocalDateTime windowEnd = now.plusHours(REMINDER_HOURS_BEFORE);
        for (Booking b : bookingRepository.findByStatusAndReminderSentAtIsNullAndCheckInDateBetween(
                BookingStatus.CONFIRMED, now.toLocalDate(), windowEnd.toLocalDate())) {
            LocalDateTime checkInAt = LocalDateTime.of(b.getCheckInDate(), b.getCheckInTime());
            if (!checkInAt.isAfter(now) || checkInAt.isAfter(windowEnd)) {
                continue;
            }
            // Dat sat ngay (trong vong 24h truoc gio nhan) thi email xac nhan vua nhan da du thong tin
            if (b.getCreatedAt() != null && b.getCreatedAt().isAfter(checkInAt.minusHours(REMINDER_HOURS_BEFORE))) {
                continue;
            }
            if (emailService.sendCheckInReminder(b)) {
                b.setReminderSentAt(now);
                bookingRepository.save(b);
                sent++;
            }
        }
        return sent;
    }

    private int sendReviewInvitations(LocalDateTime now) {
        int sent = 0;
        for (Booking b : bookingRepository.findByStatusAndReviewInviteSentAtIsNullAndCheckOutDateBetween(
                BookingStatus.CHECKED_OUT, now.toLocalDate().minusDays(INVITE_MAX_DAYS_AFTER), now.toLocalDate())) {
            LocalDateTime checkOutAt = LocalDateTime.of(b.getCheckOutDate(), b.getCheckOutTime());
            if (now.isBefore(checkOutAt.plusHours(INVITE_HOURS_AFTER))
                    || now.isAfter(checkOutAt.plusDays(INVITE_MAX_DAYS_AFTER))) {
                continue;
            }
            // Khach tu danh gia roi thi khong can moi
            if (reviewRepository.existsByBookingId(b.getId())) {
                continue;
            }
            if (emailService.sendReviewInvitation(b)) {
                b.setReviewInviteSentAt(now);
                bookingRepository.save(b);
                sent++;
            }
        }
        return sent;
    }
}
