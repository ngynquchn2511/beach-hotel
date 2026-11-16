package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Danh sach khach coc theo ngay: da coc trong ngay / khach den ngay do / het han coc ngay do; quyen xem
class DepositListIT extends ItFixtures {

    @Test
    void listsByPaidDay_arrivalDay_dueDay_andRoles() throws Exception {
        Room room = persistRoom(persistRoomType("Hang Coc IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        LocalDate day = LocalDate.now().minusDays(3);

        Booking paid = persistBooking(guest, room, BookingStatus.CONFIRMED, 10, 2);
        paid.setGuestName("Khách Đã Cọc");
        paid.setDepositAmount(BigDecimal.valueOf(600_000));
        paid.setDepositPaidAt(day.atTime(9, 30));
        paid.setDepositOnlineRef("VNP123");
        bookingRepository.save(paid);

        Booking otherDay = persistBooking(guest, room, BookingStatus.CONFIRMED, 20, 1);
        otherDay.setGuestName("Khách Cọc Hôm Khác");
        otherDay.setDepositAmount(BigDecimal.valueOf(300_000));
        otherDay.setDepositPaidAt(day.minusDays(1).atTime(23, 59));
        bookingRepository.save(otherDay);

        Booking waiting = persistBooking(guest, room, BookingStatus.PENDING, 30, 1);
        waiting.setGuestName("Khách Chờ Cọc");
        waiting.setDepositAmount(BigDecimal.valueOf(400_000));
        waiting.setDepositDeadline(day.atTime(18, 0));
        bookingRepository.save(waiting);

        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(get("/admin/deposits").with(as(staff)).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khách Đã Cọc")))
                .andExpect(content().string(containsString("VNPay")))
                .andExpect(content().string(containsString("600.000")))
                .andExpect(content().string(not(containsString("Khách Cọc Hôm Khác"))))
                .andExpect(content().string(not(containsString("Khách Chờ Cọc"))));

        mockMvc.perform(get("/admin/deposits").with(as(staff)).param("date", day.toString()).param("view", "due"))
                .andExpect(content().string(containsString("Khách Chờ Cọc")))
                .andExpect(content().string(not(containsString("Khách Đã Cọc"))));

        mockMvc.perform(get("/admin/deposits").with(as(persistUser(UserRole.ACCOUNTANT, null)))
                        .param("date", paid.getCheckInDate().toString()).param("view", "arrival"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khách Đã Cọc")));

        mockMvc.perform(get("/admin/deposits").with(as(persistUser(UserRole.HOUSEKEEPER, null)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/deposits").with(as(staff)).param("view", "lung-tung")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã cọc trong ngày")));
    }
}
