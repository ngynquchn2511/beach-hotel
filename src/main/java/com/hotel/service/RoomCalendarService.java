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
        return build(from, days, today, null);
    }

    // lastBookable: ngay nhan phong muon nhat con nhan dat (6 thang) - o sau ngay nay khong cho bam dat
    @Transactional(readOnly = true)
    public Calendar build(LocalDate from, int days, LocalDate today, LocalDate lastBookable) {
        LocalDate to = from.plusDays(days);

        List<Day> dayList = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            dayList.add(new Day(d, i, weekend, d.equals(today),
                    !d.isBefore(today) && (lastBookable == null || !d.isAfter(lastBookable))));
        }

        List<Room> rooms = roomRepository.findAll().stream()
                .sorted(Comparator.comparing((Room r) -> r.getRoomType().getName())
                        .thenComparing(Room::getRoomNumber))
                .toList();

        Map<Long, List<Booking>> bookingsByRoom = bookingRepository.findForCalendar(from, to).stream()
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()));

        // Luot dat tu Booking.com / Airbnb chua ghi nhan thanh don: thanh rieng, khong bam vao don
        Map<Long, List<com.hotel.entity.OtaBlock>> otaByRoom = otaBlockRepository == null ? Map.of()
                : otaBlockRepository.findBlockingBetween(from, to).stream().collect(Collectors.groupingBy(o -> o.getRoom().getId()));
        List<RoomRow> rows = rooms.stream()
                .map(room -> new RoomRow(room, bookingsByRoom.getOrDefault(room.getId(), List.of()).stream()
                        .map(b -> toBar(b, from, to, days))
                        .toList(),
                        otaByRoom.getOrDefault(room.getId(), List.of()).stream().map(o -> toOtaBar(o, from, to, days)).toList()))
                .toList();

        // So phong con trong tung dem: phong khong bao tri va khong co don nao o qua dem do
        long sellable = rooms.stream().filter(r -> r.getStatus() != RoomStatus.MAINTENANCE).count();
        List<Long> freeRooms = dayList.stream()
                .map(day -> sellable - rows.stream()
                        .filter(row -> row.room().getStatus() != RoomStatus.MAINTENANCE)
                        .filter(row -> !row.isFree(day.date()))
                        .count())
                .toList();

        return new Calendar(from, to, days, dayList, rows, freeRooms);
    }

    // ===== Tong quan 6 thang: moi dem 1 o, so phong da co khach dat / tong so phong ban duoc =====

    public record DayLoad(LocalDate date, long booked, long arrivals, long sellable, boolean past, boolean bookable) {
        public long free() {
            return Math.max(0, sellable - booked);
        }

        public int percent() {
            return sellable == 0 ? 0 : (int) Math.round(100.0 * booked / sellable);
        }

        // Muc mau: 0 trong het, 1 < 30%, 2 < 60%, 3 < 90%, 4 gan / het phong
        public int level() {
            if (booked == 0) {
                return 0;
            }
            int p = percent();
            return p < 30 ? 1 : (p < 60 ? 2 : (p < 90 ? 3 : 4));
        }
    }

    public record MonthLoad(java.time.YearMonth month, List<DayLoad> days, int leadingBlanks) {
        public long bookedNights() {
            return days.stream().filter(d -> !d.past()).mapToLong(DayLoad::booked).sum();
        }

        public long sellableNights() {
            return days.stream().filter(d -> !d.past()).mapToLong(DayLoad::sellable).sum();
        }

        public int percent() {
            return sellableNights() == 0 ? 0 : (int) Math.round(100.0 * bookedNights() / sellableNights());
        }

        public long arrivals() {
            return days.stream().filter(d -> !d.past()).mapToLong(DayLoad::arrivals).sum();
        }

        public long fullDays() {
            return days.stream().filter(d -> !d.past() && d.sellable() > 0 && d.free() == 0).count();
        }
    }

    // Tu thang hien tai den thang cua ngay "until" (ngay da qua dau thang van ve cho tron thang, to mo)
    @Transactional(readOnly = true)
    public List<MonthLoad> overview(LocalDate today, LocalDate until, LocalDate lastBookable) {
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = until.withDayOfMonth(until.lengthOfMonth()).plusDays(1);
        List<Room> rooms = roomRepository.findAll();
        java.util.Set<Long> sellableIds = rooms.stream().filter(r -> r.getStatus() != RoomStatus.MAINTENANCE)
                .map(Room::getId).collect(Collectors.toSet());
        long sellable = sellableIds.size();
        Map<LocalDate, java.util.Set<Long>> occupied = new java.util.HashMap<>();
        Map<LocalDate, Long> arrivals = new java.util.HashMap<>();
        for (Booking b : bookingRepository.findForCalendar(start, end)) {
            if (!sellableIds.contains(b.getRoom().getId())) {
                continue;
            }
            arrivals.merge(b.getCheckInDate(), 1L, Long::sum);
            for (LocalDate n = b.getCheckInDate().isBefore(start) ? start : b.getCheckInDate();
                 n.isBefore(b.getCheckOutDate()) && n.isBefore(end); n = n.plusDays(1)) {
                occupied.computeIfAbsent(n, k -> new java.util.HashSet<>()).add(b.getRoom().getId());
            }
        }
        if (otaBlockRepository != null) {
            for (com.hotel.entity.OtaBlock o : otaBlockRepository.findBlockingBetween(start, end)) {
                if (!sellableIds.contains(o.getRoom().getId())) {
                    continue;
                }
                for (LocalDate n = o.getStartDate().isBefore(start) ? start : o.getStartDate();
                     n.isBefore(o.getEndDate()) && n.isBefore(end); n = n.plusDays(1)) {
                    occupied.computeIfAbsent(n, k -> new java.util.HashSet<>()).add(o.getRoom().getId());
                }
            }
        }
        List<MonthLoad> months = new ArrayList<>();
        for (java.time.YearMonth m = java.time.YearMonth.from(start); !m.isAfter(java.time.YearMonth.from(until)); m = m.plusMonths(1)) {
            List<DayLoad> days = new ArrayList<>();
            for (int d = 1; d <= m.lengthOfMonth(); d++) {
                LocalDate date = m.atDay(d);
                days.add(new DayLoad(date, occupied.getOrDefault(date, java.util.Set.of()).size(), arrivals.getOrDefault(date, 0L),
                        sellable, date.isBefore(today), !date.isBefore(today) && !date.isAfter(lastBookable)));
            }
            months.add(new MonthLoad(m, days, m.atDay(1).getDayOfWeek().getValue() - 1));
        }
        return months;
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

    public record RoomRow(Room room, List<Bar> bars, List<OtaBar> otaBars) {
        public RoomRow(Room room, List<Bar> bars) {
            this(room, bars, List.of());
        }

        // Dem nay phong chua co don nao o qua -> o lich moi cho bam de dat tai quay
        // (nua dau o ngay khach khac nhan phong van lo ra, khong duoc tro toi dem da co khach)
        public boolean isFree(LocalDate night) {
            return bars.stream().noneMatch(bar -> bar.coversNight(night))
                    && otaBars.stream().noneMatch(o -> !o.block().getStartDate().isAfter(night) && o.block().getEndDate().isAfter(night));
        }
    }

    public record OtaBar(com.hotel.entity.OtaBlock block, int gridStart, int gridEnd, boolean clippedLeft, boolean clippedRight) {
    }

    static OtaBar toOtaBar(com.hotel.entity.OtaBlock o, LocalDate from, LocalDate to, int days) {
        boolean clippedLeft = o.getStartDate().isBefore(from);
        boolean clippedRight = !o.getEndDate().isBefore(to);
        int startHalf = clippedLeft ? 0 : 2 * (int) ChronoUnit.DAYS.between(from, o.getStartDate()) + 1;
        int endHalf = clippedRight ? 2 * days : 2 * (int) ChronoUnit.DAYS.between(from, o.getEndDate()) + 1;
        return new OtaBar(o, startHalf + 1, endHalf + 1, clippedLeft, clippedRight);
    }

    private com.hotel.repository.OtaBlockRepository otaBlockRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setOtaBlockRepository(com.hotel.repository.OtaBlockRepository otaBlockRepository) {
        this.otaBlockRepository = otaBlockRepository;
    }

    public record Bar(Booking booking, int gridStart, int gridEnd, boolean clippedLeft, boolean clippedRight) {
        boolean coversNight(LocalDate night) {
            return !booking.getCheckInDate().isAfter(night) && booking.getCheckOutDate().isAfter(night);
        }

        public String guestName() {
            return booking.getGuestName() != null ? booking.getGuestName() : booking.getCustomer().getFullName();
        }

        // Thanh ngan (1 dem): chi hien ten goi (chu cuoi, VD "Nguyen Van An" -> "An") de con doc duoc
        public boolean isShort() {
            return gridEnd - gridStart <= 2;
        }

        public String displayName() {
            String name = guestName() == null ? "" : guestName().trim();
            if (!isShort() || name.isEmpty()) {
                return name;
            }
            return name.substring(name.lastIndexOf(' ') + 1);
        }
    }
}
