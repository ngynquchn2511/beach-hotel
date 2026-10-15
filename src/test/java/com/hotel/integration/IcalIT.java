package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.IcalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dong bo lich iCal: link .ics co ma bi mat, khong lo thong tin khach, doi ma thi link cu het hieu luc
class IcalIT extends ItFixtures {

    @Autowired private IcalService icalService;

    private User admin;
    private Room roomA;
    private Booking confirmed;
    private Booking pending;
    private Booking cancelled;
    private Booking otherRoom;

    @BeforeEach
    void seed() {
        admin = persistUser(UserRole.ADMIN, null);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        RoomType type = persistRoomType("Hang Lich IT", 900_000, 2);
        roomA = persistRoom(type, 900_000, RoomStatus.AVAILABLE);
        Room roomB = persistRoom(type, 900_000, RoomStatus.AVAILABLE);
        confirmed = persistBooking(guest, roomA, BookingStatus.CONFIRMED, 5, 2);
        pending = persistBooking(guest, roomA, BookingStatus.PENDING, 10, 1);
        cancelled = persistBooking(guest, roomA, BookingStatus.CANCELLED, 20, 2);
        otherRoom = persistBooking(guest, roomB, BookingStatus.CONFIRMED, 5, 2);
    }

    private String feed(String path, String key) throws Exception {
        return mockMvc.perform(get(path).param("key", key))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void roomFeed_listsHoldingBookingsOnly_withoutGuestDetails() throws Exception {
        String key = icalService.token();
        String ics = feed("/ical/rooms/" + roomA.getId() + ".ics", key);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:booking-" + confirmed.getId() + "@songxanhresort")
                .contains("DTSTART;VALUE=DATE:" + confirmed.getCheckInDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                .contains("DTEND;VALUE=DATE:" + confirmed.getCheckOutDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                .contains("UID:booking-" + pending.getId() + "@songxanhresort")
                .contains("STATUS:TENTATIVE")
                .doesNotContain("booking-" + cancelled.getId() + "@")
                .doesNotContain("booking-" + otherRoom.getId() + "@")
                .doesNotContain(confirmed.getGuestName())
                .doesNotContain(confirmed.getGuestPhone())
                .doesNotContain(confirmed.getGuestEmail());

        String all = feed("/ical/all.ics", key);
        assertThat(all).contains("booking-" + confirmed.getId() + "@").contains("booking-" + otherRoom.getId() + "@");
    }

    @Test
    void wrongOrMissingKey_andRotatedKey_return404() throws Exception {
        String key = icalService.token();
        mockMvc.perform(get("/ical/rooms/{id}.ics", roomA.getId()).param("key", "sai")).andExpect(status().isNotFound());
        mockMvc.perform(get("/ical/all.ics")).andExpect(status().isNotFound());
        mockMvc.perform(get("/ical/rooms/999999.ics").param("key", key)).andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/ical/rotate").with(csrf()).with(as(admin)))
                .andExpect(redirectedUrl("/admin/ical"));
        mockMvc.perform(get("/ical/all.ics").param("key", key)).andExpect(status().isNotFound());
        feed("/ical/all.ics", icalService.token());
    }

    @Test
    void adminPage_showsLinks_staffForbidden() throws Exception {
        String key = icalService.token();
        mockMvc.perform(get("/admin/ical").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ical/rooms/" + roomA.getId() + ".ics?key=" + key)))
                .andExpect(content().string(containsString("/ical/all.ics?key=" + key)));

        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(get("/admin/ical").with(as(staff))).andExpect(status().isForbidden());
    }

    @Test
    void longLines_areFoldedAt75Bytes_withoutSplittingCharacters() throws Exception {
        String ics = icalService.calendar(roomA.getId(), "Sóng Xanh - Lịch phòng có tên rất dài để kiểm tra việc gập dòng theo chuẩn iCal");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        // Noi lai cac dong gap -> ra dung ten lich
        assertThat(ics.replace("\r\n ", "")).contains("X-WR-CALNAME:Sóng Xanh - Lịch phòng có tên rất dài để kiểm tra việc gập dòng theo chuẩn iCal");
    }
}
