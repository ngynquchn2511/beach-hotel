package com.hotel.integration;

import com.hotel.config.NoShowAlertScheduler;
import com.hotel.entity.*;
import com.hotel.service.BookingService;
import com.hotel.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach khong den: dong don giu coc, tra phong de ban lai, canh bao o don sau cua khach, nhac le tan cuoi ngay
class NoShowIT extends ItFixtures {

    @Autowired private BookingService bookingService;
    @Autowired private NoShowAlertScheduler scheduler;
    @Autowired private StaffNotificationService notificationService;

    private User staff;
    private User guest;
    private Room room;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        guest.setLoyaltyPoints(0);
        userRepository.save(guest);
        room = persistRoom(persistRoomType("Hang NS IT", 800_000, 2), 800_000, RoomStatus.AVAILABLE);
    }

    // Don da xac nhan, da coc 300k, nhan phong tu hom qua
    private Booking lateBooking() {
        Booking b = persistBooking(guest, room, BookingStatus.CONFIRMED, -1, 2);
        b.setDepositAmount(BigDecimal.valueOf(300_000));
        b.setDepositPaidAt(LocalDateTime.now().minusDays(5));
        return bookingRepository.save(b);
    }

    @Test
    void markNoShow_keepsDeposit_freesRoom_warnsOnNextBooking() throws Exception {
        Booking late = lateBooking();
        mockMvc.perform(get("/admin/today").with(as(staff)))
                .andExpect(content().string(containsString("Khách không đến</button>")));
        mockMvc.perform(post("/admin/bookings/{id}/no-show", late.getId()).with(csrf()).with(as(staff)).param("back", "today"))
                .andExpect(redirectedUrl("/admin/today"))
                .andExpect(flash().attribute("successMessage", containsString("Giữ cọc 300.000 VND")));

        Booking closed = bookingRepository.findById(late.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(closed.isNoShow()).isTrue();
        assertThat(closed.getNoShowAt()).isNotNull();
        assertThat(closed.getRefundPercent()).isZero();
        assertThat(closed.isRefundPending()).isFalse();
        // Phong trong lai cho ngay do
        assertThat(bookingService.checkAvailability(room, closed.getCheckInDate(), closed.getCheckOutDate(), null).conflicts()).isEmpty();

        // Khach xem don: thong bao mat coc do khong den
        mockMvc.perform(get("/customer/bookings/{id}", late.getId()).with(as(guest)))
                .andExpect(content().string(containsString("không đến nhận phòng")));
        // Don tiep theo cua khach: le tan thay canh bao
        Booking next = persistBooking(guest, room, BookingStatus.PENDING, 20, 1);
        mockMvc.perform(get("/admin/bookings/{id}", next.getId()).with(as(staff)))
                .andExpect(content().string(containsString("đã không đến")));
        mockMvc.perform(get("/admin/bookings/{id}", late.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Khách không đến")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("lần ở các đơn khác"))));
    }

    @Test
    void cannotMarkBeforeCheckInTime_orCheckedIn() throws Exception {
        Booking tomorrow = persistBooking(guest, room, BookingStatus.CONFIRMED, 1, 1);
        mockMvc.perform(post("/admin/bookings/{id}/no-show", tomorrow.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("Chưa tới giờ nhận phòng")));
        Booking inHouse = persistBooking(guest, persistRoom(room.getRoomType(), 800_000, RoomStatus.AVAILABLE), BookingStatus.CHECKED_IN, -1, 2);
        mockMvc.perform(post("/admin/bookings/{id}/no-show", inHouse.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("Chỉ đánh dấu")));
        assertThat(bookingRepository.findById(tomorrow.getId()).orElseThrow().isNoShow()).isFalse();
        mockMvc.perform(post("/admin/bookings/{id}/no-show", tomorrow.getId()).with(csrf())
                .with(as(persistUser(UserRole.CUSTOMER, CustomerType.NEW)))).andExpect(status().isForbidden());
    }

    @Test
    void eveningReminder_listsGuestsPastCheckInTime() {
        Booking today = persistBooking(guest, room, BookingStatus.CONFIRMED, 0, 1);
        today.setCheckInTime(LocalTime.MIDNIGHT);
        bookingRepository.save(today);
        long start = notificationService.lastId();
        // Ngoai giao dich cua service -> gui ngay khi khong co dong bo; trong test dang co giao dich nen tu goi afterCommit
        scheduler.remind();
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThat(notificationService.recent(start)).anySatisfy(n -> {
            assertThat(n.kind()).isEqualTo("no-show");
            assertThat(n.message()).contains(room.getRoomNumber());
        });
    }
}
