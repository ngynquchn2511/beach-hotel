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

        // Da dang don bao lau (phut) - qua 45 phut to do de quan ly de y
        public long cleaningMinutes() {
            return room.getCleaningStartedAt() == null ? 0
                    : Math.max(0, java.time.Duration.between(room.getCleaningStartedAt(), LocalDateTime.now()).toMinutes());
        }

        public boolean slow() {
            return room.isBeingCleaned() && cleaningMinutes() > 45;
        }

        // Phong ban bao lau roi, VD "35 phút", "3 giờ", "1 ngày"
        public String dirtyFor() {
            if (room.getDirtySince() == null) {
                return null;
            }
            long m = Math.max(0, java.time.Duration.between(room.getDirtySince(), LocalDateTime.now()).toMinutes());
            return m < 60 ? m + " phút" : m < 24 * 60 ? (m / 60) + " giờ" : (m / (24 * 60)) + " ngày";
        }

        public String state() {
            return room.isAwaitingInspection() ? "inspect" : room.isBeingCleaned() ? "cleaning" : "waiting";
        }
    }

    public record Board(List<Task> waiting, List<Task> inProgress, List<Task> awaitingInspection, List<Room> cleanedToday) {
        public int total() {
            return waiting.size() + inProgress.size() + awaitingInspection.size();
        }

        // Danh sach 1 cot: dang don, cho kiem, roi cho don (moi nhom da xep uu tien truoc)
        public List<Task> all() {
            List<Task> out = new java.util.ArrayList<>(inProgress);
            out.addAll(awaitingInspection);
            out.addAll(waiting);
            return out;
        }

        public long priorityCount() {
            return all().stream().filter(Task::priority).count();
        }

        public long unassignedWaiting() {
            return waiting.stream().filter(t -> t.assignee() == null).count();
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

    // ===== Doi buong phong: ai dang trong ca, dang giu bao nhieu viec -> goi y nguoi giao =====

    private StaffShiftService shiftService;

    @Autowired
    void setShiftService(StaffShiftService shiftService) {
        this.shiftService = shiftService;
    }

    // onShift: co ten trong lich truc ca hien tai; present: dang thao tac tren he thong (da cham cong vao, chua het ca)
    // assigned: phong chua sach dang giao cho nguoi nay; cleaning: trong do dang don; checks: phong khach sap tra cho kiem
    public record Member(User user, boolean onShift, boolean present, String shiftLabel,
                         long assigned, long cleaning, long checks, long doneToday) {
        public long load() {
            return assigned + checks;
        }

        public boolean available() {
            return onShift || present;
        }

        public String status() {
            if (present) {
                return "Đang làm" + (shiftLabel != null ? " · " + shiftLabel.toLowerCase() : "");
            }
            if (onShift) {
                return "Trong ca, chưa vào";
            }
            return shiftLabel != null ? "Hôm nay làm " + shiftLabel.toLowerCase() : "Không trong ca";
        }

        public String workload() {
            if (load() == 0) {
                return "Đang rảnh";
            }
            StringBuilder sb = new StringBuilder();
            if (cleaning > 0) {
                sb.append("đang dọn ").append(cleaning);
            }
            long queued = assigned - cleaning;
            if (queued > 0) {
                sb.append(sb.isEmpty() ? "" : " · ").append("chờ ").append(queued).append(" phòng");
            }
            if (checks > 0) {
                sb.append(sb.isEmpty() ? "" : " · ").append("kiểm ").append(checks);
            }
            String s = sb.toString();
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    @Transactional(readOnly = true)
    public List<Member> team(LocalDateTime now) {
        java.util.Set<Long> onShift = new java.util.HashSet<>();
        java.util.Set<Long> present = new java.util.HashSet<>();
        Map<String, String> shiftByName = new java.util.HashMap<>();
        String currentShift = null;
        if (shiftService != null) {
            StaffShiftService.OnDuty duty = shiftService.onDuty(now);
            currentShift = duty.shift() != null ? duty.shift().getLabel() : null;
            for (StaffShiftService.Duty d : duty.scheduled()) {
                onShift.add(d.staff().getId());
                if (d.present()) {
                    present.add(d.staff().getId());
                }
            }
            duty.extra().forEach(d -> present.add(d.staff().getId()));
            duty.dayRoster().forEach((shift, names) -> names.forEach(n -> shiftByName.putIfAbsent(n, shift.getLabel())));
        }
        List<Room> dirty = roomRepository.findByStatus(RoomStatus.NOT_READY);
        List<Booking> checks = pendingRoomChecks();
        Map<Long, Long> done = logRepository == null ? Map.of()
                : logRepository.findBetween(now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay()).stream()
                .filter(l -> l.getCleaner() != null && l.getResult() != com.hotel.entity.HousekeepingLog.Result.FAILED)
                .collect(Collectors.groupingBy(l -> l.getCleaner().getId(), Collectors.counting()));
        String current = currentShift;
        return housekeepers().stream().map(u -> {
            Long id = u.getId();
            long assigned = dirty.stream().filter(r -> r.getCleaningAssignee() != null && r.getCleaningAssignee().getId().equals(id)).count();
            long cleaning = dirty.stream().filter(r -> r.isBeingCleaned() && r.getCleaningAssignee() != null
                    && r.getCleaningAssignee().getId().equals(id)).count();
            long check = checks.stream().filter(b -> b.getRoomCheckAssignee() != null && b.getRoomCheckAssignee().getId().equals(id)).count();
            String label = onShift.contains(id) ? current : shiftByName.get(u.getFullName());
            return new Member(u, onShift.contains(id), present.contains(id), label, assigned, cleaning, check, done.getOrDefault(id, 0L));
        }).sorted(Comparator.comparing((Member m) -> !m.present())
                .thenComparing(m -> !m.onShift())
                .thenComparing(Member::load)
                .thenComparing(m -> m.user().getFullName())).toList();
    }

    // Nguoi nen giao: dang trong ca / dang lam va it viec nhat (team da sap xep san)
    public static Member recommended(List<Member> team) {
        return team.stream().filter(Member::available).findFirst().orElse(null);
    }

    // Chia cac phong cho don chua giao ai cho nhung nguoi dang trong ca, phong uu tien truoc, nguoi it viec nhat nhan truoc
    @Transactional
    public int autoAssign(String byName, LocalDateTime now) {
        List<Member> available = team(now).stream().filter(Member::available).toList();
        if (available.isEmpty()) {
            throw new BusinessException("Chưa có nhân viên buồng phòng nào trong ca - giao tay từng phòng");
        }
        List<Task> open = board(now.toLocalDate()).waiting().stream().filter(t -> t.assignee() == null).toList();
        if (open.isEmpty()) {
            throw new BusinessException("Không còn phòng chờ dọn nào chưa giao");
        }
        Map<Long, Long> load = new java.util.HashMap<>();
        available.forEach(m -> load.put(m.user().getId(), m.load()));
        Map<Long, List<String>> given = new java.util.LinkedHashMap<>();
        for (Task t : open) {
            Member pick = available.stream()
                    .min(Comparator.comparing((Member m) -> load.get(m.user().getId())).thenComparing(m -> !m.present()))
                    .orElseThrow();
            Room room = t.room();
            room.setCleaningAssignee(pick.user());
            roomRepository.save(room);
            load.merge(pick.user().getId(), 1L, Long::sum);
            given.computeIfAbsent(pick.user().getId(), k -> new java.util.ArrayList<>())
                    .add(room.getRoomNumber() + (t.priority() ? " (ưu tiên)" : ""));
        }
        given.forEach((userId, rooms) -> notify(EnumSet.of(Audience.HOUSEKEEPING), userId, Level.INFO,
                "Bạn được giao dọn " + rooms.size() + " phòng", "Phòng " + String.join(", ", rooms)
                        + (byName != null ? " · giao bởi " + byName : "")));
        return open.size();
    }

    private User requireHousekeeper(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getRole() == UserRole.HOUSEKEEPER && !Boolean.TRUE.equals(u.getLocked()))
                .orElseThrow(() -> new BusinessException("Chỉ giao cho tài khoản Buồng phòng đang hoạt động"));
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

    // ===== Kiem phong truoc khi tra phong (do mat / hu hong) =====

    // Le tan mo man hinh tra phong -> yeu cau buong phong kiem phong cua don dang o (yeu cau lai thi xoa ket qua cu)
    @Transactional
    public Booking requestRoomCheck(Long bookingId, String byName) {
        return requestRoomCheck(bookingId, byName, null);
    }

    // assigneeId: nhan vien buong phong duoc giao kiem (null = bao chung cho ca buong phong)
    @Transactional
    public Booking requestRoomCheck(Long bookingId, String byName, Long assigneeId) {
        Booking booking = requireStaying(bookingId);
        User assignee = assigneeId != null ? requireHousekeeper(assigneeId) : null;
        booking.setRoomCheckRequestedAt(LocalDateTime.now());
        booking.setRoomCheckRequestedBy(byName);
        booking.setRoomCheckAssignee(assignee);
        booking.setRoomCheckedAt(null);
        booking.setRoomCheckedBy(null);
        booking.setRoomCheckIssue(null);
        booking.setRoomCheckNote(null);
        bookingRepository.save(booking);
        notifyRoomCheck(booking, byName);
        return booking;
    }

    // Doi nguoi kiem khi yeu cau dang cho (giu nguyen gio yeu cau)
    @Transactional
    public Booking assignRoomCheck(Long bookingId, Long assigneeId, String byName) {
        Booking booking = requireStaying(bookingId);
        if (!booking.isRoomCheckPending()) {
            throw new BusinessException("Phòng " + booking.getRoom().getRoomNumber() + " không có yêu cầu kiểm đang chờ");
        }
        booking.setRoomCheckAssignee(assigneeId != null ? requireHousekeeper(assigneeId) : null);
        bookingRepository.save(booking);
        notifyRoomCheck(booking, byName);
        return booking;
    }

    private void notifyRoomCheck(Booking booking, String byName) {
        User assignee = booking.getRoomCheckAssignee();
        String guest = booking.getGuestName() != null ? " · khách " + booking.getGuestName() : "";
        notify(EnumSet.of(Audience.HOUSEKEEPING), assignee != null ? assignee.getId() : null, Level.WARNING,
                (assignee != null ? "Bạn được giao kiểm phòng " : "Kiểm phòng ") + booking.getRoom().getRoomNumber() + " trước khi trả",
                "Kiểm minibar, đồ mất / hư hỏng rồi báo lễ tân" + guest + (byName != null ? " · " + byName : ""), "/admin/housekeeping#kiem-truoc-tra");
    }

    // Nhan vien buong phong bao ket qua: khong co gi / co do mat, hu hong (bat buoc ghi ro) -> bao le tan tinh phu phi
    @Transactional
    public Booking reportRoomCheck(Long bookingId, boolean issue, String note, String checkerName) {
        return reportRoomCheck(bookingId, issue, note, checkerName == null ? null
                : User.builder().fullName(checkerName).role(UserRole.STAFF).build());
    }

    // Phong da giao cho 1 nhan vien buong phong thi nhan vien buong khac khong bao thay (le tan / quan ly van bao duoc)
    @Transactional
    public Booking reportRoomCheck(Long bookingId, boolean issue, String note, User checker) {
        String checkerName = checker != null ? checker.getFullName() : null;
        Booking booking = requireStaying(bookingId);
        if (!booking.isRoomCheckPending()) {
            throw new BusinessException("Phòng " + booking.getRoom().getRoomNumber() + " không có yêu cầu kiểm trước khi trả");
        }
        User assignee = booking.getRoomCheckAssignee();
        if (checker != null && checker.getRole() == UserRole.HOUSEKEEPER && assignee != null
                && !assignee.getId().equals(checker.getId())) {
            throw new BusinessException("Phòng " + booking.getRoom().getRoomNumber() + " đã giao cho " + assignee.getFullName() + " kiểm");
        }
        String text = note == null ? "" : note.trim();
        if (issue && text.isEmpty()) {
            throw new BusinessException("Vui lòng ghi đồ bị mất / hư hỏng");
        }
        if (text.length() > NOTE_MAX) {
            throw new BusinessException("Ghi chú tối đa " + NOTE_MAX + " ký tự");
        }
        booking.setRoomCheckedAt(LocalDateTime.now());
        booking.setRoomCheckedBy(checkerName);
        booking.setRoomCheckIssue(issue);
        booking.setRoomCheckNote(text.isEmpty() ? null : text);
        bookingRepository.save(booking);
        String room = booking.getRoom().getRoomNumber();
        notify(EnumSet.of(Audience.FRONT_DESK), null, issue ? Level.WARNING : Level.SUCCESS,
                issue ? "Phòng " + room + " có đồ mất / hư hỏng" : "Phòng " + room + " kiểm xong, không mất / hỏng gì",
                (text.isEmpty() ? "" : text + " · ") + (checkerName != null ? checkerName : ""),
                "/admin/bookings/" + booking.getId() + "/check-out");
        return booking;
    }

    // Cac phong dang cho buong phong kiem truoc khi tra (yeu cau cu nhat len dau)
    @Transactional(readOnly = true)
    public List<Booking> pendingRoomChecks() {
        return pendingRoomChecks(null);
    }

    // onlyAssigneeId != null: chi yeu cau giao cho nguoi nay + yeu cau bao chung
    @Transactional(readOnly = true)
    public List<Booking> pendingRoomChecks(Long onlyAssigneeId) {
        return bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.CHECKED_IN).stream()
                .filter(Booking::isRoomCheckPending)
                .filter(b -> onlyAssigneeId == null || b.getRoomCheckAssignee() == null
                        || b.getRoomCheckAssignee().getId().equals(onlyAssigneeId))
                .sorted(Comparator.comparing(Booking::getRoomCheckRequestedAt))
                .toList();
    }

    private Booking requireStaying(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn #" + bookingId));
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ kiểm phòng trước khi trả cho đơn đang ở (CHECKED_IN)");
        }
        return booking;
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
        notify(to, userId, level, title, message, "/admin/housekeeping");
    }

    private void notify(java.util.Set<Audience> to, Long userId, Level level, String title, String message, String link) {
        if (notifier != null) {
            notifier.notify(to, userId, "housekeeping", level, title, message, link);
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
