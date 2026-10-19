package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.service.ExpenseService;
import com.hotel.service.ProfitService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Lai lo thang: doanh thu phong chia theo dem trong thang, dich vu theo ngay phat sinh, coc giu lai; chi phi cac loai
class ProfitIT extends ItFixtures {

    @Autowired private ProfitService profitService;
    @Autowired private ExpenseService expenseService;
    @Autowired private BookingChargeRepository chargeRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final YearMonth march = YearMonth.of(2025, 3);
    private Room room;

    @BeforeEach
    void seed() {
        room = persistRoom(persistRoomType("Hang LL IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private Booking stay(LocalDate in, int nights, long total, BookingStatus status) {
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        return bookingRepository.save(Booking.builder().customer(guest).room(room).checkInDate(in).checkOutDate(in.plusDays(nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(2)
                .guestName(guest.getFullName()).guestPhone("0900000000").guestEmail(guest.getEmail())
                .totalAmount(BigDecimal.valueOf(total)).discountAmount(BigDecimal.ZERO).status(status).build());
    }

    @Test
    void monthPnl_splitsNightsAcrossMonths_addsServicesCostsAndFees() throws Exception {
        // 30/3 -> 2/4: 4 dem, 2 dem trong thang 3 -> 2.000.000 / 4.000.000
        Booking crossing = stay(LocalDate.of(2025, 3, 30), 4, 4_000_000, BookingStatus.CHECKED_OUT);
        stay(LocalDate.of(2025, 3, 10), 2, 2_000_000, BookingStatus.CHECKED_OUT);
        // Huy giu 300k coc
        Booking cancelled = stay(LocalDate.of(2025, 3, 20), 1, 1_000_000, BookingStatus.CANCELLED);
        cancelled.setCancelledAt(LocalDateTime.of(2025, 3, 15, 9, 0));
        cancelled.setDepositAmount(BigDecimal.valueOf(300_000));
        cancelled.setDepositPaidAt(LocalDateTime.of(2025, 3, 1, 9, 0));
        cancelled.setRefundPercent(0);
        cancelled.setRefundAmount(BigDecimal.ZERO);
        bookingRepository.save(cancelled);
        BookingCharge food = chargeRepository.save(BookingCharge.builder().booking(crossing).type(ChargeType.ROOM_SERVICE)
                .description("Ăn tối").amount(BigDecimal.valueOf(1_000_000)).build());
        // created_at khong cap nhat qua JPA (updatable = false) -> ghi thang vao DB nhu du lieu that
        food.setCreatedAt(LocalDateTime.of(2025, 3, 31, 20, 0));
        chargeRepository.flush();
        jdbcTemplate.update("update booking_charges set created_at = ? where id = ?", LocalDateTime.of(2025, 3, 31, 20, 0), food.getId());

        expenseService.updateCostSettings(Map.of(ChargeType.ROOM_SERVICE, 40), BigDecimal.ZERO, BigDecimal.valueOf(100_000));
        expenseService.create(Expense.Category.PAYROLL, "Lương", BigDecimal.valueOf(1_500_000), Expense.Recurrence.MONTHLY,
                null, march, null, "test");

        ProfitService.MonthPnl m = profitService.month(march);
        assertThat(m.roomRevenue()).isEqualByComparingTo("4000000");
        assertThat(m.serviceRevenueTotal()).isEqualByComparingTo("1000000");
        assertThat(m.forfeitedDeposits()).isEqualByComparingTo("300000");
        assertThat(m.totalRevenue()).isEqualByComparingTo("5300000");
        assertThat(m.soldRoomNights()).isEqualTo(4);
        // Gia von 40% x 1tr + 4 phong-dem x 100k
        assertThat(m.directCost()).isEqualByComparingTo("800000");
        assertThat(m.totalCost()).isEqualByComparingTo("2300000");
        assertThat(m.operatingProfit()).isEqualByComparingTo("3000000");
        assertThat(m.margin()).isEqualTo(56.6);
        assertThat(m.cpor()).isEqualByComparingTo("575000");

        ProfitService.MonthPnl april = profitService.month(march.plusMonths(1));
        assertThat(april.roomRevenue()).isEqualByComparingTo("2000000");

        ProfitService.Report report = profitService.report(march);
        assertThat(report.trend()).hasSize(12);
        assertThat(report.trend().get(11).month()).isEqualTo(march);

        User accountant = persistUser(UserRole.ACCOUNTANT, null);
        mockMvc.perform(get("/admin/accounting/pnl").with(as(accountant)).param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5.300.000")))
                .andExpect(content().string(containsString("56.6%")))
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("Lãi vận hành: 3.000.000 VND")));
        byte[] xlsx = mockMvc.perform(get("/admin/accounting/pnl/export").with(as(accountant)).param("month", "2025-03"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
        }
        mockMvc.perform(get("/admin/accounting/pnl").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
    }
}
