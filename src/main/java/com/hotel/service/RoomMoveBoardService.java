package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.OtaBlock;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.OtaBlockRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// Trang doi phong: bang lich trong / ban cua tung phong theo tung dem quanh cac dem cua don,
// de le tan thay phong nao trong tu ngay nao toi ngay nao truoc khi chon phong chuyen sang.
// Moi cot = 1 dem (ngay d = dem d -> d+1). Phong doi duoc (trong het cac dem con lai) len dau, co nut chon.
@Service
public class RoomMoveBoardService {

    public static final int MIN_DAYS = 14;
    public static final int MAX_DAYS = 31;
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final EnumSet<BookingStatus> HOLDING =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    private final BookingService bookingService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final OtaBlockRepository otaBlockRepository;

    public RoomMoveBoardService(BookingService bookingService, RoomRepository roomRepository,
                                BookingRepository bookingRepository, OtaBlockRepository otaBlockRepository) {
        this.bookingService = bookingService;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.otaBlockRepository = otaBlockRepository;
    }

    public enum State { FREE, THIS, BOOKED, PENDING, OTA, MAINTENANCE }

    public record Day(LocalDate date, boolean weekend, boolean today, boolean inStay, boolean monthStart) {
        public String weekdayShort() {
            return switch (date.getDayOfWeek()) {
                case MONDAY -> "T2";
                case TUESDAY -> "T3";
                case WEDNESDAY -> "T4";
                case THURSDAY -> "T5";
                case FRIDAY -> "T6";
                case SATURDAY -> "T7";
                case SUNDAY -> "CN";
            };
        }
    }

    // Doan lien tiep cung trang thai tren 1 hang (gop o de ve thanh ngang)
    public record Segment(State state, int span, boolean inStay, String label, String title, Long bookingId) {
    }

    // option != null: doi duoc (co nut chon); reason: vi sao khong doi duoc
    public record Row(Room room, boolean current, BookingService.RoomMoveOption option, String reason,
                      long freeStayNights, long stayNights, List<String> freeRanges, boolean freeWholeWindow,
                      List<Segment> segments) {
        public boolean selectable() {
            return option != null;
        }

        public boolean partlyFree() {
            return option == null && !current && freeStayNights > 0;
        }
    }

    public record Board(LocalDate from, int days, LocalDate stayFrom, LocalDate stayTo, List<Day> dayList,
                        List<Row> rows, int guests, boolean canGoBack) {
        public LocalDate prevFrom() {
            return from.minusDays(7);
        }

        public LocalDate nextFrom() {
            return from.plusDays(7);
        }

        public long selectableCount() {
            return rows.stream().filter(Row::selectable).count();
        }

        public long partlyFreeCount() {
            return rows.stream().filter(Row::partlyFree).count();
        }
    }

    // Dem bat dau tinh doi phong: khach dang o -> tu hom nay, chua den -> tu ngay nhan phong
    static LocalDate stayFrom(Booking booking, LocalDate today) {
        return booking.getStatus() == BookingStatus.CHECKED_IN && booking.getCheckInDate().isBefore(today)
                ? today : booking.getCheckInDate();
    }

    @Transactional(readOnly = true)
    public Board build(Long bookingId, LocalDate requestedFrom) {
        Booking booking = bookingService.findById(bookingId);
        LocalDate today = LocalDate.now();
        LocalDate stayFrom = stayFrom(booking, today);
        LocalDate stayTo = booking.getCheckOutDate();
        long stayNights = Math.max(0, ChronoUnit.DAYS.between(stayFrom, stayTo));

        // Mac dinh: 3 ngay truoc dem dau tien cua don, du rong de thay ca ky o + vai ngay sau
        int days = (int) Math.min(MAX_DAYS, Math.max(MIN_DAYS, stayNights + 7));
        LocalDate defaultFrom = stayFrom.minusDays(3);
        LocalDate from = requestedFrom != null ? requestedFrom : defaultFrom;
        if (from.isBefore(today)) {
            from = today;
        }
        LocalDate to = from.plusDays(days);

        List<Day> dayList = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            dayList.add(new Day(d, d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY,
                    d.equals(today), !d.isBefore(stayFrom) && d.isBefore(stayTo), i == 0 || d.getDayOfMonth() == 1));
        }

        int guests = booking.getNumberOfGuests() != null ? booking.getNumberOfGuests() : 1;
        List<BookingService.RoomMoveOption> optionList = bookingService.roomMoveOptions(bookingId);
        Map<Long, BookingService.RoomMoveOption> options = optionList.stream()
                .collect(Collectors.toMap(o -> o.room().getId(), Function.identity()));
        List<Long> optionOrder = optionList.stream().map(o -> o.room().getId()).toList();

        // Don giu phong / dot OTA trong cua so + trong cac dem cua don (de tinh ly do)
        LocalDate scanFrom = from.isBefore(stayFrom) ? from : stayFrom;
        LocalDate scanTo = to.isAfter(stayTo) ? to : stayTo;
        Map<Long, List<Booking>> bookingsByRoom = bookingRepository.findForCalendar(scanFrom, scanTo).stream()
                .filter(b -> HOLDING.contains(b.getStatus()))
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()));
        Map<Long, List<OtaBlock>> otaByRoom = otaBlockRepository.findBlockingBetween(scanFrom, scanTo).stream()
                .collect(Collectors.groupingBy(o -> o.getRoom().getId()));

        Long currentRoomId = booking.getRoom().getId();
        Long typeId = booking.getRoom().getRoomType().getId();
        List<Row> rows = new ArrayList<>();
        for (Room room : roomRepository.findAll()) {
            boolean current = room.getId().equals(currentRoomId);
            Integer max = room.getRoomType().getMaxGuests();
            if (!current && max != null && max < guests) {
                continue;
            }
            List<Booking> rb = bookingsByRoom.getOrDefault(room.getId(), List.of());
            List<OtaBlock> ro = otaByRoom.getOrDefault(room.getId(), List.of());
            Function<LocalDate, Cell> cellAt = night -> cell(booking, room, rb, ro, night);

            long freeStay = 0;
            for (LocalDate n = stayFrom; n.isBefore(stayTo); n = n.plusDays(1)) {
                if (cellAt.apply(n).state() == State.FREE) {
                    freeStay++;
                }
            }
            List<Segment> segments = segments(dayList, cellAt);
            List<String> freeRanges = freeRanges(dayList, cellAt);
            boolean freeWhole = segments.stream().allMatch(s -> s.state() == State.FREE);
            BookingService.RoomMoveOption option = current ? null : options.get(room.getId());
            String reason = current ? "Phòng khách đang giữ" : option != null ? null
                    : reason(booking, room, rb, ro, stayFrom, stayTo, freeStay, stayNights);
            rows.add(new Row(room, current, option, reason, freeStay, stayNights, freeRanges, freeWhole, segments));
        }

        rows.sort(Comparator.comparing((Row r) -> !r.current())
                .thenComparing(r -> !r.selectable())
                .thenComparing(r -> r.selectable() ? optionOrder.indexOf(r.room().getId()) : 0)
                .thenComparing(r -> !r.room().getRoomType().getId().equals(typeId))
                .thenComparing(r -> -r.freeStayNights())
                .thenComparing(r -> r.room().getRoomNumber()));
        return new Board(from, days, stayFrom, stayTo, dayList, rows, guests, from.isAfter(today));
    }

    private record Cell(State state, Long key, String label, String title, Long bookingId) {
    }

    private static Cell cell(Booking self, Room room, List<Booking> bookings, List<OtaBlock> ota, LocalDate night) {
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            return new Cell(State.MAINTENANCE, null, "Bảo trì", "Phòng đang bảo trì", null);
        }
        for (Booking b : bookings) {
            if (!b.getCheckInDate().isAfter(night) && b.getCheckOutDate().isAfter(night)) {
                String range = b.getCheckInDate().format(DM) + " – " + b.getCheckOutDate().format(DM);
                String name = b.getGuestName() != null ? b.getGuestName() : b.getCustomer().getFullName();
                if (b.getId().equals(self.getId())) {
                    return new Cell(State.THIS, b.getId(), "Đơn này", "Đơn này · " + range, b.getId());
                }
                State s = b.getStatus() == BookingStatus.PENDING ? State.PENDING : State.BOOKED;
                return new Cell(s, b.getId(), "#" + b.getCode(),
                        "Đơn #" + b.getCode() + " · " + name + " · " + range + " · " + b.getStatus().getVietnameseLabel(), b.getId());
            }
        }
        for (OtaBlock o : ota) {
            if (!o.getStartDate().isAfter(night) && o.getEndDate().isAfter(night)) {
                String ch = o.getFeed().getChannel().getLabel();
                return new Cell(State.OTA, -o.getId(), ch, "Đã bán trên " + ch + " · "
                        + o.getStartDate().format(DM) + " – " + o.getEndDate().format(DM), null);
            }
        }
        return new Cell(State.FREE, null, "", "Trống đêm " + night.format(DM), null);
    }

    private static List<Segment> segments(List<Day> days, Function<LocalDate, Cell> cellAt) {
        List<Segment> out = new ArrayList<>();
        int i = 0;
        while (i < days.size()) {
            Day d = days.get(i);
            Cell c = cellAt.apply(d.date());
            int j = i + 1;
            while (j < days.size()) {
                Day e = days.get(j);
                Cell n = cellAt.apply(e.date());
                if (n.state() != c.state() || !Objects.equals(n.key(), c.key()) || e.inStay() != d.inStay()) {
                    break;
                }
                j++;
            }
            String title = c.state() == State.FREE
                    ? "Trống " + d.date().format(DM) + " → " + days.get(j - 1).date().plusDays(1).format(DM)
                    : c.title();
            out.add(new Segment(c.state(), j - i, d.inStay(), c.label(), title, c.bookingId()));
            i = j;
        }
        return out;
    }

    // Cac khoang trong trong cua so, ghi theo ngay nhan -> ngay tra (VD dem 25, 26 trong -> "25/11 → 27/11")
    private static List<String> freeRanges(List<Day> days, Function<LocalDate, Cell> cellAt) {
        List<String> out = new ArrayList<>();
        LocalDate start = null;
        for (Day d : days) {
            boolean free = cellAt.apply(d.date()).state() == State.FREE;
            if (free && start == null) {
                start = d.date();
            } else if (!free && start != null) {
                out.add(start.format(DM) + " → " + d.date().format(DM));
                start = null;
            }
        }
        if (start != null) {
            out.add(start.format(DM) + " → " + days.get(days.size() - 1).date().plusDays(1).format(DM) + "+");
        }
        return out;
    }

    private static String reason(Booking self, Room room, List<Booking> bookings, List<OtaBlock> ota,
                                 LocalDate stayFrom, LocalDate stayTo, long freeStay, long stayNights) {
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            return "Đang bảo trì";
        }
        List<String> busy = new ArrayList<>();
        for (Booking b : bookings) {
            if (!b.getId().equals(self.getId()) && b.getCheckInDate().isBefore(stayTo) && b.getCheckOutDate().isAfter(stayFrom)) {
                busy.add("#" + b.getCode() + " " + b.getCheckInDate().format(DM) + "–" + b.getCheckOutDate().format(DM));
            }
        }
        for (OtaBlock o : ota) {
            if (o.getStartDate().isBefore(stayTo) && o.getEndDate().isAfter(stayFrom)) {
                busy.add(o.getFeed().getChannel().getLabel() + " " + o.getStartDate().format(DM) + "–" + o.getEndDate().format(DM));
            }
        }
        if (!busy.isEmpty()) {
            return "Vướng " + String.join(", ", busy) + " · trống " + freeStay + "/" + stayNights + " đêm";
        }
        if (self.getStatus() == BookingStatus.CHECKED_IN && room.getStatus() != RoomStatus.AVAILABLE) {
            return room.getStatus() == RoomStatus.NOT_READY ? "Chưa dọn xong - báo buồng phòng dọn trước"
                    : "Phòng đang " + room.getStatus().getVietnameseLabel().toLowerCase();
        }
        return "Vướng giờ đổi khách cùng ngày (khách trước trả muộn / khách sau nhận sớm)";
    }
}
