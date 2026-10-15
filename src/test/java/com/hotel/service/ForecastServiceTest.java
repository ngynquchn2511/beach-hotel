package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Du bao cong suat: dem phong theo tung dem, tach don cho xac nhan, bo phong bao tri
@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private RoomRepository roomRepository;
    @InjectMocks private ForecastService forecastService;

    private final LocalDate day0 = LocalDate.of(2026, 11, 2);
    private final RoomType standard = RoomType.builder().id(1L).name("Tiêu chuẩn").build();
    private final RoomType suite = RoomType.builder().id(2L).name("Suite").build();

    private Room room(long id, RoomType type, RoomStatus status) {
        return Room.builder().id(id).roomNumber("10" + id).roomType(type).price(BigDecimal.valueOf(1_000_000)).status(status).build();
    }

    private Booking booking(Room room, int fromDay, int nights, BookingStatus status) {
        return Booking.builder().id((long) (fromDay * 10 + nights)).room(room).status(status)
                .checkInDate(day0.plusDays(fromDay)).checkOutDate(day0.plusDays(fromDay + nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .roomAmount(BigDecimal.valueOf(1_000_000L * nights)).totalAmount(BigDecimal.ZERO).build();
    }

    @Test
    void countsConfirmedAndPendingPerNight_excludesMaintenanceAndCheckedOut() {
        Room a = room(1, standard, RoomStatus.AVAILABLE);
        Room b = room(2, standard, RoomStatus.AVAILABLE);
        Room c = room(3, suite, RoomStatus.AVAILABLE);
        Room broken = room(4, suite, RoomStatus.MAINTENANCE);
        when(roomRepository.findAll()).thenReturn(List.of(a, b, c, broken));
        when(bookingRepository.findForCalendar(any(), any())).thenReturn(List.of(
                booking(a, 0, 2, BookingStatus.CONFIRMED),
                booking(b, 1, 1, BookingStatus.PENDING),
                booking(c, 0, 1, BookingStatus.CHECKED_IN),
                booking(b, 0, 1, BookingStatus.CHECKED_OUT)));

        ForecastService.Forecast f = forecastService.forecast(day0, 3);

        assertThat(f.days()).hasSize(3);
        ForecastService.Day d0 = f.days().get(0);
        assertThat(d0.sellable()).isEqualTo(3);
        assertThat(d0.confirmed()).isEqualTo(2);
        assertThat(d0.occupancy()).isEqualTo(67);
        assertThat(d0.revenue()).isEqualByComparingTo("2000000");
        ForecastService.Day d1 = f.days().get(1);
        assertThat(d1.confirmed()).isEqualTo(1);
        assertThat(d1.pending()).isEqualTo(1);
        assertThat(d1.free()).isEqualTo(1);
        assertThat(f.days().get(2).confirmed()).isZero();
        assertThat(f.days().get(2).low()).isTrue();
        assertThat(f.lowDays()).isEqualTo(2);
        // Tieu chuan: 2 phong x 3 dem = 6, ban 2 dem -> 33%; Suite: 1 phong (phong bao tri khong tinh) x 3 = 3, ban 1 -> 33%
        assertThat(f.types()).extracting(ForecastService.TypeRow::occupancy).containsOnly(33);
        assertThat(f.revenue()).isEqualByComparingTo("3000000");
    }
}
