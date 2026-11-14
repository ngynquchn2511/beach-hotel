package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ma don: thang + ngay tao + so thu tu trong ngay (VD 11140001), hien thay #id, tim duoc theo ma
class BookingCodeIT extends ItFixtures {

    @Test
    void newBookings_getSequentialDailyCodes_shownAndSearchable() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Hang Ma Don IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        Booking first = persistBooking(guest, room, BookingStatus.CONFIRMED, 3, 2);
        Booking second = persistBooking(guest, room, BookingStatus.CONFIRMED, 7, 2);

        LocalDate today = LocalDate.now();
        String prefix = String.format("%02d%02d", today.getMonthValue(), today.getDayOfMonth());
        assertThat(first.getBookingCode()).matches(prefix + "\\d{4,}");
        assertThat(Integer.parseInt(second.getBookingCode().substring(4)))
                .isEqualTo(Integer.parseInt(first.getBookingCode().substring(4)) + 1);
        assertThat(bookingRepository.findById(first.getId()).orElseThrow().getBookingCode()).isEqualTo(first.getBookingCode());

        mockMvc.perform(get("/admin/bookings/{id}", first.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đơn đặt phòng #" + first.getBookingCode())));

        mockMvc.perform(get("/admin/bookings").param("q", "#" + second.getBookingCode()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#" + second.getBookingCode())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("#" + first.getBookingCode() + "<"))));
    }
}
