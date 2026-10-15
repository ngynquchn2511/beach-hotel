package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.CashShiftRepository;
import com.hotel.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// So quy ca: nhan ca -> thu tien trong ca -> dong ca doi chieu tien mat -> ca sau nhan ban giao
class CashShiftIT extends ItFixtures {

    @Autowired private CashShiftRepository shiftRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User morning;
    private User evening;
    private Booking booking;

    @BeforeEach
    void seed() {
        morning = persistUser(UserRole.STAFF, null);
        evening = persistUser(UserRole.STAFF, null);
        Room room = persistRoom(persistRoomType("Hang Ca IT", 700_000, 2), 700_000, RoomStatus.AVAILABLE);
        booking = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CHECKED_OUT, -1, 1);
    }

    @Test
    void fullShiftCycle_withHandover() throws Exception {
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(morning)).param("openingCash", "500000"))
                .andExpect(flash().attribute("successMessage", containsString("Đã nhận ca")));
        paymentRepository.save(Payment.builder().booking(booking).amount(BigDecimal.valueOf(700_000)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(LocalDateTime.now()).build());

        mockMvc.perform(get("/admin/shifts").with(as(morning)))
                .andExpect(content().string(containsString("Thanh toán đơn #" + booking.getId())))
                .andExpect(content().string(containsString("1.200.000 đ")));

        // Chi 1 ca mo
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(evening)).param("openingCash", "0"))
                .andExpect(flash().attribute("errorMessage", containsString("chưa đóng")));

        CashShift shift = shiftRepository.findAll().get(0);
        // Thieu 100.000 khong ghi ly do -> khong cho dong
        mockMvc.perform(post("/admin/shifts/{id}/close", shift.getId()).with(csrf()).with(as(morning))
                        .param("countedCash", "1100000").param("cashToSafe", "1000000"))
                .andExpect(flash().attribute("errorMessage", containsString("lý do")));
        mockMvc.perform(post("/admin/shifts/{id}/close", shift.getId()).with(csrf()).with(as(morning))
                        .param("countedCash", "1100000").param("cashToSafe", "1000000")
                        .param("handoverNote", "Thối nhầm khách 100k. Phòng 305 xin trả muộn 14h"))
                .andExpect(redirectedUrl("/admin/shifts/" + shift.getId()));

        CashShift closed = shiftRepository.findById(shift.getId()).orElseThrow();
        assertThat(closed.getCashIn()).isEqualByComparingTo("700000");
        assertThat(closed.getCashDifference()).isEqualByComparingTo("-100000");
        assertThat(closed.getLeftCash()).isEqualByComparingTo("100000");

        // Ca sau thay ghi chu ban giao, nhan ca lech 50.000 so voi ca truoc de lai
        mockMvc.perform(get("/admin/shifts").with(as(evening)))
                .andExpect(content().string(containsString("Phòng 305 xin trả muộn")));
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(evening)).param("openingCash", "150000"));
        CashShift next = shiftRepository.findAll().stream().filter(CashShift::isOpen).findFirst().orElseThrow();
        assertThat(next.getOpeningDifference()).isEqualByComparingTo("50000");
        mockMvc.perform(get("/admin/shifts").with(as(evening)))
                .andExpect(content().string(containsString("lệch 50.000 đ")));
    }

    @Test
    void invalidAmounts_andCustomerForbidden() throws Exception {
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(morning)).param("openingCash", "-1"))
                .andExpect(flash().attribute("errorMessage", containsString("không âm")));
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(morning)).param("openingCash", "0"));
        CashShift shift = shiftRepository.findAll().get(0);
        mockMvc.perform(post("/admin/shifts/{id}/close", shift.getId()).with(csrf()).with(as(morning))
                        .param("countedCash", "0").param("cashToSafe", "10000"))
                .andExpect(flash().attribute("errorMessage", containsString("không thể lớn hơn")));
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(get("/admin/shifts").with(as(customer))).andExpect(status().isForbidden());
    }
}
