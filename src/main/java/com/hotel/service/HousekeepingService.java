package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.StaffNotificationService.Audience;
import com.hotel.service.StaffNotificationService.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Bang don phong: Cho don -> Dang don (ai nhan, tu luc nao) -> [Cho kiem, neu bat kiem phong] -> Sach (AVAILABLE).
// Le tan / quan ly giao phong cho tung nhan vien buong. Phong co khach den hom nay duoc dua len dau ("Uu tien").
@Service
public class HousekeepingService {

    public static final int NOTE_MAX = 300;

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final RoomService roomService;

    private UserRepository userRepository;
    private PricingService pricingService;
    private PricingSettingsRepository settingsRepository;
    private StaffNotificationService notifier;
    private com.hotel.repository.HousekeepingLogRepository logRepository;

    @Autowired
    void setLogRepository(com.hotel.repository.HousekeepingLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // Ghi 1 luot don (thong ke nang suat / chat luong)
    private void log(Room room, User cleaner, String cleanerName, LocalDateTime startedAt, LocalDateTime finishedAt,
                     com.hotel.entity.HousekeepingLog.Result result, String inspectedBy, String note) {
        if (logRepository == null) {
            return;
        }
        logRepository.save(com.hotel.entity.HousekeepingLog.builder().room(room)
                .cleaner(cleaner != null && cleaner.getId() != null ? cleaner : null).cleanerName(cleanerName)
                .startedAt(startedAt).finishedAt(finishedAt).result(result).inspectedBy(inspectedBy).note(note).build());
    }

    public HousekeepingService(RoomRepository roomRepository, BookingRepository bookingRepository, RoomService roomService) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.roomService = roomService;
    }

    @Autowired
    void setDependencies(UserRepository userRepository, PricingService pricingService, PricingSettingsRepository settingsRepository,
                         StaffNotificationService notifier) {
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.settingsRepository = settingsRepository;
        this.notifier = notifier;
    }

    // 1 phong can don; arrival = don sap nhan phong nay hom nay (null = khong gap)
    public record Task(Room room, Booking arrival) {
        public boolean priority() {
            return arrival != null;
        }

        public User assignee() {
            return room.getCleaningAssignee();
        }
    }

    public record Board(List<Task> waiting, List<Task> inProgress, List<Task> awaitingInspection, List<Room> cleanedToday) {
        public int total() {
            return waiting.size() + inProgress.size() + awaitingInspection.size();
        }
    }

    @Transactional(readOnly = true)
    public Board board(LocalDate today) {
        return board(today, null);
    }

    // onlyAssigneeId != null: chi phong giao cho nhan vien do + phong chua giao ai (loc "Phong cua toi")
    @Transactional(readOnly = true)
    public Board board(LocalDate today, Long onlyAssigneeId) {
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
                .filter(r -> onlyAssigneeId == null || r.getCleaningAssignee() == null
                        || r.getCleaningAssignee().getId().equals(onlyAssigneeId))
                .map(r -> new Task(r, arrivals.get(r.getId())))
                .sorted(order)
                .toList();
        LocalDateTime startOfDay = today.atStartOfDay();
        List<Room> cleanedToday = rooms.stream()
                .filter(r -> r.getStatus() != RoomStatus.NOT_READY && r.getLastCleanedAt() != null
                        && !r.getLastCleanedAt().isBefore(startOfDay))
                .sorted(Comparator.comparing(Room::getLastCleanedAt).reversed())
                .toList();
        return new Board(dirty.stream().filter(t -> !t.room().isBeingCleaned() && !t.room().isAwaitingInspection()).toList(),
                dirty.stream().filter(t -> t.room().isBeingCleaned()).toList(),
                dirty.stream().filter(t -> t.room().isAwaitingInspection()).toList(),
                cleanedToday);
    }

    // So phong chua sach (cho don + dang don + cho kiem) - hien so tren sidebar
    @Transactional(readOnly = true)
    public long countDirty() {
        return roomRepository.findByStatus(RoomStatus.NOT_READY).size();
    }

    public List<User> housekeepers() {
        return userRepository == null ? List.of() : userRepository.findByRoleOrderByCreatedAtDesc(UserRole.HOUSEKEEPER).stream()
                .filter(u -> !Boolean.TRUE.equals(u.getLocked())).sorted(Comparator.comparing(User::getFullName)).toList();
    }

    public boolean inspectionRequired() {
        return pricingService != null && pricingService.getSettings().isInspectionRequired();
    }

    @Transactional
    public void setInspectionRequired(boolean required) {
        PricingSettings s = pricingService.getSettings();
        s.setHousekeepingInspection(required ? true : null);
        settingsRepository.save(s);
    }

    // Le tan / quan ly giao phong cho 1 nhan vien buong (assigneeId null = bo giao)
    @Transactional
    public void assign(Long roomId, Long assigneeId, String byName) {
        Room room = requireDirty(roomId);
        User assignee = null;
        if (assigneeId != null) {
            assignee = userRepository.findById(assigneeId)
                    .filter(u -> u.getRole() == UserRole.HOUSEKEEPER)
                    .orElseThrow(() -> new BusinessException("Chỉ giao phòng cho tài khoản Buồng phòng"));
        }
        room.setCleaningAssignee(assignee);
        roomRepository.save(room);
        if (assignee != null) {
            notify(EnumSet.of(Audience.HOUSEKEEPING), assignee.getId(), Level.INFO, "Bạn được giao dọn phòng " + room.getRoomNumber(),
                    (byName != null ? "Giao bởi " + byName : "") + (room.getInspectionNote() != null ? " · " + room.getInspectionNote() : ""));
        }
    }

    // Nhan vien bam "Nhan don" - phong chuyen sang "Dang don" ghi ten nguoi nhan.
    // Phong da giao cho nguoi khac thi nhan vien buong khac khong nhan duoc (le tan / quan ly van nhan duoc)
    @Transactional
    public void start(Long roomId, User staff) {
        Room room = requireDirty(roomId);
        if (room.isAwaitingInspection()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đang chờ kiểm");
        }
        if (room.isBeingCleaned()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đang được " + room.getCleaningBy() + " dọn");
        }
        boolean housekeeper = staff != null && staff.getRole() == UserRole.HOUSEKEEPER;
        if (housekeeper && room.getCleaningAssignee() != null && !room.getCleaningAssignee().getId().equals(staff.getId())) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đã giao cho " + room.getCleaningAssignee().getFullName());
        }
        if (housekeeper && room.getCleaningAssignee() == null) {
            room.setCleaningAssignee(staff);
        }
        room.setCleaningBy(staff != null ? staff.getFullName() : null);
        room.setCleaningStartedAt(LocalDateTime.now());
        roomRepository.save(room);
    }

    // Cho cac cho goi cu (ten nhan vien, khong ro vai tro)
    @Transactional
    public void start(Long roomId, String staffName) {
        start(roomId, staffName == null ? null : User.builder().fullName(staffName).role(UserRole.STAFF).build());
    }

    // Tra lai phong ve "Cho don" (nhan nham / doi nguoi don)
    @Transactional
    public void release(Long roomId) {
        Room room = requireDirty(roomId);
        room.setCleaningBy(null);
        room.setCleaningStartedAt(null);
        room.setInspectionRequestedAt(null);
        roomRepository.save(room);
    }

    // Don xong: bat kiem phong + nguoi bam la nhan vien buong -> "Cho kiem"; con lai -> sach ngay
    @Transactional
    public boolean finish(Long roomId, User staff) {
        Room room = requireDirty(roomId);
        if (room.isAwaitingInspection()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đã báo dọn xong, đang chờ kiểm");
        }
        String name = staff != null ? staff.getFullName() : null;
        if (inspectionRequired() && staff != null && staff.getRole() == UserRole.HOUSEKEEPER) {
            if (room.getCleaningStartedAt() == null) {
                room.setCleaningStartedAt(LocalDateTime.now());
            }
            if (room.getCleaningBy() == null) {
                room.setCleaningBy(name);
            }
            room.setInspectionRequestedAt(LocalDateTime.now());
            roomRepository.save(room);
            notify(EnumSet.of(Audience.FRONT_DESK), null, Level.INFO, "Phòng " + room.getRoomNumber() + " chờ kiểm",
                    name + " vừa dọn xong");
            return false;
        }
        User cleaner = room.getCleaningAssignee() != null ? room.getCleaningAssignee()
                : staff != null && staff.getRole() == UserRole.HOUSEKEEPER ? staff : null;
        log(room, cleaner, room.getCleaningBy() != null ? room.getCleaningBy() : name, room.getCleaningStartedAt(), LocalDateTime.now(),
                com.hotel.entity.HousekeepingLog.Result.DIRECT, null, null);
        roomService.markReady(roomId, name);
        notifyReady(room, name);
        return true;
    }

    @Transactional
    public void finish(Long roomId, String staffName) {
        finish(roomId, staffName == null ? null : User.builder().fullName(staffName).role(UserRole.STAFF).build());
    }

    // Kiem dat -> phong sach (ghi nguoi don, khong phai nguoi kiem)
    @Transactional
    public void pass(Long roomId, String inspectorName) {
        Room room = requireAwaiting(roomId);
        String cleaner = room.getCleaningBy();
        Long cleanerId = room.getCleaningAssignee() != null ? room.getCleaningAssignee().getId() : null;
        log(room, room.getCleaningAssignee(), cleaner, room.getCleaningStartedAt(), room.getInspectionRequestedAt(),
                com.hotel.entity.HousekeepingLog.Result.PASSED, inspectorName, null);
        roomService.markReady(roomId, cleaner);
        notifyReady(room, cleaner);
        if (cleanerId != null) {
            notify(EnumSet.of(Audience.HOUSEKEEPING), cleanerId, Level.SUCCESS, "Phòng " + room.getRoomNumber() + " đã kiểm đạt",
                    inspectorName != null ? "Kiểm bởi " + inspectorName : "");
        }
    }

    // Kiem chua dat -> tra lai "Dang don" cho nguoi don kem ly do
    @Transactional
    public void fail(Long roomId, String note, String inspectorName) {
        Room room = requireAwaiting(roomId);
        String text = note == null ? "" : note.trim();
        if (text.isEmpty() || text.length() > NOTE_MAX) {
            throw new BusinessException("Vui lòng ghi chỗ chưa đạt (tối đa " + NOTE_MAX + " ký tự)");
        }
        log(room, room.getCleaningAssignee(), room.getCleaningBy(), room.getCleaningStartedAt(), room.getInspectionRequestedAt(),
                com.hotel.entity.HousekeepingLog.Result.FAILED, inspectorName, text);
        room.setInspectionRequestedAt(null);
        room.setInspectionNote(text);
        room.setCleaningStartedAt(LocalDateTime.now());
        roomRepository.save(room);
        notify(EnumSet.of(Audience.HOUSEKEEPING), room.getCleaningAssignee() != null ? room.getCleaningAssignee().getId() : null,
                Level.WARNING, "Phòng " + room.getRoomNumber() + " kiểm chưa đạt", text + (inspectorName != null ? " · " + inspectorName : ""));
    }

    // ===== Thong ke buong phong =====

    public record CleanerStats(String name, long cleaned, Double avgMinutes, long inspected, long failed) {
        public double failRate() {
            return inspected == 0 ? 0 : Math.round(1000.0 * failed / inspected) / 10.0;
        }
    }

    public record Stats(List<CleanerStats> cleaners, List<com.hotel.entity.HousekeepingLog> recentFailures, long totalCleaned) {
    }

    // Moi nguoi: so phong don xong (DIRECT + PASSED), thoi gian TB, so lan kiem / chua dat
    @Transactional(readOnly = true)
    public Stats stats(LocalDate from, LocalDate to) {
        List<com.hotel.entity.HousekeepingLog> logs = logRepository == null ? List.of()
                : logRepository.findBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<String, List<com.hotel.entity.HousekeepingLog>> byCleaner = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getCleaner() != null ? l.getCleaner().getFullName()
                        : l.getCleanerName() != null ? l.getCleanerName() : "Không rõ"));
        List<CleanerStats> cleaners = byCleaner.entrySet().stream().map(e -> {
            List<com.hotel.entity.HousekeepingLog> list = e.getValue();
            List<com.hotel.entity.HousekeepingLog> done = list.stream().filter(l -> l.getResult() != com.hotel.entity.HousekeepingLog.Result.FAILED).toList();
            Double avg = list.stream().map(com.hotel.entity.HousekeepingLog::getMinutes).filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue).average().stream().boxed().findFirst().map(v -> Math.round(v * 10) / 10.0).orElse(null);
            long inspected = list.stream().filter(l -> l.getResult() != com.hotel.entity.HousekeepingLog.Result.DIRECT).count();
            long failed = list.stream().filter(l -> l.getResult() == com.hotel.entity.HousekeepingLog.Result.FAILED).count();
            return new CleanerStats(e.getKey(), done.size(), avg, inspected, failed);
        }).sorted(Comparator.comparing(CleanerStats::cleaned).reversed().thenComparing(CleanerStats::name)).toList();
        List<com.hotel.entity.HousekeepingLog> failures = logs.stream()
                .filter(l -> l.getResult() == com.hotel.entity.HousekeepingLog.Result.FAILED).limit(20).toList();
        return new Stats(cleaners, failures, cleaners.stream().mapToLong(CleanerStats::cleaned).sum());
    }

    private void notifyReady(Room room, String cleaner) {
        boolean arrivalToday = bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CONFIRMED).stream()
                .anyMatch(b -> b.getRoom().getId().equals(room.getId()) && !b.getCheckInDate().isAfter(LocalDate.now()));
        notify(EnumSet.of(Audience.FRONT_DESK), null, arrivalToday ? Level.SUCCESS : Level.INFO, "Phòng " + room.getRoomNumber() + " đã sạch",
                (arrivalToday ? "Khách đến hôm nay - nhận phòng được" : "Sẵn sàng đón khách") + (cleaner != null ? " · " + cleaner : ""));
    }

    private void notify(java.util.Set<Audience> to, Long userId, Level level, String title, String message) {
        if (notifier != null) {
            notifier.notify(to, userId, "housekeeping", level, title, message, "/admin/housekeeping");
        }
    }

    private Room requireDirty(Long roomId) {
        Room room = roomService.findById(roomId);
        if (room.getStatus() != RoomStatus.NOT_READY) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " không ở trạng thái chờ dọn");
        }
        return room;
    }

    private Room requireAwaiting(Long roomId) {
        Room room = requireDirty(roomId);
        if (!room.isAwaitingInspection()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " không ở trạng thái chờ kiểm");
        }
        return room;
    }
}
