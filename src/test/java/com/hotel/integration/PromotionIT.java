package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PromotionRuleRepository;
import com.hotel.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khuyen mai tu dong: admin tao luat, khach dat online thoa dieu kien thi tu giam % tien phong (luat co loi nhat)
class PromotionIT extends ItFixtures {

    @Autowired private PromotionRuleRepository ruleRepository;
    @Autowired private PromotionService promotionService;

    private User admin;
    private User customer;
    private Room room;

    @BeforeEach
    void seed() {
        ruleRepository.deleteAll();
        admin = persistUser(UserRole.ADMIN, null);
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang KM IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private void rule(String name, String type, int threshold, int percent) throws Exception {
        mockMvc.perform(post("/admin/pricing/promotions").with(csrf()).with(as(admin))
                        .param("name", name).param("type", type)
                        .param("threshold", String.valueOf(threshold)).param("discountPercent", String.valueOf(percent)))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm")));
    }

    private Booking book(int daysAhead, int nights) throws Exception {
        LocalDate in = LocalDate.now().plusDays(daysAhead);
        mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(customer))
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(nights).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", customer.getFullName()).param("guestPhone", customer.getPhoneNumber())
                        .param("guestEmail", customer.getEmail()))
                .andExpect(status().is3xxRedirection());
        return bookingRepository.findAll().stream()
                .filter(b -> b.getCustomer().getId().equals(customer.getId()))
                .max(Comparator.comparing(Booking::getId)).orElseThrow();
    }

    @Test
    void onlineBooking_getsBestQualifyingPromotion() throws Exception {
        rule("Ở dài ngày", "LONG_STAY", 3, 10);
        rule("Đặt sớm", "EARLY_BIRD", 30, 15);

        // 3 dem, dat truoc 40 ngay -> ca 2 luat thoa, lay 15%
        Booking early = book(40, 3);
        assertThat(early.getPromotionName()).isEqualTo("Đặt sớm");
        assertThat(early.getPromotionAmount()).isEqualByComparingTo(early.getEffectiveRoomAmount().multiply(BigDecimal.valueOf(0.15)));
        assertThat(early.getTotalAmount()).isEqualByComparingTo(early.getEffectiveRoomAmount().subtract(early.getPromotionAmount()));

        // 3 dem, dat truoc 5 ngay -> chi o dai ngay 10%
        Booking longStay = book(5, 3);
        assertThat(longStay.getPromotionName()).isEqualTo("Ở dài ngày");
        assertThat(longStay.getPromotionPercent()).isEqualTo(10);

        // 1 dem, dat truoc 5 ngay -> khong co khuyen mai
        Booking none = book(8, 1);
        assertThat(none.getPromotionAmount()).isNull();
        assertThat(none.getTotalAmount()).isEqualByComparingTo(none.getEffectiveRoomAmount());
    }

    @Test
    void roomPageShowsBannerAndLine_pausedRuleNotApplied() throws Exception {
        rule("Giờ chót", "LAST_MINUTE", 2, 20);
        LocalDate in = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString()))
                .andExpect(content().string(containsString("Ưu đãi tự động")))
                .andExpect(content().string(containsString("Khuyến mãi Giờ chót (-20%)")));

        PromotionRule saved = ruleRepository.findAll().get(0);
        mockMvc.perform(post("/admin/pricing/promotions/{id}/toggle", saved.getId()).with(csrf()).with(as(admin)));
        assertThat(book(1, 1).getPromotionAmount()).isNull();
    }

    @Test
    void changeDates_reevaluatesPromotion_editComboKeepsIt() throws Exception {
        rule("Ở dài ngày", "LONG_STAY", 3, 10);
        Booking booking = book(20, 3);
        assertThat(booking.getPromotionPercent()).isEqualTo(10);

        // Doi xuong 2 dem -> het dieu kien o dai ngay
        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).with(as(customer))
                .param("checkIn", booking.getCheckInDate().toString())
                .param("checkOut", booking.getCheckInDate().plusDays(2).toString()));
        Booking changed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(changed.getPromotionAmount()).isNull();
        assertThat(changed.getTotalAmount()).isEqualByComparingTo(changed.getEffectiveRoomAmount());

        // Doi lai 4 dem -> co lai khuyen mai
        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).with(as(customer))
                .param("checkIn", booking.getCheckInDate().toString())
                .param("checkOut", booking.getCheckInDate().plusDays(4).toString()));
        Booking back = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(back.getPromotionPercent()).isEqualTo(10);
        assertThat(back.getTotalAmount()).isEqualByComparingTo(back.getEffectiveRoomAmount().subtract(back.getPromotionAmount()));
    }

    @Test
    void adminValidation_andStaffCannotManage() throws Exception {
        mockMvc.perform(post("/admin/pricing/promotions").with(csrf()).with(as(admin))
                        .param("name", "Quá tay").param("type", "LONG_STAY").param("threshold", "2").param("discountPercent", "80"))
                .andExpect(flash().attribute("errorMessage", containsString("từ 1% đến 50%")));
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/pricing/promotions").with(csrf()).with(as(staff))
                        .param("name", "X").param("type", "LONG_STAY").param("threshold", "2").param("discountPercent", "10"))
                .andExpect(status().isForbidden());
        assertThat(ruleRepository.count()).isZero();
    }

    @Test
    void qualifyRules() {
        LocalDate today = LocalDate.of(2026, 10, 16);
        PromotionRule lastMinute = PromotionRule.builder().type(PromotionRule.Type.LAST_MINUTE).threshold(2).build();
        assertThat(PromotionService.qualifies(lastMinute, today, today.plusDays(1), today)).isTrue();
        assertThat(PromotionService.qualifies(lastMinute, today.plusDays(3), today.plusDays(4), today)).isFalse();
        PromotionRule early = PromotionRule.builder().type(PromotionRule.Type.EARLY_BIRD).threshold(30).build();
        assertThat(PromotionService.qualifies(early, today.plusDays(30), today.plusDays(31), today)).isTrue();
        assertThat(PromotionService.qualifies(early, today.plusDays(29), today.plusDays(31), today)).isFalse();
    }
}
