package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

// Man hinh "Hom nay" cua le tan: khach den / khach di / dang o / can xu ly / phong cho don trong 1 ngay
@Service
public class FrontDeskService {

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository guestRepository;
    private final RoomRepository roomRepository;

    public FrontDeskService(BookingRepository bookingRepository, BookingGuestRepository guestRepository,
                            RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
    }

    // 1 dong khach den / di: kem so khach da khai bao luu tru (can >= 1 moi check-in duoc)
    public record Entry(Booking booking, long declaredGuests) {
        public boolean isDone() {
            BookingStatus s = booking.getStatus();
            return s == BookingStatus.CHECKED_IN || s == BookingStatus.CHECKED_OUT;
        }

        // Phong chua san sang don khach den: chua don / khach truoc chua tra / bao tri
        public boolean isRoomNotReady() {
            RoomStatus rs = booking.getRoom().getStatus();
            return !isDone() && (rs == RoomStatus.NOT_READY || rs == RoomStatus.OCCUPIED || rs == RoomStatus.MAINTENANCE);
        }
    }

    public record Day(LocalDate date, boolean today, List<Entry> arrivals, List<Entry> departures,
                      List<Booking> inHouse, List<Booking> overdueCheckOuts, List<Booking> noShows,
                      List<Room> roomsToClean) {
        public long arrivalsLeft() { return arrivals.stream().filter(e -> !e.isDone()).count(); }
        public long departuresLeft() {
            return departures.stream().filter(e -> e.booking().getStatus() == BookingStatus.CHECKED_IN).count();
        }
        public int attentionCount() { return overdueCheckOuts.size() + noShows.size(); }
    }

    @Transactional(readOnly = true)
    public Day day(LocalDate date, LocalDate today) {
        List<Booking> active = bookingRepository.findForCalendar(date.minusDays(1), date.plusDays(1));

        // Khach den: nhan phong hom nay (cho xac nhan / da xac nhan / da nhan phong)
        List<Entry> arrivals = active.stream()
                .filter(b -> b.getCheckInDate().equals(date))
                .filter(b -> b.getStatus() != BookingStatus.CHECKED_OUT)
                .sorted(Comparator.comparing(Booking::getCheckInTime).thenComparing(b -> b.getRoom().getRoomNumber()))
                .map(this::entry)
                .toList();

        // Khach di: tra phong hom nay (dang o -> can check-out, da tra -> xong)
        List<Entry> departures = active.stream()
                .filter(b -> b.getCheckOutDate().equals(date))
                .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN || b.getStatus() == BookingStatus.CHECKED_OUT)
                .sorted(Comparator.comparing(Booking::getCheckOutTime).thenComparing(b -> b.getRoom().getRoomNumber()))
                .map(this::entry)
                .toList();

        List<Booking> inHouse = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CHECKED_IN).stream()
                .sorted(Comparator.comparing(b -> b.getRoom().getRoomNumber()))
                .toList();

        // Can xu ly (chi tinh tu hom nay tro ve truoc): qua ngay tra ma chua check-out, qua ngay nhan ma chua den
        List<Booking> overdue = inHouse.stream().filter(b -> b.getCheckOutDate().isBefore(date)).toList();
        List<Booking> noShows = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED).stream()
                .filter(b -> b.getCheckInDate().isBefore(date))
                .sorted(Comparator.comparing(Booking::getCheckInDate))
                .toList();

        List<Room> roomsToClean = roomRepository.findAll().stream()
                .filter(r -> r.getStatus() == RoomStatus.NOT_READY)
                .sorted(Comparator.comparing(Room::getRoomNumber))
                .toList();

        return new Day(date, date.equals(today), arrivals, departures, inHouse, overdue, noShows, roomsToClean);
    }

    // So khach con phai don hom nay - hien so o muc "Hom nay" tren sidebar
    @Transactional(readOnly = true)
    public long arrivalsLeftToday(LocalDate today) {
        return bookingRepository.findForCalendar(today, today.plusDays(1)).stream()
                .filter(b -> b.getCheckInDate().equals(today))
                .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
                .count();
    }

    private Entry entry(Booking b) {
        return new Entry(b, guestRepository.countByBookingId(b.getId()));
    }
}
