package com.hotel.service;

import com.hotel.entity.Attendance;
import com.hotel.entity.DutyShift;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.AttendanceRepository;
import com.hotel.repository.DutyShiftRepository;
import com.hotel.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

// Phan ca (lich truc theo tuan), ai dang truc, cham cong tu dong theo lan thao tac tren cong quan tri
@Service
public class StaffShiftService {

    public static final List<UserRole> STAFF_ROLES = List.of(UserRole.ADMIN, UserRole.STAFF, UserRole.HOUSEKEEPER, UserRole.ACCOUNTANT);
    // Thao tac trong vong 30 phut gan nhat = dang co mat
    public static final long PRESENT_MINUTES = 30;
    // Vao tre qua 5 phut moi tinh di muon
    public static final long LATE_GRACE_MINUTES = 5;
    // Xep ca toi da 62 ngay 1 lan
    public static final int MAX_ASSIGN_DAYS = 62;

    private final DutyShiftRepository dutyRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public StaffShiftService(DutyShiftRepository dutyRepository, AttendanceRepository attendanceRepository, UserRepository userRepository) {
        this.dutyRepository = dutyRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    public List<User> staffList() {
        return userRepository.findByRoleInOrderByFullNameAsc(STAFF_ROLES);
    }

    // ===== Lich truc =====

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // Lich 1 tuan: nhan vien -> ngay -> cac ca (nhan vien khong co ca nao van hien dong trong de xep)
    @Transactional(readOnly = true)
    public Week week(LocalDate anyDay) {
        LocalDate from = weekStart(anyDay);
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(from.plusDays(i));
        }
        Map<Long, Map<LocalDate, List<DutyShift>>> cells = new HashMap<>();
        Map<LocalDate, Map<DutyShift.Shift, Integer>> counts = new HashMap<>();
        for (DutyShift d : dutyRepository.findBetween(from, from.plusDays(6))) {
            cells.computeIfAbsent(d.getStaff().getId(), k -> new HashMap<>())
                    .computeIfAbsent(d.getWorkDate(), k -> new ArrayList<>()).add(d);
            counts.computeIfAbsent(d.getWorkDate(), k -> new EnumMap<>(DutyShift.Shift.class)).merge(d.getShift(), 1, Integer::sum);
        }
        List<Row> rows = staffList().stream().filter(u -> !Boolean.TRUE.equals(u.getLocked()) || cells.containsKey(u.getId()))
                .map(u -> new Row(u, days.stream().map(day -> cells.getOrDefault(u.getId(), Map.of()).getOrDefault(day, List.of())).toList()))
                .toList();
        // Ca chua co ai truc trong tuan (de quan tri thay cho trong)
        List<String> gaps = new ArrayList<>();
        for (LocalDate day : days) {
            for (DutyShift.Shift s : DutyShift.Shift.values()) {
                if (counts.getOrDefault(day, Map.of()).getOrDefault(s, 0) == 0) {
                    gaps.add(String.format("%s %02d/%02d", s.getLabel(), day.getDayOfMonth(), day.getMonthValue()));
                }
            }
        }
        return new Week(from, days, rows, gaps);
    }

    // Xep 1 nhan vien vao ca `shift` cac ngay tu..den (chi cac thu duoc chon); bo qua ngay da co ca do. Tra ve so ca da them
    @Transactional
    public int assign(Long staffId, LocalDate from, LocalDate to, DutyShift.Shift shift, Collection<DayOfWeek> weekdays,
                      String note, String by) {
        User staff = userRepository.findById(staffId).filter(u -> STAFF_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên"));
        if (Boolean.TRUE.equals(staff.getLocked())) {
            throw new BusinessException("Tài khoản " + staff.getFullName() + " đang bị khóa");
        }
        if (shift == null || from == null) {
            throw new BusinessException("Vui lòng chọn ca và ngày");
        }
        LocalDate end = to == null ? from : to;
        if (end.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        if (ChronoUnit.DAYS.between(from, end) >= MAX_ASSIGN_DAYS) {
            throw new BusinessException("Mỗi lần xếp tối đa " + MAX_ASSIGN_DAYS + " ngày");
        }
        if (from.isBefore(LocalDate.now().minusDays(31))) {
            throw new BusinessException("Không xếp ca cho ngày đã qua quá 31 ngày");
        }
        Set<DayOfWeek> allowed = weekdays == null || weekdays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : EnumSet.copyOf(weekdays);
        String text = note == null || note.isBlank() ? null : note.trim();
        if (text != null && text.length() > 200) {
            throw new BusinessException("Ghi chú tối đa 200 ký tự");
        }
        int added = 0;
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            if (allowed.contains(d.getDayOfWeek()) && !dutyRepository.existsByStaffIdAndWorkDateAndShift(staffId, d, shift)) {
                dutyRepository.save(DutyShift.builder().staff(staff).workDate(d).shift(shift).note(text).createdBy(by).build());
                added++;
            }
        }
        return added;
    }

    @Transactional
    public void remove(Long dutyId) {
        dutyRepository.delete(dutyRepository.findById(dutyId).orElseThrow(() -> new BusinessException("Không tìm thấy ca trực")));
    }

    // Chep lich tuan co ngay `sourceDay` sang tuan co ngay `targetDay` (bo qua ca da co)
    @Transactional
    public int copyWeek(LocalDate sourceDay, LocalDate targetDay, String by) {
        LocalDate src = weekStart(sourceDay);
        LocalDate dst = weekStart(targetDay);
        if (src.equals(dst)) {
            throw new BusinessException("Tuần nguồn và tuần đích trùng nhau");
        }
        long shiftDays = ChronoUnit.DAYS.between(src, dst);
        int added = 0;
        for (DutyShift d : dutyRepository.findBetween(src, src.plusDays(6))) {
            LocalDate day = d.getWorkDate().plusDays(shiftDays);
            if (!Boolean.TRUE.equals(d.getStaff().getLocked())
                    && !dutyRepository.existsByStaffIdAndWorkDateAndShift(d.getStaff().getId(), day, d.getShift())) {
                dutyRepository.save(DutyShift.builder().staff(d.getStaff()).workDate(day).shift(d.getShift())
                        .note(d.getNote()).createdBy(by).build());
                added++;
            }
        }
        return added;
    }

    // ===== Ai dang truc =====

    // Ca dang dien ra: ai duoc xep, ai da vao (gio vao, di muon), ai chua vao; nguoi dang lam ngoai lich; cac ca con lai trong ngay
    @Transactional(readOnly = true)
    public OnDuty onDuty(LocalDateTime now) {
        DutyShift.Shift current = DutyShift.Shift.at(now.toLocalTime());
        LocalDate workDate = DutyShift.Shift.workDateAt(now);
        Map<Long, Attendance> att = new HashMap<>();
        // Truoc 6h: nguoi ca dem tinh cong hom qua, nguoi vao som ngoai lich tinh hom nay -> lay ca 2 ngay, uu tien ban moi nhat
        for (Attendance a : attendanceRepository.findBetween(workDate, now.toLocalDate())) {
            att.merge(a.getStaff().getId(), a, (x, y) -> x.getCheckOutAt().isAfter(y.getCheckOutAt()) ? x : y);
        }
        List<DutyShift> dayRoster = dutyRepository.findBetween(workDate, workDate);
        List<Duty> currentDuty = new ArrayList<>();
        Set<Long> scheduledNow = new HashSet<>();
        Map<DutyShift.Shift, List<String>> byShift = new EnumMap<>(DutyShift.Shift.class);
        for (DutyShift d : dayRoster) {
            byShift.computeIfAbsent(d.getShift(), k -> new ArrayList<>()).add(d.getStaff().getFullName());
            if (d.getShift() == current) {
                Attendance a = att.get(d.getStaff().getId());
                currentDuty.add(new Duty(d.getStaff(), d, a, a != null && isPresent(a, now)));
                scheduledNow.add(d.getStaff().getId());
            }
        }
        List<Duty> extra = att.values().stream()
                .filter(a -> !scheduledNow.contains(a.getStaff().getId()) && isPresent(a, now))
                .sorted(Comparator.comparing(Attendance::getCheckInAt))
                .map(a -> new Duty(a.getStaff(), null, a, true)).toList();
        return new OnDuty(now, workDate, current, currentDuty, extra, byShift);
    }

    static boolean isPresent(Attendance a, LocalDateTime now) {
        return !a.isEnded() && Duration.between(a.getCheckOutAt(), now).toMinutes() <= PRESENT_MINUTES;
    }

    // ===== Cham cong tu dong =====

    // Goi moi khi nhan vien thao tac tren cong quan tri (da gioi han 5 phut / lan o interceptor)
    @Transactional
    public Attendance touch(Long staffId, LocalDateTime now) {
        User staff = userRepository.findById(staffId).orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên"));
        LocalDate workDate = workDateFor(staffId, now);
        Optional<Attendance> existing = attendanceRepository.findByStaffIdAndWorkDate(staffId, workDate);
        if (existing.isPresent()) {
            Attendance a = existing.get();
            if (now.isAfter(a.getCheckOutAt())) {
                a.setCheckOutAt(now);
            }
            a.setEnded(false);
            return a;
        }
        Attendance.AttendanceBuilder b = Attendance.builder().staff(staff).workDate(workDate).checkInAt(now).checkOutAt(now).ended(false);
        matchingShift(staffId, workDate, now).ifPresent(d -> {
            b.shift(d.getShift());
            long late = Duration.between(d.getStartAt(), now).toMinutes();
            b.lateMinutes(late > LATE_GRACE_MINUTES ? (int) late : 0);
        });
        return attendanceRepository.save(b.build());
    }

    // Truoc 6h sang ma hom qua co ca dem (hoac dang lam do tu hom qua) -> van tinh cong ngay hom qua
    LocalDate workDateFor(Long staffId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (now.getHour() >= 6) {
            return today;
        }
        LocalDate yesterday = today.minusDays(1);
        boolean nightYesterday = dutyRepository.existsByStaffIdAndWorkDateAndShift(staffId, yesterday, DutyShift.Shift.NIGHT);
        boolean stillWorking = attendanceRepository.findByStaffIdAndWorkDate(staffId, yesterday)
                .filter(a -> !a.isEnded() && Duration.between(a.getCheckOutAt(), now).toHours() < 2).isPresent();
        return nightYesterday || stillWorking ? yesterday : today;
    }

    // Ca trong lich gan nhat voi gio vao (vao som toi 3 tieng van tinh cho ca do)
    private Optional<DutyShift> matchingShift(Long staffId, LocalDate workDate, LocalDateTime now) {
        return dutyRepository.findForStaff(staffId, workDate).stream()
                .filter(d -> !now.isBefore(d.getStartAt().minusHours(3)) && now.isBefore(d.getEndAt()))
                .min(Comparator.comparing(d -> Math.abs(Duration.between(d.getStartAt(), now).toMinutes())));
    }

    @Transactional(readOnly = true)
    public Optional<Attendance> current(Long staffId, LocalDateTime now) {
        return attendanceRepository.findByStaffIdAndWorkDate(staffId, workDateFor(staffId, now));
    }

    // Nhan vien bam "Ket thuc ca"
    @Transactional
    public Attendance endShift(Long staffId, LocalDateTime now) {
        Attendance a = attendanceRepository.findByStaffIdAndWorkDate(staffId, workDateFor(staffId, now))
                .orElseThrow(() -> new BusinessException("Chưa có chấm công hôm nay"));
        a.setCheckOutAt(now.isAfter(a.getCheckOutAt()) ? now : a.getCheckOutAt());
        a.setEnded(true);
        return a;
    }

    // Quan tri sua tay so cong (0 / 0.5 / 1 / 1.5 / 2; de trong = tu dong) va ghi chu
    @Transactional
    public Attendance adjust(Long attendanceId, BigDecimal units, String note) {
        Attendance a = attendanceRepository.findById(attendanceId).orElseThrow(() -> new BusinessException("Không tìm thấy bản chấm công"));
        if (units != null && (units.signum() < 0 || units.compareTo(BigDecimal.valueOf(2)) > 0
                || units.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0)) {
            throw new BusinessException("Số công từ 0 đến 2, bước 0,5");
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (units != null && text == null) {
            throw new BusinessException("Sửa số công cần ghi lý do");
        }
        if (text != null && text.length() > 300) {
            throw new BusinessException("Ghi chú tối đa 300 ký tự");
        }
        a.setManualUnits(units);
        a.setNote(text);
        return a;
    }

    // Quan tri them cong cho nhan vien quen dang nhap (VD buong phong lam ca ma khong mo may)
    @Transactional
    public Attendance addManual(Long staffId, LocalDate day, DutyShift.Shift shift, BigDecimal units, String note) {
        User staff = userRepository.findById(staffId).filter(u -> STAFF_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên"));
        if (day == null || shift == null || day.isAfter(LocalDate.now())) {
            throw new BusinessException("Chọn ngày (không sau hôm nay) và ca");
        }
        if (attendanceRepository.findByStaffIdAndWorkDate(staffId, day).isPresent()) {
            throw new BusinessException("Ngày này đã có chấm công - sửa số công ở dòng đó");
        }
        Attendance a = attendanceRepository.save(Attendance.builder().staff(staff).workDate(day).shift(shift)
                .checkInAt(shift.startOn(day)).checkOutAt(shift.endOn(day)).ended(true).lateMinutes(0).build());
        return adjust(a.getId(), units == null ? BigDecimal.ONE : units, note == null || note.isBlank() ? "Thêm tay" : note);
    }

    @Transactional(readOnly = true)
    public List<Attendance> attendances(LocalDate from, LocalDate to, Long staffId) {
        return staffId == null ? attendanceRepository.findBetween(from, to)
                : attendanceRepository.findForStaff(staffId, from, to);
    }

    // Tong hop cham cong theo nhan vien trong khoang ngay
    public static Map<Long, Summary> summarize(List<Attendance> list) {
        Map<Long, Summary> out = new LinkedHashMap<>();
        for (Attendance a : list) {
            Summary s = out.computeIfAbsent(a.getStaff().getId(), k -> new Summary(a.getStaff()));
            s.units = s.units.add(a.getUnits());
            s.days++;
            s.minutes += a.getWorkedMinutes();
            if (a.isLate()) {
                s.lateCount++;
                s.lateMinutes += a.getLateMinutes();
            }
        }
        return out;
    }

    // ===== Kieu tra ve =====

    public record Row(User staff, List<List<DutyShift>> days) {
    }

    public record Week(LocalDate start, List<LocalDate> days, List<Row> rows, List<String> gaps) {
        public LocalDate getPrev() {
            return start.minusWeeks(1);
        }

        public LocalDate getNext() {
            return start.plusWeeks(1);
        }
    }

    public record Duty(User staff, DutyShift duty, Attendance attendance, boolean present) {
    }

    public record OnDuty(LocalDateTime now, LocalDate workDate, DutyShift.Shift shift, List<Duty> scheduled, List<Duty> extra,
                         Map<DutyShift.Shift, List<String>> dayRoster) {
        public long getPresentCount() {
            return scheduled.stream().filter(Duty::present).count() + extra.size();
        }
    }

    public static class Summary {
        private final User staff;
        private BigDecimal units = BigDecimal.ZERO;
        private int days;
        private long minutes;
        private int lateCount;
        private long lateMinutes;

        Summary(User staff) {
            this.staff = staff;
        }

        public User getStaff() { return staff; }
        public BigDecimal getUnits() { return units; }
        public int getDays() { return days; }
        public long getHours() { return minutes / 60; }
        public int getLateCount() { return lateCount; }
        public long getLateMinutes() { return lateMinutes; }
    }
}
