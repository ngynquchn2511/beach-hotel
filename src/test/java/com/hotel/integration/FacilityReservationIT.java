package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.FacilityReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dat lich spa / ban nha hang tren trang don; le tan xac nhan (spa -> phu phi don phong)
class FacilityReservationIT extends ItFixtures {

    @Autowired private FacilityReservationRepository reservationRepository;
    @Autowired private BookingChargeRepository chargeRepository;

    private User customer;
    private User staff;
    private Booking booking;
    private Room room;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang Spa IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        booking = persistBooking(customer, room, BookingStatus.CONFIRMED, 10, 3);
    }

    private ResultActions reserve(Booking b, User who, String kind, String treatment, LocalDate date, String time, int people)
            throws Exception {
        var req = post("/customer/bookings/{id}/facility", b.getId()).with(csrf()).with(as(who))
                .param("kind", kind).param("date", date.toString()).param("time", time).param("people", String.valueOf(people));
        if (treatment != null) {
            req.param("treatment", treatment);
        }
        return mockMvc.perform(req);
    }

    private FacilityReservation latest() {
        return reservationRepository.findAll().stream().max(java.util.Comparator.comparing(FacilityReservation::getId)).orElseThrow();
    }

    @Test
    void spa_bookConfirmCharge_thenGuestCancelRemovesCharge() throws Exception {
        LocalDate day = booking.getCheckInDate().plusDays(1);
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(as(customer)))
                .andExpect(content().string(containsString("Đặt lịch spa")));
        reserve(booking, customer, "SPA", "BODY_MASSAGE", day, "10:00", 2)
                .andExpect(flash().attribute("facilitySuccess", containsString("Đã gửi yêu cầu")));
        FacilityReservation r = latest();
        assertThat(r.getStatus()).isEqualTo(ServiceStatus.PENDING);
        assertThat(r.getTotalPrice()).isEqualByComparingTo("1100000");

        mockMvc.perform(get("/admin/facility-reservations").with(as(staff)).param("from", day.toString()))
                .andExpect(content().string(containsString("Massage body tinh dầu")));
        mockMvc.perform(post("/admin/facility-reservations/{id}/confirm", r.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("tiền spa đã cộng")));
        FacilityReservation confirmed = reservationRepository.findById(r.getId()).orElseThrow();
        BookingCharge charge = chargeRepository.findById(confirmed.getCharge().getId()).orElseThrow();
        assertThat(charge.getType()).isEqualTo(ChargeType.SPA);
        assertThat(charge.getAmount()).isEqualByComparingTo("1100000");

        mockMvc.perform(post("/customer/bookings/{id}/facility/{rid}/cancel", booking.getId(), r.getId()).with(csrf()).with(as(customer)))
                .andExpect(flash().attribute("facilitySuccess", containsString("Đã hủy")));
        assertThat(reservationRepository.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(ServiceStatus.CANCELLED);
        assertThat(chargeRepository.findById(charge.getId())).isEmpty();
    }

    @Test
    void capacityAndValidationRules() throws Exception {
        LocalDate day = booking.getCheckInDate();
        // Spa 3 giuong / khung gio
        for (int i = 0; i < 3; i++) {
            Booking other = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), persistRoom(room.getRoomType(), 900_000,
                    RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 10, 3);
            reserve(other, other.getCustomer(), "SPA", "FACIAL", day, "15:00", 1)
                    .andExpect(flash().attributeExists("facilitySuccess"));
        }
        reserve(booking, customer, "SPA", "FACIAL", day, "15:00", 1)
                .andExpect(flash().attribute("facilityError", containsString("kín lịch")));
        // Nha hang 40 cho / khung gio
        reserve(booking, customer, "RESTAURANT", null, day, "19:00", 20).andExpect(flash().attributeExists("facilitySuccess"));
        reserve(booking, customer, "RESTAURANT", null, day, "19:00", 20).andExpect(flash().attributeExists("facilitySuccess"));
        reserve(booking, customer, "RESTAURANT", null, day, "19:00", 1)
                .andExpect(flash().attribute("facilityError", containsString("kín chỗ")));
        // Ngoai ngay o, sai khung gio, qua so nguoi
        reserve(booking, customer, "RESTAURANT", null, booking.getCheckOutDate().plusDays(1), "19:00", 2)
                .andExpect(flash().attribute("facilityError", containsString("thời gian lưu trú")));
        reserve(booking, customer, "SPA", "FACIAL", day, "10:30", 1)
                .andExpect(flash().attribute("facilityError", containsString("Khung giờ")));
        reserve(booking, customer, "SPA", "FACIAL", day, "11:00", 3)
                .andExpect(flash().attribute("facilityError", containsString("từ 1 đến 2")));
        assertThat(reservationRepository.findAll().stream().filter(r -> r.getStatus() != ServiceStatus.CANCELLED)).hasSize(5);
    }

    @Test
    void cancelledBooking_cannotReserve_otherCustomerBlocked() throws Exception {
        Booking cancelled = persistBooking(customer, room, BookingStatus.CANCELLED, 30, 2);
        reserve(cancelled, customer, "RESTAURANT", null, cancelled.getCheckInDate(), "19:00", 2)
                .andExpect(flash().attribute("facilityError", containsString("đã hủy")));
        User stranger = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        reserve(booking, stranger, "RESTAURANT", null, booking.getCheckInDate(), "19:00", 2);
        assertThat(reservationRepository.count()).isZero();
        mockMvc.perform(get("/admin/facility-reservations").with(as(customer))).andExpect(status().isForbidden());
    }
}
