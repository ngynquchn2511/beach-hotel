package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.RevenueForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Du bao doanh thu theo pickup: tren so + luong dat them cung ky nam truoc x toc do ban; don huy sau thoi diem van tinh la "co tren so" luc do
class RevenueForecastIT extends ItFixtures {

    @Autowired private RevenueForecastService forecastService;
    @Autowired private com.hotel.repository.BookingChargeRepository chargeRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Room room;
    // Moc co dinh: hom nay 15/6/2025 -> du bao thang 6, 7, 8
    private final LocalDate today = LocalDate.of(2025, 6, 15);

    @BeforeEach
    void seed() {
        room = persistRoom(persistRoomType("Hang DB IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private Booking booking(LocalDate in, int nights, long total, BookingStatus status, LocalDateTime createdAt) {
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking b = bookingRepository.save(Booking.builder().customer(guest).room(room).checkInDate(in).checkOutDate(in.plusDays(nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(2)
                .guestName("A").guestPhone("0900000000").guestEmail(guest.getEmail())
                .totalAmount(BigDecimal.valueOf(total)).discountAmount(BigDecimal.ZERO).status(status).build());
        b.setCreatedAt(createdAt);
        return bookingRepository.save(b);
    }

    @Test
    void pickupFromLastYear_scaledByPace() {
        // Nam ngoai thang 7: luc 15/6/2024 da co 2tr tren so, ket qua cuoi thang 6tr -> pickup 4tr
        booking(LocalDate.of(2024, 7, 10), 2, 2_000_000, BookingStatus.CHECKED_OUT, LocalDateTime.of(2024, 5, 1, 9, 0));
        booking(LocalDate.of(2024, 7, 20), 4, 4_000_000, BookingStatus.CHECKED_OUT, LocalDateTime.of(2024, 7, 1, 9, 0));
        // Nam nay thang 7: da co 3tr tren so -> toc do 1.5 -> du bao 3 + 4 x 1.5 = 9tr
        booking(LocalDate.of(2025, 7, 5), 3, 3_000_000, BookingStatus.CONFIRMED, LocalDateTime.of(2025, 6, 1, 9, 0));
        // Don dang cho xac nhan khong tinh tren so
        booking(LocalDate.of(2025, 7, 25), 2, 2_000_000, BookingStatus.PENDING, LocalDateTime.of(2025, 6, 10, 9, 0));

        List<RevenueForecastService.MonthForecast> list = forecastService.forecast(today);
        assertThat(list).hasSize(3);
        RevenueForecastService.MonthForecast july = list.get(1);
        assertThat(july.onTheBooks().revenue()).isEqualByComparingTo("3000000");
        assertThat(july.lastYearAtSameLead().revenue()).isEqualByComparingTo("2000000");
        assertThat(july.lastYearFinal().revenue()).isEqualByComparingTo("6000000");
        assertThat(july.pace()).isEqualTo(1.5);
        assertThat(july.forecastRevenue()).isEqualByComparingTo("9000000");
        assertThat(july.getPickup()).isEqualByComparingTo("6000000");
        assertThat(july.confidence()).isEqualTo(RevenueForecastService.Confidence.MEDIUM);

        // Thang 8 khong co du lieu nam ngoai -> chi tren so, tin cay thap
        assertThat(list.get(2).confidence()).isEqualTo(RevenueForecastService.Confidence.LOW);
    }

    private void charge(Booking b, long amount, LocalDateTime at) {
        BookingCharge c = chargeRepository.save(BookingCharge.builder().booking(b).type(ChargeType.MINIBAR).description("Minibar")
                .amount(BigDecimal.valueOf(amount)).build());
        c.setCreatedAt(at);
        chargeRepository.flush();
        // created_at khong cap nhat qua JPA (updatable = false) -> ghi thang vao DB nhu du lieu that
        jdbcTemplate.update("update booking_charges set created_at = ? where id = ?", at, c.getId());
    }

    @Test
    void serviceRevenue_fromLastYearSpendPerNight_andTotalVsTarget() {
        // Nam ngoai thang 7: 6 phong-dem, 600k dich vu -> 100k / phong-dem
        Booking a = booking(LocalDate.of(2024, 7, 10), 2, 2_000_000, BookingStatus.CHECKED_OUT, LocalDateTime.of(2024, 5, 1, 9, 0));
        booking(LocalDate.of(2024, 7, 20), 4, 4_000_000, BookingStatus.CHECKED_OUT, LocalDateTime.of(2024, 7, 1, 9, 0));
        charge(a, 600_000, LocalDateTime.of(2024, 7, 11, 20, 0));
        // Phu phi cua don da huy khong tinh
        Booking x = booking(LocalDate.of(2024, 7, 25), 1, 1_000_000, BookingStatus.CANCELLED, LocalDateTime.of(2024, 6, 1, 9, 0));
        charge(x, 5_000_000, LocalDateTime.of(2024, 7, 12, 20, 0));
        // Nam nay thang 7: 3tr tren so (3 dem) -> du bao phong 9tr = 9 dem x ADR 1tr -> dich vu 9 x 100k
        booking(LocalDate.of(2025, 7, 5), 3, 3_000_000, BookingStatus.CONFIRMED, LocalDateTime.of(2025, 6, 1, 9, 0));

        RevenueForecastService.MonthForecast july = forecastService.forecast(today).get(1);
        assertThat(july.forecastNights()).isEqualTo(9);
        assertThat(july.serviceSpendPerNight()).isEqualByComparingTo("100000");
        assertThat(july.serviceBasis()).isEqualTo("cùng kỳ năm trước");
        assertThat(july.forecastServiceRevenue()).isEqualByComparingTo("900000");
        assertThat(july.forecastTotalRevenue()).isEqualByComparingTo("9900000");
    }

    @Test
    void cancelledAfterLeadDate_countedAsOnBooksThen_page() throws Exception {
        // Nam ngoai: don tao 1/6/2024, huy 1/7/2024 -> luc 15/6/2024 van con tren so, nhung khong vao ket qua cuoi
        Booking c = booking(LocalDate.of(2024, 7, 10), 2, 2_000_000, BookingStatus.CANCELLED, LocalDateTime.of(2024, 6, 1, 9, 0));
        c.setCancelledAt(LocalDateTime.of(2024, 7, 1, 9, 0));
        bookingRepository.save(c);
        booking(LocalDate.of(2024, 7, 15), 1, 1_000_000, BookingStatus.CHECKED_OUT, LocalDateTime.of(2024, 6, 2, 9, 0));
        RevenueForecastService.MonthForecast july = forecastService.forecast(today).get(1);
        assertThat(july.lastYearAtSameLead().revenue()).isEqualByComparingTo("3000000");
        assertThat(july.lastYearFinal().revenue()).isEqualByComparingTo("1000000");

        mockMvc.perform(get("/admin/accounting/revenue-forecast").with(as(persistUser(UserRole.ACCOUNTANT, null))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đã có trên sổ")));
        mockMvc.perform(get("/admin/accounting/revenue-forecast").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
    }
}
