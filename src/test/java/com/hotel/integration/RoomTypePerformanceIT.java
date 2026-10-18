package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.ExpenseService;
import com.hotel.service.RoomTypePerformanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

// Hieu qua theo loai phong: cong suat / ADR / RevPAR / dong gop theo tung loai, xep theo dong gop moi phong-dem san co
class RoomTypePerformanceIT extends ItFixtures {

    @Autowired private RoomTypePerformanceService service;
    @Autowired private ExpenseService expenseService;

    @Test
    void perTypeMetrics_sortedByContributionPerAvailableNight() throws Exception {
        RoomType villa = persistRoomType("Villa HQ IT", 3_000_000, 4);
        RoomType standard = persistRoomType("Standard HQ IT", 800_000, 2);
        Room v1 = persistRoom(villa, 3_000_000, RoomStatus.AVAILABLE);
        persistRoom(standard, 800_000, RoomStatus.AVAILABLE);
        Room s2 = persistRoom(standard, 800_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        for (Object[] x : new Object[][]{{v1, 3, 9_000_000L}, {s2, 10, 8_000_000L}}) {
            bookingRepository.save(Booking.builder().customer(guest).room((Room) x[0]).checkInDate(LocalDate.of(2025, 11, 3))
                    .checkOutDate(LocalDate.of(2025, 11, 3).plusDays((Integer) x[1])).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                    .numberOfGuests(2).guestName("A").guestPhone("0900000000").guestEmail(guest.getEmail())
                    .totalAmount(BigDecimal.valueOf((Long) x[2])).discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());
        }
        expenseService.updateCostSettings(Map.of(), BigDecimal.ZERO, BigDecimal.valueOf(100_000));

        List<RoomTypePerformanceService.Row> rows = service.month(YearMonth.of(2025, 11)).stream()
                .filter(r -> r.type().getName().endsWith("HQ IT")).toList();
        assertThat(rows).extracting(r -> r.type().getName()).containsExactly("Villa HQ IT", "Standard HQ IT");
        RoomTypePerformanceService.Row v = rows.get(0);
        assertThat(v.rooms()).isEqualTo(1);
        assertThat(v.soldNights()).isEqualTo(3);
        assertThat(v.occupancy()).isEqualTo(10.0);
        assertThat(v.adr()).isEqualByComparingTo("3000000");
        // (9tr - 3 x 100k) / 30 phong-dem
        assertThat(v.contributionPerAvailableNight()).isEqualByComparingTo("290000");
        RoomTypePerformanceService.Row s = rows.get(1);
        assertThat(s.availableNights()).isEqualTo(60);
        assertThat(s.occupancy()).isEqualTo(16.7);

        mockMvc.perform(get("/admin/accounting/pnl").with(as(persistUser(UserRole.ACCOUNTANT, null))).param("month", "2025-11"))
                .andExpect(content().string(containsString("Hiệu quả theo loại phòng")))
                .andExpect(content().string(containsString("Villa HQ IT")));
    }
}
