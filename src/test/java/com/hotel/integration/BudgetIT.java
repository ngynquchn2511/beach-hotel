package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.BudgetService;
import com.hotel.service.InsightService;
import com.hotel.service.ProfitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Ke hoach thang: nhap 12 thang, so thuc te / ke hoach tren trang lai lo, nhan dinh thieu bao nhieu phong-dem
class BudgetIT extends ItFixtures {

    @Autowired private BudgetService budgetService;
    @Autowired private ProfitService profitService;
    @Autowired private InsightService insightService;

    @Test
    void saveYear_varianceOnPnl_andShortfallInsight() throws Exception {
        Room room = persistRoom(persistRoomType("Hang KH IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        bookingRepository.save(Booking.builder().customer(guest).room(room).checkInDate(LocalDate.of(2025, 4, 10))
                .checkOutDate(LocalDate.of(2025, 4, 13)).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .numberOfGuests(2).guestName("A").guestPhone("0900000000").guestEmail(guest.getEmail())
                .totalAmount(BigDecimal.valueOf(3_000_000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());

        User accountant = persistUser(UserRole.ACCOUNTANT, null);
        mockMvc.perform(post("/admin/accounting/budget").with(csrf()).with(as(accountant)).param("year", "2025")
                        .param("revenue_4", "5000000").param("cost_4", "1000000").param("occupancy_4", "20").param("adr_4", "1000000"))
                .andExpect(flash().attribute("successMessage", containsString("2025")));
        assertThat(budgetService.of(YearMonth.of(2025, 4))).isPresent();
        assertThat(budgetService.of(YearMonth.of(2025, 5))).isEmpty();

        BudgetService.Variance v = budgetService.variance(profitService.month(YearMonth.of(2025, 4))).orElseThrow();
        assertThat(v.revenuePercent()).isEqualTo(60.0);
        assertThat(v.getRevenueShortfall()).isEqualByComparingTo("2000000");
        assertThat(v.adrPercent()).isEqualTo(100.0);
        assertThat(v.inProgress()).isFalse();

        assertThat(insightService.analyze(YearMonth.of(2025, 4))).anySatisfy(i -> {
            assertThat(i.title()).isEqualTo("Chỉ đạt 60.0% kế hoạch doanh thu");
            assertThat(i.detail()).contains("2.000.000").contains("2 phòng-đêm");
        });
        mockMvc.perform(get("/admin/accounting/pnl").with(as(accountant)).param("month", "2025-04"))
                .andExpect(content().string(containsString("So với kế hoạch")))
                .andExpect(content().string(containsString("Còn thiếu 2.000.000")));
        mockMvc.perform(get("/admin/accounting/budget").with(as(accountant)).param("year", "2025"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"5000000\"")));

        // Xoa trang ca 4 o -> bo ke hoach thang
        mockMvc.perform(post("/admin/accounting/budget").with(csrf()).with(as(accountant)).param("year", "2025"));
        assertThat(budgetService.of(YearMonth.of(2025, 4))).isEmpty();
        mockMvc.perform(post("/admin/accounting/budget").with(csrf()).with(as(accountant)).param("year", "2025").param("occupancy_1", "120"))
                .andExpect(flash().attribute("errorMessage", containsString("0 đến 100")));
    }
}
