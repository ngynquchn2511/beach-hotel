package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.CallReminderRepository;
import com.hotel.service.CallReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Goi nhac khach: danh sach khach sap den / sap het han coc; khong nghe may van con trong danh sach, khach xac nhan thi roi danh sach
class CallReminderIT extends ItFixtures {

    @Autowired private CallReminderService service;
    @Autowired private CallReminderRepository repository;

    private List<Long> dueIds(CallReminder.Purpose purpose) {
        return service.due(LocalDateTime.now()).stream().filter(t -> t.purpose() == purpose)
                .map(t -> t.booking().getId()).toList();
    }

    @Test
    void dueListContainsUpcomingArrivalsAndDepositsOnly() {
        Room room = persistRoom(persistRoomType("Hang Goi IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking tomorrow = persistBooking(guest, room, BookingStatus.CONFIRMED, 1, 2);
        Booking later = persistBooking(guest, room, BookingStatus.CONFIRMED, 10, 2);
        Booking cancelled = persistBooking(guest, room, BookingStatus.CANCELLED, 1, 2);
        Booking deposit = persistBooking(guest, room, BookingStatus.PENDING, 20, 2);
        deposit.setDepositAmount(BigDecimal.valueOf(500_000));
        deposit.setDepositDeadline(LocalDateTime.now().plusHours(10));
        bookingRepository.save(deposit);

        assertThat(dueIds(CallReminder.Purpose.ARRIVAL)).contains(tomorrow.getId())
                .doesNotContain(later.getId(), cancelled.getId(), deposit.getId());
        assertThat(dueIds(CallReminder.Purpose.DEPOSIT)).contains(deposit.getId()).doesNotContain(tomorrow.getId());

        // Khong nghe may -> van con, ghi so lan goi; xac nhan -> roi danh sach
        service.record(tomorrow.getId(), CallReminder.Purpose.ARRIVAL, CallReminder.Result.NO_ANSWER, null, "Lễ tân A");
        CallReminderService.CallTask task = service.due(LocalDateTime.now()).stream()
                .filter(t -> t.booking().getId().equals(tomorrow.getId())).findFirst().orElseThrow();
        assertThat(task.attempts()).isEqualTo(1);
        assertThat(task.last().getResult()).isEqualTo(CallReminder.Result.NO_ANSWER);
        assertThat(task.phone()).isEqualTo(guest.getPhoneNumber());

        service.record(tomorrow.getId(), CallReminder.Purpose.ARRIVAL, CallReminder.Result.CONFIRMED, "Đến 15h", "Lễ tân A");
        assertThat(dueIds(CallReminder.Purpose.ARRIVAL)).doesNotContain(tomorrow.getId());
        // Goi nhac coc la muc dich rieng
        service.record(deposit.getId(), CallReminder.Purpose.ARRIVAL, CallReminder.Result.CONFIRMED, null, null);
        assertThat(dueIds(CallReminder.Purpose.DEPOSIT)).contains(deposit.getId());
    }

    @Test
    void staffRecordsCallFromPageAndBookingDetail() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        Room room = persistRoom(persistRoomType("Hang Goi2 IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking b = persistBooking(guest, room, BookingStatus.CONFIRMED, 2, 1);

        mockMvc.perform(get("/admin/call-reminders").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gọi nhắc khách")))
                .andExpect(content().string(containsString("tel:" + guest.getPhoneNumber())))
                .andExpect(content().string(containsString("#" + b.getId())));

        mockMvc.perform(post("/admin/call-reminders/" + b.getId()).with(as(staff)).with(csrf())
                        .param("purpose", "ARRIVAL").param("result", "CALL_BACK").param("note", "Gọi lại sau 17h"))
                .andExpect(redirectedUrl("/admin/call-reminders"))
                .andExpect(flash().attributeExists("successMessage"));
        // Thieu ket qua -> bao loi
        mockMvc.perform(post("/admin/call-reminders/" + b.getId()).with(as(staff)).with(csrf()).param("purpose", "ARRIVAL"))
                .andExpect(flash().attributeExists("errorMessage"));
        // Ghi tu trang chi tiet don -> quay lai trang don
        mockMvc.perform(post("/admin/call-reminders/" + b.getId()).with(as(staff)).with(csrf())
                        .param("purpose", "ARRIVAL").param("result", "CONFIRMED").param("back", "booking"))
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId()));

        List<CallReminder> calls = repository.findByBookingIdOrderByCalledAtDesc(b.getId());
        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(CallReminder::getStaffName).containsOnly(staff.getFullName());

        mockMvc.perform(get("/admin/bookings/" + b.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cuộc gọi nhắc khách")))
                .andExpect(content().string(containsString("Gọi lại sau 17h")));
    }

    @Test
    void housekeeperAndCustomerCannotOpen() throws Exception {
        mockMvc.perform(get("/admin/call-reminders").with(as(persistUser(UserRole.HOUSEKEEPER, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/call-reminders").with(as(persistUser(UserRole.CUSTOMER, CustomerType.NEW))))
                .andExpect(status().isForbidden());
    }
}
