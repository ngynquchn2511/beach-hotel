package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gia thanh vien: khach dang nhap duoc giam them % tien phong theo hang; khach vang lai chi thay loi moi
class MemberPriceIT extends ItFixtures {

    private Room room;
    private User regular;
    private LocalDate in;

    @BeforeEach
    void seed() throws Exception {
        // Ngay thuong lien tiep de khong dinh phu thu cuoi tuan (mac dinh 0%)
        room = persistRoom(persistRoomType("Hang Thanh Vien IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        regular = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        in = LocalDate.now().plusDays(40);
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/pricing/member-discounts").with(csrf()).with(as(admin))
                        .param("memberNew", "3").param("memberRegular", "5").param("memberVip", "8"))
                .andExpect(flash().attribute("successMessage", containsString("giá thành viên")));
    }

    private ResultActions book(User who, int nights) throws Exception {
        MockHttpServletRequestBuilder req = post("/customer/bookings/new").with(csrf())
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(nights).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Khách Thành Viên").param("guestPhone", "0912345678").param("guestEmail", "tv" + nights + "@mail.vn");
        if (who != null) {
            req.with(as(who));
        }
        return mockMvc.perform(req);
    }

    private Booking latest() {
        return bookingRepository.findAll().stream().max(java.util.Comparator.comparing(Booking::getId)).orElseThrow();
    }

    @Test
    void loggedInMember_getsTierDiscount_recalculatedOnDateChange() throws Exception {
        mockMvc.perform(get("/customer/bookings/new").with(as(regular)).param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(content().string(containsString("Giá thành viên (-5%): giảm 100.000 VND")));
        mockMvc.perform(get("/customer/rooms/{id}", room.getId()).with(as(regular)))
                .andExpect(content().string(containsString("Giá thành viên của bạn: 950.000 VND")));

        book(regular, 2).andExpect(status().is3xxRedirection());
        Booking b = latest();
        assertThat(b.getMemberDiscountPercent()).isEqualTo(5);
        assertThat(b.getMemberDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(b.getTotalAmount()).isEqualByComparingTo(b.getRoomAmount().subtract(b.getPromotionAmountOrZero())
                .subtract(b.getMemberDiscountAmount()));

        mockMvc.perform(post("/customer/bookings/{id}/change-dates", b.getId()).with(csrf()).with(as(regular))
                .param("checkIn", in.toString()).param("checkOut", in.plusDays(3).toString()));
        Booking changed = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(changed.getCheckOutDate()).isEqualTo(in.plusDays(3));
        assertThat(changed.getMemberDiscountAmount()).isEqualByComparingTo(
                changed.getRoomAmount().subtract(changed.getPromotionAmountOrZero()).multiply(java.math.BigDecimal.valueOf(5))
                        .divide(java.math.BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP));
        assertThat(changed.getTotalAmount()).isEqualByComparingTo(changed.getRoomAmount().subtract(changed.getPromotionAmountOrZero())
                .subtract(changed.getMemberDiscountAmount()));
    }

    @Test
    void guestBooking_noMemberDiscount_butSeesInvite() throws Exception {
        mockMvc.perform(get("/customer/bookings/new").param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(content().string(containsString("giảm thêm đến 8%")))
                .andExpect(content().string(not(containsString("Giá thành viên (-"))));
        book(null, 2).andExpect(status().is3xxRedirection());
        assertThat(latest().getMemberDiscountAmount()).isNull();
    }

    @Test
    void invalidSettings_rejected_andCustomerCannotChange() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/pricing/member-discounts").with(csrf()).with(as(admin))
                        .param("memberNew", "10").param("memberRegular", "5").param("memberVip", "8"))
                .andExpect(flash().attribute("errorMessage", containsString("tăng dần")));
        mockMvc.perform(post("/admin/pricing/member-discounts").with(csrf()).with(as(admin))
                        .param("memberNew", "0").param("memberRegular", "5").param("memberVip", "31"))
                .andExpect(flash().attribute("errorMessage", containsString("từ 0 đến 30")));
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/pricing/member-discounts").with(csrf()).with(as(staff))
                .param("memberNew", "0").param("memberRegular", "0").param("memberVip", "0")).andExpect(status().isForbidden());
    }
}
