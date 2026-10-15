package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PaymentRepository;
import com.hotel.service.PeriodComparisonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

// Dashboard: so voi ky lien truoc va cung ky nam truoc
class PeriodComparisonIT extends ItFixtures {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PeriodComparisonService comparisonService;

    private void paid(int daysAgo, long amount) {
        Room room = persistRoom(persistRoomType("Hang SS IT " + daysAgo, 500_000, 2), 500_000, RoomStatus.AVAILABLE);
        Booking b = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CHECKED_OUT, -daysAgo - 1, 1);
        paymentRepository.save(Payment.builder().booking(b).amount(BigDecimal.valueOf(amount)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(LocalDateTime.now().minusDays(daysAgo)).build());
    }

    @Test
    void comparesWithPreviousPeriodAndLastYear() throws Exception {
        LocalDate today = LocalDate.now();
        paid(0, 3_000_000);
        paid(1, 2_000_000);
        paid((int) java.time.temporal.ChronoUnit.DAYS.between(today.minusYears(1), today), 1_500_000);

        PeriodComparisonService.Comparison c = comparisonService.compare(today, today);
        assertThat(c.current().paidTotal()).isEqualByComparingTo("3000000");
        assertThat(c.rows().get(0).snapshot().paidTotal()).isEqualByComparingTo("2000000");
        assertThat(c.rows().get(0).delta().paidPercent()).isEqualTo(50.0);
        assertThat(c.rows().get(1).snapshot().from()).isEqualTo(today.minusYears(1));
        assertThat(c.rows().get(1).delta().paidPercent()).isEqualTo(100.0);

        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(get("/admin/dashboard").with(as(staff)).param("fromDate", today.toString()).param("toDate", today.toString()))
                .andExpect(content().string(containsString("So sánh cùng kỳ")))
                .andExpect(content().string(containsString("Cùng kỳ năm trước")))
                .andExpect(content().string(containsString("▲ +50")))
                .andExpect(content().string(containsString("▲ +100")));
    }

    @Test
    void emptyPeriod_hasNoPercent() {
        LocalDate far = LocalDate.now().plusYears(3);
        PeriodComparisonService.Comparison c = comparisonService.compare(far, far.plusDays(6));
        assertThat(c.rows().get(0).snapshot().from()).isEqualTo(far.minusDays(7));
        assertThat(c.rows().get(0).delta().paidPercent()).isNull();
        assertThat(c.rows().get(0).delta().occupancyPoints()).isZero();
    }
}
