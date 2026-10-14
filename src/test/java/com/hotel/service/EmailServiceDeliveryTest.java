package com.hotel.service;

import com.hotel.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Email xac nhan don chi duoc gui SAU KHI giao dich commit (khong giu khoa phong, khong gui cho don bi rollback)
class EmailServiceDeliveryTest {

    private JavaMailSender mailSender;
    private final List<Runnable> queued = new ArrayList<>();
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // Executor gia: chi xep hang, test tu chay -> kiem soat duoc thoi diem gui
        emailService = new EmailService(mailSender, queued::add, "homestay@test.com", "secret");
        ReflectionTestUtils.setField(emailService, "bankId", "MB");
        ReflectionTestUtils.setField(emailService, "accountNo", "0000000000");
        ReflectionTestUtils.setField(emailService, "accountName", "SONG XANH RESORT");
        ReflectionTestUtils.setField(emailService, "baseUrl", "");
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Booking booking() {
        RoomType type = RoomType.builder().id(1L).name("Phong doi").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build();
        Room room = Room.builder().id(1L).roomNumber("101").roomType(type).price(BigDecimal.valueOf(500000))
                .status(RoomStatus.AVAILABLE).build();
        User customer = User.builder().id(1L).fullName("Khach").email("khach@mail.com").role(UserRole.CUSTOMER).build();
        return Booking.builder().id(7L).customer(customer).room(room)
                .checkInDate(LocalDate.now().plusDays(3)).checkOutDate(LocalDate.now().plusDays(4))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khach").guestPhone("0900000000").guestEmail("khach@mail.com")
                .totalAmount(BigDecimal.valueOf(500000)).discountAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now()).status(BookingStatus.PENDING).build();
    }

    private void finishTransaction(int status) {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            syncs.forEach(TransactionSynchronization::afterCommit);
        }
        syncs.forEach(s -> s.afterCompletion(status));
    }

    @Test
    void insideTransaction_mailIsSentOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        emailService.sendBookingConfirmation(booking());
        assertEquals(0, queued.size(), "Chua commit thi chua duoc xep hang gui");

        finishTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertEquals(1, queued.size());
        verify(mailSender, never()).send(any(SimpleMailMessage.class)); // gui o luong rieng, khong chan luong dat phong

        queued.get(0).run();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void transactionRolledBack_noMailSent() {
        TransactionSynchronizationManager.initSynchronization();

        emailService.sendBookingConfirmation(booking());
        finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertEquals(0, queued.size());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void noTransaction_mailQueuedImmediately() {
        emailService.sendBookingConfirmation(booking());

        assertEquals(1, queued.size());
        queued.get(0).run();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailure_isLoggedNotThrown() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        emailService.sendBookingConfirmation(booking());
        queued.get(0).run(); // khong nem loi ra ngoai
    }
}
