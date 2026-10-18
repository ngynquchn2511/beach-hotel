package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.BudgetService;
import com.hotel.service.MonthlyReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

// Bao cao quan tri thang: noi dung email (ket qua, ke hoach, nhan dinh) va nut gui ngay
class MonthlyReportIT extends ItFixtures {

    @Autowired private MonthlyReportService reportService;
    @Autowired private BudgetService budgetService;

    @Test
    void text_containsResultsBudgetAndInsights_sendButton() throws Exception {
        Room room = persistRoom(persistRoomType("Hang BCT IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        bookingRepository.save(Booking.builder().customer(guest).room(room).checkInDate(LocalDate.of(2025, 9, 5))
                .checkOutDate(LocalDate.of(2025, 9, 7)).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .numberOfGuests(2).guestName("A").guestPhone("0900000000").guestEmail(guest.getEmail())
                .totalAmount(BigDecimal.valueOf(2_000_000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());
        budgetService.save(YearMonth.of(2025, 9), BigDecimal.valueOf(4_000_000), null, null, null);

        String text = reportService.text(YearMonth.of(2025, 9));
        assertThat(text).contains("BÁO CÁO QUẢN TRỊ THÁNG 9/2025")
                .contains("Doanh thu: 2.000.000 VND")
                .contains("Doanh thu đạt 50.0% kế hoạch (thiếu 2.000.000 VND)")
                .contains("CẦN CHÚ Ý");
        assertThat(MonthlyReportService.subject(YearMonth.of(2025, 9))).contains("9/2025");

        User admin = persistUser(UserRole.ADMIN, null);
        // Moi truong test co cau hinh mail -> bao da gui toi cac tai khoan ADMIN
        mockMvc.perform(post("/admin/accounting/insights/email").with(csrf()).with(as(admin)).param("month", "2025-09"))
                .andExpect(flash().attribute("successMessage", containsString(admin.getEmail())));
    }
}
