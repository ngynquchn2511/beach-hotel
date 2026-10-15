package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Du bao cong suat cac ngay toi: dem phong da giu (da xac nhan / dang o) va cho xac nhan moi dem,
// doanh thu phong du kien (tien phong cua don chia deu theo dem). Ngay vang -> goi y bat khuyen mai gio chot
@Service
public class ForecastService {

    public static final int LOW_OCCUPANCY_PERCENT = 40;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public ForecastService(BookingRepository bookingRepository, RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    public record Day(LocalDate date, int sellable, int confirmed, int pending, BigDecimal revenue) {
        public int free() {
            return Math.max(0, sellable - confirmed - pending);
        }

        // Cong suat tinh tren phong da chac chan (da xac nhan / dang o)
        public int occupancy() {
            return sellable == 0 ? 0 : Math.round(100f * confirmed / sellable);
        }

        public int pendingPercent() {
            return sellable == 0 ? 0 : Math.round(100f * pending / sellable);
        }

        public boolean low() {
            return occupancy() < LOW_OCCUPANCY_PERCENT;
        }

        public boolean weekend() {
            return date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY;
        }
    }

    public record TypeRow(String name, int rooms, int bookedNights, int occupancy) {
    }

    public record Forecast(List<Day> days, List<TypeRow> types, int averageOccupancy, BigDecimal revenue, int lowDays) {
    }

    @Transactional(readOnly = true)
    public Forecast forecast(LocalDate from, int days) {
        LocalDate to = from.plusDays(days);
        List<Room> rooms = roomRepository.findAll();
        int sellable = (int) rooms.stream().filter(r -> r.getStatus() != RoomStatus.MAINTENANCE).count();
        List<Booking> bookings = bookingRepository.findForCalendar(from, to).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED
                        || b.getStatus() == BookingStatus.CHECKED_IN)
                .toList();

        List<Day> result = new ArrayList<>();
        Map<Long, Integer> nightsByType = new LinkedHashMap<>();
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            int confirmed = 0;
            int pending = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            for (Booking b : bookings) {
                if (b.getCheckInDate().isAfter(d) || !b.getCheckOutDate().isAfter(d)) {
                    continue;
                }
                if (b.getStatus() == BookingStatus.PENDING) {
                    pending++;
                } else {
                    confirmed++;
                    revenue = revenue.add(perNight(b));
                    nightsByType.merge(b.getRoom().getRoomType().getId(), 1, Integer::sum);
                }
            }
            result.add(new Day(d, sellable, confirmed, pending, revenue));
        }

        Map<Long, RoomType> types = new LinkedHashMap<>();
        Map<Long, Integer> roomsByType = new LinkedHashMap<>();
        for (Room r : rooms) {
            if (r.getStatus() == RoomStatus.MAINTENANCE) {
                continue;
            }
            types.putIfAbsent(r.getRoomType().getId(), r.getRoomType());
            roomsByType.merge(r.getRoomType().getId(), 1, Integer::sum);
        }
        List<TypeRow> typeRows = types.values().stream()
                .map(t -> {
                    int count = roomsByType.getOrDefault(t.getId(), 0);
                    int nights = nightsByType.getOrDefault(t.getId(), 0);
                    int capacity = count * days;
                    return new TypeRow(t.getName(), count, nights, capacity == 0 ? 0 : Math.round(100f * nights / capacity));
                })
                .sorted(Comparator.comparingInt(TypeRow::occupancy))
                .toList();

        int average = result.isEmpty() ? 0 : (int) Math.round(result.stream().mapToInt(Day::occupancy).average().orElse(0));
        BigDecimal revenue = result.stream().map(Day::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        int low = (int) result.stream().filter(Day::low).count();
        return new Forecast(result, typeRows, average, revenue, low);
    }

    // Tien phong 1 dem cua don (da tru khuyen mai / ma giam gia theo ty le tren tong don thi phuc tap -> lay tien phong chia deu)
    private static BigDecimal perNight(Booking b) {
        long nights = Math.max(1, b.getNights());
        return b.getEffectiveRoomAmount().divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP);
    }
}
