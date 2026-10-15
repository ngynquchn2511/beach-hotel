package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.EmailService;
import com.hotel.service.GuestEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Nhac chuyen coc: con <= 3 tieng la het han coc -> gui 1 lan; don moi dat / da coc / con xa han thi khong
class DepositReminderIT extends ItFixtures {

    @MockBean private EmailService emailService;
    @Autowired private GuestEmailService guestEmailService;

    private User guest;
    private Room room;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void seed() {
        when(emailService.sendDepositReminder(any())).thenReturn(true);
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang Nhac IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
    }

    private Booking awaiting(int inDays, LocalDateTime deadline, LocalDateTime createdAt) {
        Booking b = persistBooking(guest, room, BookingStatus.PENDING, inDays, 1);
        b.setDepositAmount(BigDecimal.valueOf(300_000));
        b.setDepositDeadline(deadline);
        b.setCreatedAt(createdAt);
        return bookingRepository.save(b);
    }

    @Test
    void remindsOnce_whenDeadlineWithinThreeHours() {
        Booking due = awaiting(10, now.plusHours(2), now.minusHours(20));
        Booking farAway = awaiting(20, now.plusHours(8), now.minusHours(10));
        Booking justBooked = awaiting(30, now.plusHours(2), now.minusMinutes(20));
        Booking paid = awaiting(40, now.plusHours(1), now.minusHours(5));
        paid.setDepositPaidAt(now.minusMinutes(5));
        bookingRepository.save(paid);

        assertThat(guestEmailService.sendDepositReminders(now)).isEqualTo(1);
        verify(emailService).sendDepositReminder(argThat(b -> b.getId().equals(due.getId())));
        assertThat(bookingRepository.findById(due.getId()).orElseThrow().getDepositReminderSentAt()).isNotNull();
        assertThat(bookingRepository.findById(farAway.getId()).orElseThrow().getDepositReminderSentAt()).isNull();
        assertThat(bookingRepository.findById(justBooked.getId()).orElseThrow().getDepositReminderSentAt()).isNull();

        // Lan chay sau khong nhac lai
        assertThat(guestEmailService.sendDepositReminders(now.plusMinutes(15))).isZero();
    }

    @Test
    void expiredDeadline_notReminded() {
        awaiting(10, now.minusMinutes(1), now.minusHours(30));
        assertThat(guestEmailService.sendDepositReminders(now)).isZero();
        verify(emailService, never()).sendDepositReminder(any());
    }
}
