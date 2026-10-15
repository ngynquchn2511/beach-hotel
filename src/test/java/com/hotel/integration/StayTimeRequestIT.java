package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Nhan phong som / tra phong muon: ngoai khung 14:00 - 12:00 phai gui yeu cau, co phu phi, le tan duyet
class StayTimeRequestIT extends ItFixtures {

    @Autowired private BookingChargeRepository chargeRepository;

    private User staff;
    private User guest;
    private Room room;
    private Booking booking;
    private String token;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang Gio IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        booking = persistBooking(guest, room, BookingStatus.CONFIRMED, 10, 2);
        token = booking.getAccessToken();
    }

    private ResultActions request(String in, String out) throws Exception {
        return mockMvc.perform(post("/customer/bookings/{id}/stay-time", booking.getId()).with(csrf())
                .param("token", token).param("checkInTime", in).param("checkOutTime", out));
    }

    private Booking reload() {
        return bookingRepository.findById(booking.getId()).orElseThrow();
    }

    @Test
    void requestThenApprove_changesTimesAndAddsCharges() throws Exception {
        // Nhan 12:00 (som 2 tieng -> 30%), tra 16:00 (muon 4 tieng -> 50%) cua gia 1.000.000d/dem
        request("12:00", "16:00")
                .andExpect(flash().attribute("stayTimeSuccess", containsString("Đã gửi yêu cầu")));
        Booking pending = reload();
        assertThat(pending.getTimeRequestStatus()).isEqualTo(StayTimeRequestStatus.PENDING);
        assertThat(pending.getTimeRequestFee()).isEqualByComparingTo("800000");
        assertThat(pending.getCheckInTime()).isEqualTo(LocalTime.of(14, 0));

        mockMvc.perform(get("/admin/today").with(as(staff)))
                .andExpect(content().string(containsString("Yêu cầu nhận sớm / trả muộn chờ duyệt")));

        mockMvc.perform(post("/admin/bookings/{id}/stay-time/approve", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Đã duyệt")));
        Booking approved = reload();
        assertThat(approved.getTimeRequestStatus()).isEqualTo(StayTimeRequestStatus.APPROVED);
        assertThat(approved.getCheckInTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(approved.getCheckOutTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
                .extracting(BookingCharge::getType, c -> c.getAmount().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChargeType.EARLY_CHECKIN, 300000),
                        org.assertj.core.groups.Tuple.tuple(ChargeType.LATE_CHECKOUT, 500000));

        // Gio da duyet duoc giu khi khach sua gio trong khung (khong bi bat loi)
        mockMvc.perform(post("/customer/bookings/{id}/edit-time", booking.getId()).with(csrf()).param("token", token)
                        .param("checkInTime", "12:00").param("checkOutTime", "11:00"))
                .andExpect(flash().attributeCount(0));
        assertThat(reload().getCheckOutTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void outsideWindow_rejectedWhenBookingOrEditingDirectly() throws Exception {
        LocalDate in = LocalDate.now().plusDays(30);
        mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(guest))
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString())
                        .param("checkInTime", "09:00").param("checkOutTime", "12:00")
                        .param("guestName", guest.getFullName()).param("guestPhone", guest.getPhoneNumber())
                        .param("guestEmail", guest.getEmail()))
                .andExpect(model().attribute("errorMessage", containsString("Nhận phòng sớm / Trả phòng muộn")));

        mockMvc.perform(post("/customer/bookings/{id}/edit-time", booking.getId()).with(csrf()).param("token", token)
                        .param("checkInTime", "14:00").param("checkOutTime", "15:00"))
                .andExpect(flash().attribute("errorMessage", containsString("trả phòng trước 12:00")));
        assertThat(reload().getCheckOutTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void earlyCheckIn_blockedWhenPreviousGuestLeavesSameDay() throws Exception {
        // Khach truoc tra phong 12:00 dung ngay nhan phong -> khong cho nhan luc 10:00
        User other = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        persistBooking(other, room, BookingStatus.CONFIRMED, 8, 2);

        request("10:00", "").andExpect(flash().attribute("stayTimeError", containsString("12:00")));
        assertThat(reload().getTimeRequestStatus()).isNull();
    }

    @Test
    void rejectWithNote_andWithdraw() throws Exception {
        request("", "18:00");
        mockMvc.perform(post("/admin/bookings/{id}/stay-time/reject", booking.getId()).with(csrf()).with(as(staff))
                .param("note", "Phòng có khách nhận sớm"));
        Booking rejected = reload();
        assertThat(rejected.getTimeRequestStatus()).isEqualTo(StayTimeRequestStatus.REJECTED);
        assertThat(rejected.getCheckOutTime()).isEqualTo(LocalTime.of(12, 0));
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token))
                .andExpect(content().string(containsString("Phòng có khách nhận sớm")));

        // Gui lai roi rut
        request("", "13:00");
        mockMvc.perform(post("/customer/bookings/{id}/stay-time/withdraw", booking.getId()).with(csrf()).param("token", token));
        assertThat(reload().getTimeRequestStatus()).isNull();
    }

    @Test
    void checkedInGuest_canAskLateCheckOut_only() throws Exception {
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckInDate(LocalDate.now().minusDays(1));
        booking.setCheckOutDate(LocalDate.now().plusDays(1));
        bookingRepository.save(booking);

        // Gio nhan bi bo qua khi dang o
        request("10:00", "20:00");
        Booking pending = reload();
        assertThat(pending.getRequestedCheckInTime()).isNull();
        assertThat(pending.getRequestedCheckOutTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(pending.getTimeRequestFee()).isEqualByComparingTo("1000000");
    }

    @Test
    void nothingOutsideWindow_isRejected() throws Exception {
        request("", "").andExpect(flash().attribute("stayTimeError", containsString("Hãy chọn giờ")));
    }
}
