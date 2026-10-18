package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.CancellationReportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bao cao huy / khong den: dem theo thang + kenh, coc giu lai, huy sat ngay, khach huy nhieu lan
class CancellationReportIT extends ItFixtures {

    @Autowired private CancellationReportService reportService;

    @Test
    void build_countsCancelNoShowLateAndRepeatGuests() throws Exception {
        User flaky = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        User good = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        RoomType type = persistRoomType("Hang HK IT", 1_000_000, 2);
        persistBooking(good, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 20, 2);
        // Huy som, mat 300k coc
        Booking early = persistBooking(flaky, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CANCELLED, 20, 2);
        early.setCancelledAt(LocalDateTime.now());
        early.setDepositAmount(BigDecimal.valueOf(300_000));
        early.setDepositPaidAt(LocalDateTime.now().minusDays(1));
        early.setRefundPercent(0);
        early.setRefundAmount(BigDecimal.ZERO);
        // Huy sat ngay (nhan phong ngay mai)
        Booking late = persistBooking(good, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CANCELLED, 1, 1);
        late.setCancelledAt(LocalDateTime.now());
        // Khong den
        Booking ns = persistBooking(flaky, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CANCELLED, -1, 1);
        ns.setCancelledAt(LocalDateTime.now());
        ns.setNoShow(true);
        bookingRepository.saveAll(java.util.List.of(early, late, ns));

        YearMonth now = YearMonth.now();
        CancellationReportService.Report r = reportService.build(now.minusMonths(2), now);
        assertThat(r.months()).hasSize(3);
        CancellationReportService.Line month = r.months().get(2);
        assertThat(month.created()).isGreaterThanOrEqualTo(4);
        assertThat(month.cancelled()).isGreaterThanOrEqualTo(2);
        assertThat(month.noShows()).isGreaterThanOrEqualTo(1);
        assertThat(month.lateCancels()).isGreaterThanOrEqualTo(1);
        assertThat(month.forfeited()).isGreaterThanOrEqualTo(BigDecimal.valueOf(300_000));
        assertThat(r.repeatGuests()).anySatisfy(g -> {
            assertThat(g.customer().getId()).isEqualTo(flaky.getId());
            assertThat(g.cancelled()).isEqualTo(1);
            assertThat(g.noShows()).isEqualTo(1);
        });
        assertThat(r.repeatGuests()).noneMatch(g -> g.customer().getId().equals(good.getId()));

        mockMvc.perform(get("/admin/reports/cancellations").with(as(persistUser(UserRole.ACCOUNTANT, null))))
                .andExpect(status().isOk()).andExpect(content().string(containsString(flaky.getFullName())));
        byte[] xlsx = mockMvc.perform(get("/admin/reports/cancellations/export").with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheet("Khach huy nhieu lan").getLastRowNum()).isGreaterThanOrEqualTo(1);
        }
        mockMvc.perform(get("/admin/reports/cancellations").with(as(persistUser(UserRole.HOUSEKEEPER, null)))).andExpect(status().isForbidden());
    }

    @Test
    void rangeIsSwappedAndCapped() {
        YearMonth[] r = com.hotel.controller.AdminCancellationReportControllerAccess.range(YearMonth.of(2026, 5), YearMonth.of(2023, 1));
        assertThat(r[0]).isEqualTo(YearMonth.of(2024, 6));
        assertThat(r[1]).isEqualTo(YearMonth.of(2026, 5));
    }
}
