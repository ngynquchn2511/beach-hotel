package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Le tan doi phong cho khach: chi phong trong dung cac dem, tinh chenh lech (hoac mien), khach dang o thi doi trang thai phong
class RoomMoveIT extends ItFixtures {

    @Autowired private BookingService bookingService;

    private User staff;
    private User guest;
    private Room roomA;
    private Room sameType;
    private Room bigger;
    private Room maintenance;
    private Room taken;
    private Booking booking;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        RoomType standard = persistRoomType("Hang Doi A IT", 1_000_000, 2);
        RoomType suite = persistRoomType("Hang Doi B IT", 1_500_000, 2);
        roomA = persistRoom(standard, 1_000_000, RoomStatus.AVAILABLE);
        sameType = persistRoom(standard, 1_000_000, RoomStatus.AVAILABLE);
        bigger = persistRoom(suite, 1_500_000, RoomStatus.AVAILABLE);
        maintenance = persistRoom(standard, 1_000_000, RoomStatus.MAINTENANCE);
        taken = persistRoom(standard, 1_000_000, RoomStatus.AVAILABLE);
        booking = persistBooking(guest, roomA, BookingStatus.CONFIRMED, 30, 2);
        persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), taken, BookingStatus.CONFIRMED, 31, 2);
    }

    private Booking reload() {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    @Test
    void movePage_listsOnlyFreeRooms_sameTypeFirst() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/bookings/{id}/move", booking.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đổi phòng cho đơn #" + booking.getId())))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<BookingService.RoomMoveOption> options =
                (List<BookingService.RoomMoveOption>) result.getModelAndView().getModel().get("options");
        List<Long> ids = options.stream().map(o -> o.room().getId()).toList();
        assertThat(ids).contains(sameType.getId(), bigger.getId())
                .doesNotContain(roomA.getId(), maintenance.getId(), taken.getId());
        assertThat(ids.indexOf(sameType.getId())).isLessThan(ids.indexOf(bigger.getId()));
    }

    @Test
    void moveToPricierRoom_chargesDifference_orWaived() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/move", booking.getId()).with(csrf()).with(as(staff))
                        .param("newRoomId", bigger.getId().toString()).param("note", "Khách xin view biển"))
                .andExpect(flash().attribute("successMessage", containsString("Đã chuyển sang phòng " + bigger.getRoomNumber())));
        Booking moved = reload();
        assertThat(moved.getRoom().getId()).isEqualTo(bigger.getId());
        assertThat(moved.getMovedFromRoom()).isEqualTo(roomA.getRoomNumber());
        assertThat(moved.getRoomMoveNote()).isEqualTo("Khách xin view biển");
        assertThat(moved.getTotalAmount()).isEqualByComparingTo("3000000");

        // Doi ve phong cung hang, mien chenh lech -> tien giu nguyen
        mockMvc.perform(post("/admin/bookings/{id}/move", booking.getId()).with(csrf()).with(as(staff))
                .param("newRoomId", sameType.getId().toString()).param("waiveDifference", "true"));
        assertThat(reload().getRoom().getId()).isEqualTo(sameType.getId());
        assertThat(reload().getTotalAmount()).isEqualByComparingTo("3000000");

        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Đã đổi từ phòng " + bigger.getRoomNumber())));
    }

    @Test
    void takenOrMaintenanceRoom_rejected() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/move", booking.getId()).with(csrf()).with(as(staff))
                        .param("newRoomId", taken.getId().toString()))
                .andExpect(redirectedUrl("/admin/bookings/" + booking.getId() + "/move"))
                .andExpect(flash().attribute("errorMessage", containsString("không còn trống")));
        mockMvc.perform(post("/admin/bookings/{id}/move", booking.getId()).with(csrf()).with(as(staff))
                .param("newRoomId", maintenance.getId().toString()));
        assertThat(reload().getRoom().getId()).isEqualTo(roomA.getId());
    }

    @Test
    void checkedInGuest_oldRoomNeedsCleaning_newRoomOccupied_stayedNightsKeepOldPrice() throws Exception {
        Booking staying = persistBooking(guest, roomA, BookingStatus.CHECKED_IN, -1, 3);
        roomA.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(roomA);
        Room dirty = persistRoom(roomA.getRoomType(), 1_000_000, RoomStatus.NOT_READY);

        List<Long> ids = bookingService.roomMoveOptions(staying.getId()).stream().map(o -> o.room().getId()).toList();
        assertThat(ids).contains(bigger.getId()).doesNotContain(dirty.getId());

        mockMvc.perform(post("/admin/bookings/{id}/move", staying.getId()).with(csrf()).with(as(staff))
                .param("newRoomId", bigger.getId().toString()));
        Booking moved = bookingRepository.findById(staying.getId()).orElseThrow();
        // 1 dem da o 1.000.000 + 2 dem con lai phong moi 1.500.000
        assertThat(moved.getRoomAmount()).isEqualByComparingTo("4000000");
        assertThat(roomRepository.findById(roomA.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.NOT_READY);
        assertThat(roomRepository.findById(bigger.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
    }

    @Test
    void checkedOutBooking_cannotMove() throws Exception {
        booking.setStatus(BookingStatus.CHECKED_OUT);
        bookingRepository.save(booking);
        mockMvc.perform(get("/admin/bookings/{id}/move", booking.getId()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + booking.getId()));
    }
}
