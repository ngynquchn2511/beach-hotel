package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gioi han ma giam gia: thoi han, so luot (doan tinh 1), don toi thieu, tran giam cho ma %
class DiscountLimitsIT extends ItFixtures {

    @Autowired private com.hotel.service.ChatbotService chatbotService;

    private Room room;
    private LocalDate in;

    @BeforeEach
    void seed() {
        room = persistRoom(persistRoomType("Hang MG IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        in = LocalDate.now().plusDays(50);
    }

    private ResultActions book(String code, int offset) throws Exception {
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(asNewCustomer())
                .param("roomId", room.getId().toString()).param("checkIn", in.plusDays(offset).toString())
                .param("checkOut", in.plusDays(offset + 2).toString()).param("guests", "2")
                .param("checkInTime", "14:00").param("checkOutTime", "12:00").param("discountCode", code)
                .param("guestName", "Khách Mã").param("guestPhone", "0909001122").param("guestEmail", "ma" + seq() + "@mail.vn"));
    }

    private Booking latest() {
        return bookingRepository.findAll().stream().max(Comparator.comparing(Booking::getId)).orElseThrow();
    }

    private DiscountCode code(DiscountType type, long value) {
        return persistCode("LIM" + seq(), type, value, null, true);
    }

    @Test
    void maxUses_countsActiveBookings_cancelledFreesSlot() throws Exception {
        DiscountCode dc = code(DiscountType.FIXED_AMOUNT, 100_000);
        dc.setMaxUses(1);
        discountCodeRepository.save(dc);
        book(dc.getCode(), 0).andExpect(status().is3xxRedirection());
        Booking first = latest();
        book(dc.getCode(), 5).andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("hết lượt")));
        // Don dung ma bi huy -> tra lai luot
        first.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(first);
        book(dc.getCode(), 10).andExpect(status().is3xxRedirection());
    }

    @Test
    void validityWindow_minOrder_andPercentCap() throws Exception {
        DiscountCode expired = code(DiscountType.PERCENTAGE, 10);
        expired.setValidTo(LocalDate.now().minusDays(1));
        DiscountCode future = code(DiscountType.PERCENTAGE, 10);
        future.setValidFrom(LocalDate.now().plusDays(3));
        DiscountCode bigOrder = code(DiscountType.FIXED_AMOUNT, 200_000);
        bigOrder.setMinOrderAmount(BigDecimal.valueOf(3_000_000));
        DiscountCode capped = code(DiscountType.PERCENTAGE, 20);
        capped.setMaxDiscountAmount(BigDecimal.valueOf(150_000));
        discountCodeRepository.saveAll(java.util.List.of(expired, future, bigOrder, capped));

        book(expired.getCode(), 0).andExpect(model().attribute("errorMessage", containsString("hết hạn")));
        book(future.getCode(), 0).andExpect(model().attribute("errorMessage", containsString("áp dụng từ ngày")));
        book(bigOrder.getCode(), 0).andExpect(model().attribute("errorMessage", containsString("3.000.000")));
        // 20% x 2.000.000 = 400.000 nhung tran 150.000
        book(capped.getCode(), 0).andExpect(status().is3xxRedirection());
        assertThat(latest().getDiscountAmount()).isEqualByComparingTo("150000");

        // Nut "Kiem tra ma" bao giong luc dat
        String json = mockMvc.perform(get("/customer/bookings/check-discount").with(asNewCustomer()).param("code", expired.getCode())
                        .param("roomId", room.getId().toString()).param("checkIn", in.plusDays(20).toString())
                        .param("checkOut", in.plusDays(22).toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"valid\":false").contains("hết hạn");
    }

    @Test
    void groupCountsAsOneUse_adminFormAndStats() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/discount-codes/new").with(csrf()).with(as(admin))
                        .param("code", "doanhe").param("discountType", "PERCENTAGE").param("discountValue", "10")
                        .param("maxUses", "2").param("validFrom", LocalDate.now().toString()).param("validTo", LocalDate.now().plusDays(30).toString())
                        .param("maxDiscountAmount", "500000"))
                .andExpect(status().is3xxRedirection());
        DiscountCode dc = discountCodeRepository.findByCodeAndActiveTrue("DOANHE").orElseThrow();
        assertThat(dc.getMaxUses()).isEqualTo(2);
        assertThat(dc.getMaxDiscountAmount()).isEqualByComparingTo("500000");

        Room b = persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE);
        mockMvc.perform(post("/customer/bookings/group/new").with(csrf()).with(asNewCustomer())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Đoàn Mã").param("guestPhone", "0912345678").param("guestEmail", "doanma@mail.vn")
                        .param("roomIds", room.getId().toString()).param("guests_" + room.getId(), "2")
                        .param("roomIds", b.getId().toString()).param("guests_" + b.getId(), "2")
                        .param("discountCode", "DOANHE"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/discount-codes").with(as(admin))).andExpect(content().string(containsString("1 / 2")));
        mockMvc.perform(get("/admin/discount-codes/{id}/usage", dc.getId()).with(as(admin)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("400.000")));

        // Han ket thuc truoc han bat dau -> loi form
        mockMvc.perform(post("/admin/discount-codes/new").with(csrf()).with(as(admin))
                        .param("code", "SAINGAY").param("discountType", "FIXED_AMOUNT").param("discountValue", "100000")
                        .param("validFrom", LocalDate.now().plusDays(5).toString()).param("validTo", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ngày kết thúc phải từ ngày bắt đầu")));
    }

    @Test
    void chatbot_onlyMentionsUsableCodes() {
        DiscountCode ok = code(DiscountType.PERCENTAGE, 5);
        DiscountCode gone = code(DiscountType.PERCENTAGE, 7);
        gone.setValidTo(LocalDate.now().minusDays(2));
        discountCodeRepository.save(gone);
        String answer = chatbotService.answer("có mã giảm giá nào không");
        assertThat(answer).contains(ok.getCode()).doesNotContain(gone.getCode());
    }
}
