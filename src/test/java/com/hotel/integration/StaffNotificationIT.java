package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Thong bao tuc thoi cho le tan: nghiep vu that tao thong bao (sau commit), luong SSE chi cho ADMIN / STAFF
class StaffNotificationIT extends ItFixtures {

    @Autowired private StaffNotificationService notificationService;

    private User staff;
    private Room room;
    private long startId;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang TB IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        startId = notificationService.lastId();
    }

    // Test chay trong 1 giao dich (khong bao gio commit): tu goi afterCommit cua thong bao dang cho
    private List<StaffNotificationService.Notice> commitNotices() {
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(TransactionSynchronization::afterCommit);
        return notificationService.recent(startId);
    }

    @Test
    void newOnlineBooking_and_contactMessage_notifyStaffAfterCommit() throws Exception {
        LocalDate in = LocalDate.now().plusDays(40);
        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                        .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khách Thông Báo").param("guestPhone", "0909001122").param("guestEmail", "tb@mail.vn"))
                .andExpect(status().is3xxRedirection());
        // Chua commit -> chua bao
        assertThat(notificationService.recent(startId)).isEmpty();

        mockMvc.perform(post("/contact").with(csrf()).param("fullName", "Chị Lan").param("email", "lan@mail.vn")
                .param("message", "Cho em hỏi resort có đón khách ở ga Hạ Long không ạ?"));

        List<StaffNotificationService.Notice> notices = commitNotices();
        assertThat(notices).extracting(StaffNotificationService.Notice::title)
                .anySatisfy(t -> assertThat(t).startsWith("Đơn đặt phòng mới #"))
                .anySatisfy(t -> assertThat(t).isEqualTo("Tin nhắn liên hệ từ Chị Lan"));
        StaffNotificationService.Notice booking = notices.stream().filter(n -> n.kind().equals("booking")).findFirst().orElseThrow();
        assertThat(booking.message()).contains("Phòng " + room.getRoomNumber()).contains("Khách Thông Báo");
        assertThat(booking.url()).startsWith("/admin/bookings/");

        // Trang du phong tra JSON cho le tan
        String json = mockMvc.perform(get("/admin/notifications/recent").with(as(staff)).param("after", String.valueOf(startId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("Đơn đặt phòng mới").contains("\"level\":\"INFO\"");
    }

    @Test
    void roomServiceAndSpa_notify() throws Exception {
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking stay = persistBooking(guest, room, BookingStatus.CHECKED_IN, 0, 3);
        mockMvc.perform(post("/customer/bookings/{id}/facility", stay.getId()).with(csrf()).with(as(guest))
                        .param("kind", "RESTAURANT").param("date", stay.getCheckOutDate().toString()).param("time", "19:00").param("people", "4"))
                .andExpect(flash().attributeExists("facilitySuccess"));
        assertThat(commitNotices()).singleElement().satisfies(n -> {
            assertThat(n.title()).isEqualTo("Đặt bàn nhà hàng mới · phòng " + room.getRoomNumber());
            assertThat(n.url()).isEqualTo("/admin/facility-reservations");
        });
    }

    @Test
    void stream_onlyAdminAndStaff_scriptOnlyOnFrontDeskPages() throws Exception {
        mockMvc.perform(get("/admin/notifications/stream").with(as(staff)).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
        mockMvc.perform(get("/admin/notifications/stream").with(as(persistUser(UserRole.ACCOUNTANT, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/notifications/recent").with(as(persistUser(UserRole.CUSTOMER, CustomerType.NEW))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/today").with(as(staff)))
                .andExpect(content().string(containsString("/js/staff-notify.js")));
        mockMvc.perform(get("/admin/company-invoices").with(as(persistUser(UserRole.ACCOUNTANT, null))))
                .andExpect(content().string(not(containsString("staff-notify.js"))));
    }
}
