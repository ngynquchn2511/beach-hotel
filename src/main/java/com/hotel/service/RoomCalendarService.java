package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Lich phong dang timeline cho le tan: moi hang la 1 phong, moi cot la 1 ngay, moi don la 1 thanh ngang.
// Moi ngay chia 2 nua cot: khach nhan phong buoi chieu -> thanh bat dau giua ngay nhan,
// tra phong buoi trua -> thanh ket thuc giua ngay tra. Nho vay 2 don noi tiep cung phong khong de len nhau.
@Service
public class RoomCalendarService {

    public static final List<Integer> ALLOWED_DAYS = List.of(7, 14, 30);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomCalendarService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public Calendar build(LocalDate from, int days, LocalDate today) {
        LocalDate to = from.plusDays(days);

        List<Day> dayList = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            dayList.add(new Day(d, i, weekend, d.equals(today), !d.isBefore(today)));
        }

        List<Room> rooms = roomRepository.findAll().stream()
                .sorted(Comparator.comparing((Room r) -> r.getRoomType().getName())
                        .thenComparing(Room::getRoomNumber))
                .toList();

        Map<Long, List<Booking>> bookingsByRoom = bookingRepository.findForCalendar(from, to).stream()
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()));

        List<RoomRow> rows = rooms.stream()
                .map(room -> new RoomRow(room, bookingsByRoom.getOrDefault(room.getId(), List.of()).stream()
                        .map(b -> toBar(b, from, to, days))
                        .toList()))
                .toList();

        // So phong con trong tung dem: phong khong bao tri va khong co don nao o qua dem do
        long sellable = rooms.stream().filter(r -> r.getStatus() != RoomStatus.MAINTENANCE).count();
        List<Long> freeRooms = dayList.stream()
                .map(day -> sellable - rows.stream()
                        .filter(row -> row.room().getStatus() != RoomStatus.MAINTENANCE)
                        .filter(row -> row.bars().stream().anyMatch(bar -> bar.coversNight(day.date())))
                        .count())
                .toList();

        return new Calendar(from, to, days, dayList, rows, freeRooms);
    }

    static Bar toBar(Booking b, LocalDate from, LocalDate to, int days) {
        boolean clippedLeft = b.getCheckInDate().isBefore(from);
        boolean clippedRight = !b.getCheckOutDate().isBefore(to);
        // Chi so nua-cot (0-based): nua sau cua ngay nhan -> nua dau cua ngay tra (tinh ca nua dau)
        int startHalf = clippedLeft ? 0 : 2 * (int) ChronoUnit.DAYS.between(from, b.getCheckInDate()) + 1;
        int endHalf = clippedRight ? 2 * days : 2 * (int) ChronoUnit.DAYS.between(from, b.getCheckOutDate()) + 1;
        // grid-column trong CSS danh so tu 1, dau cuoi la duong ke -> +1
        return new Bar(b, startHalf + 1, endHalf + 1, clippedLeft, clippedRight);
    }

    public record Calendar(LocalDate from, LocalDate to, int days, List<Day> dayList,
                           List<RoomRow> rows, List<Long> freeRooms) {
        public LocalDate prevFrom() { return from.minusDays(days); }
        public LocalDate nextFrom() { return from.plusDays(days); }
        public int halfColumns() { return 2 * days; }
    }

    public record Day(LocalDate date, int index, boolean weekend, boolean today, boolean bookable) {
        public int gridStart() { return 2 * index + 1; }
    }

    public record RoomRow(Room room, List<Bar> bars) { }

    public record Bar(Booking booking, int gridStart, int gridEnd, boolean clippedLeft, boolean clippedRight) {
        boolean coversNight(LocalDate night) {
            return !booking.getCheckInDate().isAfter(night) && booking.getCheckOutDate().isAfter(night);
        }

        public String guestName() {
            return booking.getGuestName() != null ? booking.getGuestName() : booking.getCustomer().getFullName();
        }
    }
}
