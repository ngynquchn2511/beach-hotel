package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Bang don phong cua le tan: Cho don -> Dang don (ai nhan, tu luc nao) -> Sach (AVAILABLE).
// Phong co khach den hom nay duoc dua len dau ("Uu tien"), vi chua don xong thi khong check-in duoc.
@Service
public class HousekeepingService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final RoomService roomService;

    public HousekeepingService(RoomRepository roomRepository, BookingRepository bookingRepository, RoomService roomService) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.roomService = roomService;
    }

    // 1 phong can don; arrival = don sap nhan phong nay hom nay (null = khong gap)
    public record Task(Room room, Booking arrival) {
        public boolean priority() {
            return arrival != null;
        }
    }

    public record Board(List<Task> waiting, List<Task> inProgress, List<Room> cleanedToday) {
        public int total() {
            return waiting.size() + inProgress.size();
        }
    }

    @Transactional(readOnly = true)
    public Board board(LocalDate today) {
        // Don da xac nhan, den ngay nhan (ke ca khach den tre tu hom truoc) -> phong do can xong truoc
        Map<Long, Booking> arrivals = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED).stream()
                .filter(b -> !b.getCheckInDate().isAfter(today))
                .collect(Collectors.toMap(b -> b.getRoom().getId(), Function.identity(),
                        (a, b) -> a.getCheckInDate().isBefore(b.getCheckInDate()) ? a : b));
        List<Room> rooms = roomRepository.findAll();
        Comparator<Task> order = Comparator.comparing((Task t) -> !t.priority())
                .thenComparing(t -> t.room().getDirtySince(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> t.room().getRoomNumber());
        List<Task> dirty = rooms.stream()
                .filter(r -> r.getStatus() == RoomStatus.NOT_READY)
                .map(r -> new Task(r, arrivals.get(r.getId())))
                .sorted(order)
                .toList();
        LocalDateTime startOfDay = today.atStartOfDay();
        List<Room> cleanedToday = rooms.stream()
                .filter(r -> r.getStatus() != RoomStatus.NOT_READY && r.getLastCleanedAt() != null
                        && !r.getLastCleanedAt().isBefore(startOfDay))
                .sorted(Comparator.comparing(Room::getLastCleanedAt).reversed())
                .toList();
        return new Board(dirty.stream().filter(t -> !t.room().isBeingCleaned()).toList(),
                dirty.stream().filter(t -> t.room().isBeingCleaned()).toList(),
                cleanedToday);
    }

    // So phong chua sach (cho don + dang don) - hien so tren sidebar
    @Transactional(readOnly = true)
    public long countDirty() {
        return roomRepository.findByStatus(RoomStatus.NOT_READY).size();
    }

    // Nhan vien bam "Nhan don" - phong chuyen sang "Dang don" ghi ten nguoi nhan
    @Transactional
    public void start(Long roomId, String staffName) {
        Room room = requireDirty(roomId);
        if (room.isBeingCleaned()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đang được " + room.getCleaningBy() + " dọn");
        }
        room.setCleaningBy(staffName);
        room.setCleaningStartedAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    // Tra lai phong ve "Cho don" (nhan nham / doi nguoi don)
    @Transactional
    public void release(Long roomId) {
        Room room = requireDirty(roomId);
        room.setCleaningBy(null);
        room.setCleaningStartedAt(null);
        roomRepository.save(room);
    }

    // Don xong -> phong sach, dat / nhan phong duoc
    @Transactional
    public void finish(Long roomId, String staffName) {
        requireDirty(roomId);
        roomService.markReady(roomId, staffName);
    }

    private Room requireDirty(Long roomId) {
        Room room = roomService.findById(roomId);
        if (room.getStatus() != RoomStatus.NOT_READY) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " không ở trạng thái chờ dọn");
        }
        return room;
    }
}
