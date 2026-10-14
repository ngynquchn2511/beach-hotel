package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Chon dung don de gui email nhac lich / moi danh gia, khong gui trung, khong gui ban dem
class GuestEmailServiceTest {

    private BookingRepository bookingRepository;
    private ReviewRepository reviewRepository;
    private EmailService emailService;
    private GuestEmailService service;

    // 10h sang ngay mai - trong khung gio gui
    private static final LocalDateTime NOW = LocalDate.now().plusDays(1).atTime(10, 0);

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        emailService = mock(EmailService.class);
        service = new GuestEmailService(bookingRepository, reviewRepository, emailService, true);
        when(emailService.sendCheckInReminder(any())).thenReturn(true);
        when(emailService.sendReviewInvitation(any())).thenReturn(true);
    }

    private static Booking booking(BookingStatus status, LocalDateTime checkIn, LocalDateTime checkOut, LocalDateTime createdAt) {
        return Booking.builder().id(1L).status(status)
                .checkInDate(checkIn.toLocalDate()).checkInTime(checkIn.toLocalTime())
                .checkOutDate(checkOut.toLocalDate()).checkOutTime(checkOut.toLocalTime())
                .totalAmount(BigDecimal.valueOf(1000000)).createdAt(createdAt).build();
    }

    private void reminderCandidates(Booking... bookings) {
        when(bookingRepository.findByStatusAndReminderSentAtIsNullAndCheckInDateBetween(
                BookingStatus.CONFIRMED, NOW.toLocalDate(), NOW.plusHours(24).toLocalDate())).thenReturn(List.of(bookings));
    }

    private void inviteCandidates(Booking... bookings) {
        when(bookingRepository.findByStatusAndReviewInviteSentAtIsNullAndCheckOutDateBetween(
                BookingStatus.CHECKED_OUT, NOW.toLocalDate().minusDays(7), NOW.toLocalDate())).thenReturn(List.of(bookings));
    }

    @Test
    void reminder_checkInWithin24h_bookedEarlier_sentAndMarked() {
        Booking b = booking(BookingStatus.CONFIRMED, NOW.plusHours(20), NOW.plusDays(2), NOW.minusDays(5));
        reminderCandidates(b);

        assertThat(service.sendDueEmails(NOW).reminders()).isEqualTo(1);
        assertThat(b.getReminderSentAt()).isEqualTo(NOW);
        verify(bookingRepository).save(b);
    }

    @Test
    void reminder_checkInMoreThan24hAway_skipped() {
        // Ngay mai nhung 14h -> con 28h
        Booking b = booking(BookingStatus.CONFIRMED, NOW.plusHours(28), NOW.plusDays(3), NOW.minusDays(5));
        reminderCandidates(b);

        assertThat(service.sendDueEmails(NOW).reminders()).isZero();
        verify(emailService, never()).sendCheckInReminder(any());
    }

    @Test
    void reminder_bookedWithinLast24hBeforeCheckIn_skipped() {
        Booking b = booking(BookingStatus.CONFIRMED, NOW.plusHours(20), NOW.plusDays(2), NOW.minusHours(2));
        reminderCandidates(b);

        assertThat(service.sendDueEmails(NOW).reminders()).isZero();
        assertThat(b.getReminderSentAt()).isNull();
    }

    @Test
    void reminder_emailNotQueued_notMarkedSoItRetries() {
        when(emailService.sendCheckInReminder(any())).thenReturn(false);
        Booking b = booking(BookingStatus.CONFIRMED, NOW.plusHours(20), NOW.plusDays(2), NOW.minusDays(5));
        reminderCandidates(b);

        assertThat(service.sendDueEmails(NOW).reminders()).isZero();
        assertThat(b.getReminderSentAt()).isNull();
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void invitation_threeHoursAfterCheckOut_sentAndMarked() {
        Booking b = booking(BookingStatus.CHECKED_OUT, NOW.minusDays(2), NOW.minusHours(3), NOW.minusDays(10));
        inviteCandidates(b);

        assertThat(service.sendDueEmails(NOW).invitations()).isEqualTo(1);
        assertThat(b.getReviewInviteSentAt()).isEqualTo(NOW);
    }

    @Test
    void invitation_tooSoonOrAlreadyReviewed_skipped() {
        Booking soon = booking(BookingStatus.CHECKED_OUT, NOW.minusDays(2), NOW.minusHours(1), NOW.minusDays(10));
        Booking reviewed = booking(BookingStatus.CHECKED_OUT, NOW.minusDays(3), NOW.minusDays(1), NOW.minusDays(10));
        reviewed.setId(2L);
        when(reviewRepository.existsByBookingId(2L)).thenReturn(true);
        inviteCandidates(soon, reviewed);

        assertThat(service.sendDueEmails(NOW).invitations()).isZero();
        verify(emailService, never()).sendReviewInvitation(any());
    }

    @Test
    void outsideSendingHours_orDisabled_nothingHappens() {
        assertThat(service.sendDueEmails(NOW.withHour(22))).isEqualTo(new GuestEmailService.Result(0, 0));
        assertThat(service.sendDueEmails(NOW.with(LocalTime.of(7, 59)))).isEqualTo(new GuestEmailService.Result(0, 0));

        GuestEmailService disabled = new GuestEmailService(bookingRepository, reviewRepository, emailService, false);
        assertThat(disabled.sendDueEmails(NOW)).isEqualTo(new GuestEmailService.Result(0, 0));
        verifyNoInteractions(bookingRepository);
    }
}
