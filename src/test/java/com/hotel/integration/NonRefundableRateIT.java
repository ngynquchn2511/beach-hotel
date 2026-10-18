package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PricingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gia khong hoan tien: giam them %, tra truoc toan bo, huy khong hoan, khong tu doi ngay
class NonRefundableRateIT extends ItFixtures {

    @Autowired private PricingSettingsRepository settingsRepository;

    private User customer;
    private Room room;
    private LocalDate in;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang KHT IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        in = LocalDate.now().plusDays(45);
    }

    private void settings(int depositPercent, Integer nonRefPercent) {
        PricingSettings s = PricingSettings.defaults();
        s.setDepositPercent(depositPercent);
        s.setNonRefundablePercent(nonRefPercent);
        settingsRepository.save(s);
    }

    private ResultActions book(boolean nonRefundable) throws Exception {
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(customer))
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Khách KHT").param("guestPhone", "0909001122").param("guestEmail", customer.getEmail())
                .param("nonRefundable", String.valueOf(nonRefundable)));
    }

    private Booking latest() {
        return bookingRepository.findAll().stream().max(Comparator.comparing(Booking::getId)).orElseThrow();
    }

    @Test
    void nonRefundable_discountedPrepaidInFull_cancelKeepsAll_noDateChange() throws Exception {
        settings(30, 10);
        mockMvc.perform(get("/customer/bookings/new").with(as(customer)).param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(content().string(containsString("Giá không hoàn tiền - giảm thêm 10% (khoảng 200.000 VND)")));

        book(true).andExpect(status().is3xxRedirection());
        Booking b = latest();
        assertThat(b.isNonRefundable()).isTrue();
        assertThat(b.getNonRefundableAmount()).isEqualByComparingTo("200000");
        assertThat(b.getTotalAmount()).isEqualByComparingTo("1800000");
        assertThat(b.getDepositAmount()).isEqualByComparingTo("1800000");

        // Doi ngay: bi chan, an o trang don
        mockMvc.perform(post("/customer/bookings/{id}/change-dates", b.getId()).with(csrf()).with(as(customer))
                        .param("checkIn", in.plusDays(3).toString()).param("checkOut", in.plusDays(5).toString()))
                .andExpect(flash().attribute("errorMessage", containsString("không đổi ngày được")));
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(as(customer)))
                .andExpect(content().string(containsString("Giá không hoàn tiền (-10%)")))
                .andExpect(content().string(not(containsString("id=\"changeDatesForm\""))));

        // Da tra truoc roi huy (con 45 ngay): khong hoan
        b.setDepositPaidAt(LocalDateTime.now());
        bookingRepository.save(b);
        mockMvc.perform(post("/customer/bookings/{id}/cancel", b.getId()).with(csrf()).with(as(customer)));
        Booking cancelled = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isZero();
        assertThat(cancelled.isRefundPending()).isFalse();
    }

    @Test
    void flexibleRate_unchanged_andOptionHiddenWhenOffOrNoDeposit() throws Exception {
        settings(30, 10);
        book(false).andExpect(status().is3xxRedirection());
        Booking flexible = latest();
        assertThat(flexible.isNonRefundable()).isFalse();
        assertThat(flexible.getTotalAmount()).isEqualByComparingTo("2000000");
        assertThat(flexible.getDepositAmount()).isEqualByComparingTo("600000");

        // Khong nhan coc -> khong ban gia khong hoan tien du da dat %
        settings(0, 10);
        mockMvc.perform(get("/customer/bookings/new").with(as(customer)).param("roomId", room.getId().toString())
                        .param("checkIn", in.plusDays(10).toString()).param("checkOut", in.plusDays(11).toString()))
                .andExpect(content().string(not(containsString("Giá không hoàn tiền"))));
        in = in.plusDays(10);
        book(true).andExpect(status().isOk()).andExpect(model().attribute("errorMessage", containsString("không áp dụng giá không hoàn tiền")));
    }

    @Test
    void adminSetsPercent_validated() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/pricing/non-refundable").with(csrf()).with(as(admin)).param("nonRefundablePercent", "15"))
                .andExpect(flash().attribute("successMessage", containsString("giảm 15%")));
        assertThat(settingsRepository.findById(PricingSettings.SINGLETON_ID).orElseThrow().getNonRefundablePercent()).isEqualTo(15);
        mockMvc.perform(post("/admin/pricing/non-refundable").with(csrf()).with(as(admin)).param("nonRefundablePercent", "80"))
                .andExpect(flash().attribute("errorMessage", containsString("đến 50%")));
        mockMvc.perform(post("/admin/pricing/non-refundable").with(csrf()).with(as(admin)).param("nonRefundablePercent", "0"))
                .andExpect(flash().attribute("successMessage", containsString("tắt")));
    }
}
