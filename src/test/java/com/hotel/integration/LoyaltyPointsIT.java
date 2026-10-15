package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PointTransactionRepository;
import com.hotel.service.LoyaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Diem thuong: tich khi tra phong, dung khi dat (1 diem = 1.000d, toi da 50%), huy don thi hoan diem
class LoyaltyPointsIT extends ItFixtures {

    @Autowired private PointTransactionRepository transactionRepository;

    private User customer;
    private Room room;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang Diem IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private User fresh() {
        return userRepository.findById(customer.getId()).orElseThrow();
    }

    private void givePoints(int points) {
        User u = fresh();
        u.setLoyaltyPoints(points);
        userRepository.save(u);
    }

    // Dat 2 dem phong 1.000.000d (tong 2.000.000d, ngay thuong) - ngay cach xa de khong trung
    private ResultActions book(int days, String points) throws Exception {
        LocalDate in = LocalDate.now().plusDays(days);
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(customer))
                .param("roomId", room.getId().toString())
                .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", customer.getFullName()).param("guestPhone", customer.getPhoneNumber())
                .param("guestEmail", customer.getEmail())
                .param("pointsToUse", points));
    }

    private Booking latestBooking() {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getCustomer().getId().equals(customer.getId()))
                .max(java.util.Comparator.comparing(Booking::getId)).orElseThrow();
    }

    @Test
    void redeemOnBooking_thenCancel_refundsPoints() throws Exception {
        givePoints(500);

        book(40, "200").andExpect(status().is3xxRedirection());
        Booking booking = latestBooking();
        assertThat(booking.getPointsUsed()).isEqualTo(200);
        assertThat(booking.getPointsDiscount()).isEqualByComparingTo("200000");
        assertThat(booking.getTotalAmount().add(booking.getPointsDiscount()))
                .isEqualByComparingTo(booking.getEffectiveRoomAmount());
        assertThat(fresh().getPointsBalance()).isEqualTo(300);

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).with(csrf()).with(as(customer)));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(fresh().getPointsBalance()).isEqualTo(500);
        assertThat(transactionRepository.findRecent(customer.getId(), PageRequest.of(0, 10)))
                .extracting(PointTransaction::getType)
                .containsExactly(PointTransaction.Type.REFUND, PointTransaction.Type.REDEEM);
    }

    @Test
    void redeem_rejectsAboveCapBelowMinimumAndAboveBalance() throws Exception {
        givePoints(5000);
        long before = bookingRepository.count();

        // 50% cua 2.000.000d = 1.000 diem
        book(50, "1500").andExpect(model().attribute("errorMessage", containsString("tối đa 1.000 điểm")));
        book(50, "20").andExpect(model().attribute("errorMessage", containsString("tối thiểu 50")));
        givePoints(100);
        book(50, "300").andExpect(model().attribute("errorMessage", containsString("chỉ có 100 điểm")));

        assertThat(bookingRepository.count()).isEqualTo(before);
        assertThat(fresh().getPointsBalance()).isEqualTo(100);
    }

    @Test
    void confirmPage_showsPointsBox_withMaxForThisBooking() throws Exception {
        givePoints(1500);
        LocalDate in = LocalDate.now().plusDays(60);

        mockMvc.perform(get("/customer/bookings/new").with(as(customer))
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("maxPoints", 1000))
                .andExpect(content().string(containsString("Dùng tối đa (1.000)")));
    }

    @Test
    void checkout_earnsPointsByTier_andAccountShowsHistory() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        User vip = persistUser(UserRole.CUSTOMER, CustomerType.VIP);
        Booking stay = persistBooking(vip, room, BookingStatus.CHECKED_IN, -2, 2);
        declareGuest(stay);
        room.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        mockMvc.perform(post("/admin/bookings/{id}/check-out", stay.getId()).with(csrf()).with(as(staff))
                .param("paymentMethod", "CASH"));

        // 2.000.000d / 10.000 = 200 diem x1.5 (VIP) = 300
        Booking done = bookingRepository.findById(stay.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(done.getPointsEarned()).isEqualTo(300);
        assertThat(userRepository.findById(vip.getId()).orElseThrow().getPointsBalance()).isEqualTo(300);

        mockMvc.perform(get("/customer/account").with(as(vip)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tích điểm")))
                .andExpect(content().string(containsString("+300")));
    }

    @Test
    void pointsFormula() {
        assertThat(LoyaltyService.pointsFor(BigDecimal.valueOf(2_000_000), CustomerType.NEW)).isEqualTo(200);
        assertThat(LoyaltyService.pointsFor(BigDecimal.valueOf(2_000_000), CustomerType.REGULAR)).isEqualTo(240);
        assertThat(LoyaltyService.pointsFor(BigDecimal.valueOf(9_999), CustomerType.VIP)).isZero();
    }
}
