package com.hotel.service;

import com.hotel.entity.Attendance;
import com.hotel.entity.DutyShift;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.AttendanceRepository;
import com.hotel.repository.DutyShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

// Chuyen can: doi chieu lich truc voi cham cong tu dong -> moi ca da xep la dung gio / di muon / ve som / vang / nghi co phep.
// Tong hop theo thang de xet luong: goi y khau tru (di muon, vang khong bao) va thuong chuyen can. Chi la goi y, nguoi tra luong sua duoc
@Service
public class PunctualityService {

    // 3 lan muon dau tien trong thang (moi lan <= 30 phut) chi nhac nho, khong tru tien
    public static final int FREE_LATES_PER_MONTH = 3;
    public static final int FREE_LATE_MAX_MINUTES = 30;
    // Tru tien muon = luong ngay / 480 phut x so phut muon, toi thieu 20.000 d, toi da nua ngay luong
    public static final BigDecimal MIN_LATE_DEDUCTION = new BigDecimal("20000");
    public static final int WORK_MINUTES_PER_DAY = 8 * 60;
    // Vang khong bao: ngay do khong co cong (khong tinh luong) + phat them 50% luong ngay
    public static final BigDecimal ABSENCE_PENALTY_RATE = new BigDecimal("0.5");
    // Bam "Ket thuc ca" som hon gio het ca qua 30 phut = ve som (gio ra tu dong chi la lan thao tac cuoi nen khong tinh)
    public static final long EARLY_LEAVE_MINUTES = 30;
    // Thuong chuyen can: tu 15 ca trong thang, khong muon, khong ve som, khong vang khong bao
    public static final int BONUS_MIN_SHIFTS = 15;
    public static final BigDecimal PERFECT_BONUS = new BigDecimal("300000");

    public enum Status {
        ON_TIME("Đúng giờ", "ok"),
        LATE("Đi muộn", "late"),
        EARLY_LEAVE("Về sớm", "early"),
        ABSENT("Vắng không báo", "absent"),
        EXCUSED("Nghỉ có phép", "excused"),
        NOT_YET_IN("Chưa vào ca", "missing"),
        UPCOMING("Chưa tới ca", "upcoming");

        private final String label;
        private final String css;

        Status(String label, String css) {
            this.label = label;
            this.css = css;
        }

        public String getLabel() {
            return label;
        }

        public String getCss() {
            return css;
        }

        public boolean isViolation() {
            return this == LATE || this == EARLY_LEAVE || this == ABSENT;
        }
    }

    private final DutyShiftRepository dutyRepository;
    private final AttendanceRepository attendanceRepository;
    private final StaffShiftService shiftService;

    public PunctualityService(DutyShiftRepository dutyRepository, AttendanceRepository attendanceRepository,
                              StaffShiftService shiftService) {
        this.dutyRepository = dutyRepository;
        this.attendanceRepository = attendanceRepository;
        this.shiftService = shiftService;
    }

    // ===== Tung ca =====

    @Transactional(readOnly = true)
    public List<Check> checks(LocalDate from, LocalDate to, Long staffId, LocalDateTime now) {
        Map<String, Attendance> att = new HashMap<>();
        List<Attendance> attendances = staffId == null ? attendanceRepository.findBetween(from, to)
                : attendanceRepository.findForStaff(staffId, from, to);
        attendances.forEach(a -> att.put(key(a.getStaff().getId(), a.getWorkDate()), a));
        List<Check> out = new ArrayList<>();
        for (DutyShift d : dutyRepository.findBetween(from, to)) {
            if (staffId == null || d.getStaff().getId().equals(staffId)) {
                out.add(check(d, att.get(key(d.getStaff().getId(), d.getWorkDate())), now));
            }
        }
        out.sort(Comparator.comparing((Check c) -> c.duty().getStartAt()).thenComparing(c -> c.duty().getStaff().getFullName()));
        return out;
    }

    // Ket qua theo id ca truc (cho bang lich truc tuan)
    @Transactional(readOnly = true)
    public Map<Long, Check> byDuty(LocalDate from, LocalDate to, LocalDateTime now) {
        Map<Long, Check> out = new HashMap<>();
        checks(from, to, null, now).forEach(c -> out.put(c.duty().getId(), c));
        return out;
    }

    static Check check(DutyShift d, Attendance a, LocalDateTime now) {
        LocalDateTime start = d.getStartAt();
        LocalDateTime end = d.getEndAt();
        if (a != null && a.getManualUnits() != null && a.getManualUnits().signum() == 0) {
            return new Check(d, a, Status.EXCUSED, 0, 0);
        }
        // Cham cong trong ngay co phu ca nay khong: ca da khop luc vao (vao som toi 3 tieng van tinh);
        // ca khac cung ngay (VD lam 2 ca lien) thi phai con lam qua 30 phut dau cua ca nay
        boolean covered = a != null && a.getCheckInAt().isBefore(end) && (a.getShift() == null || a.getShift() == d.getShift()
                ? !a.getCheckOutAt().isBefore(start.minusHours(3))
                : a.getCheckOutAt().isAfter(start.plusMinutes(30)));
        if (!covered) {
            if (now.isBefore(start.plusMinutes(StaffShiftService.LATE_GRACE_MINUTES + 1))) {
                return new Check(d, a, Status.UPCOMING, 0, 0);
            }
            if (now.isBefore(end)) {
                return new Check(d, a, Status.NOT_YET_IN, (int) Duration.between(start, now).toMinutes(), 0);
            }
            return new Check(d, a, Status.ABSENT, 0, 0);
        }
        long lateMin = Duration.between(start, a.getCheckInAt()).toMinutes();
        int late = lateMin > StaffShiftService.LATE_GRACE_MINUTES ? (int) lateMin : 0;
        long earlyMin = a.isEnded() ? Duration.between(a.getCheckOutAt(), end).toMinutes() : 0;
        int early = earlyMin > EARLY_LEAVE_MINUTES ? (int) earlyMin : 0;
        Status s = late > 0 ? Status.LATE : early > 0 ? Status.EARLY_LEAVE : Status.ON_TIME;
        return new Check(d, a, s, late, early);
    }

    private static String key(Long staffId, LocalDate day) {
        return staffId + "|" + day;
    }

    // ===== Tong hop thang de xet luong =====

    @Transactional(readOnly = true)
    public Map<Long, Summary> month(YearMonth month, Long staffId, LocalDateTime now) {
        Map<Long, Summary> out = new LinkedHashMap<>();
        for (Check c : checks(month.atDay(1), month.atEndOfMonth(), staffId, now)) {
            out.computeIfAbsent(c.duty().getStaff().getId(), k -> new Summary(c.duty().getStaff())).add(c);
        }
        out.values().forEach(Summary::finish);
        return out;
    }

    // Danh sach nhan vien co lich trong thang, it chuyen can nhat len dau
    @Transactional(readOnly = true)
    public List<Summary> ranking(YearMonth month, LocalDateTime now) {
        List<Summary> list = new ArrayList<>(month(month, null, now).values());
        list.sort(Comparator.comparing(Summary::getDeduction).reversed()
                .thenComparing(Comparator.comparingInt(Summary::getViolationCount).reversed())
                .thenComparing(s -> s.getStaff().getFullName()));
        return list;
    }

    // Quan tri ghi nhan 1 ca vang la nghi co phep (tao cham cong 0 cong kem ly do) -> khong bi tru tien
    @Transactional
    public void excuse(Long staffId, LocalDate day, DutyShift.Shift shift, String reason, String by) {
        String text = reason == null ? "" : reason.trim();
        if (text.length() < 3) {
            throw new BusinessException("Ghi lý do nghỉ (ít nhất 3 ký tự)");
        }
        if (!dutyRepository.existsByStaffIdAndWorkDateAndShift(staffId, day, shift)) {
            throw new BusinessException("Nhân viên không có ca này trong lịch trực");
        }
        if (attendanceRepository.findByStaffIdAndWorkDate(staffId, day).isPresent()) {
            throw new BusinessException("Ngày này nhân viên đã có chấm công - sửa ở trang Chấm công nhân viên");
        }
        String note = "Nghỉ có phép: " + text + (by == null ? "" : " (" + by + ")");
        shiftService.addManual(staffId, day, shift, BigDecimal.ZERO, note.length() > 300 ? note.substring(0, 300) : note);
    }

    // ===== Kieu tra ve =====

    public record Check(DutyShift duty, Attendance attendance, Status status, int lateMinutes, int earlyMinutes) {

        // Chu ngan hien trong o lich truc
        public String getShort() {
            return switch (status) {
                case ON_TIME -> "✓ " + attendance.getCheckInAt().toLocalTime().withSecond(0).withNano(0);
                case LATE -> "Muộn " + lateMinutes + "'";
                case EARLY_LEAVE -> "Về sớm " + earlyMinutes + "'";
                case ABSENT -> "Vắng";
                case EXCUSED -> "Phép";
                case NOT_YET_IN -> "Chưa vào";
                case UPCOMING -> "";
            };
        }

        public String getDetail() {
            StringBuilder b = new StringBuilder(duty.getShift().getLabel()).append(" ").append(duty.getShift().getTimeRange());
            if (attendance != null && status != Status.EXCUSED) {
                b.append(" · vào ").append(attendance.getCheckInAt().toLocalTime().withSecond(0).withNano(0))
                        .append(", ra ").append(attendance.getCheckOutAt().toLocalTime().withSecond(0).withNano(0));
            }
            b.append(" · ").append(status.getLabel());
            if (lateMinutes > 0 && status != Status.NOT_YET_IN) {
                b.append(" ").append(lateMinutes).append(" phút");
            }
            if (earlyMinutes > 0) {
                b.append(status == Status.EARLY_LEAVE ? " " : ", về sớm ").append(earlyMinutes).append(" phút");
            }
            if (attendance != null && attendance.getNote() != null) {
                b.append(" · ").append(attendance.getNote());
            }
            return b.toString();
        }
    }

    // 1 dong tru / thuong goi y (kem ly do)
    public record Item(LocalDate day, String reason, BigDecimal amount) {
    }

    public static class Summary {
        private final User staff;
        private final List<Check> checks = new ArrayList<>();
        private int scheduled, done, attended, onTime, late, early, absent, excused, upcoming;
        private long lateMinutes;
        private final List<Item> deductions = new ArrayList<>();
        private BigDecimal deduction = BigDecimal.ZERO;
        private BigDecimal bonus = BigDecimal.ZERO;
        private String bonusReason;

        Summary(User staff) {
            this.staff = staff;
        }

        void add(Check c) {
            checks.add(c);
            scheduled++;
            if (c.status() == Status.ON_TIME || c.status() == Status.LATE || c.status() == Status.EARLY_LEAVE) {
                attended++;
            }
            switch (c.status()) {
                case ON_TIME -> onTime++;
                case LATE -> {
                    late++;
                    lateMinutes += c.lateMinutes();
                    if (c.earlyMinutes() > 0) {
                        early++;
                    }
                }
                case EARLY_LEAVE -> early++;
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
                default -> upcoming++;
            }
        }

        void finish() {
            done = attended + absent;
            BigDecimal wage = staff.getDailyWage() == null ? BigDecimal.ZERO : staff.getDailyWage();
            int lateIndex = 0;
            for (Check c : checks) {
                LocalDate day = c.duty().getWorkDate();
                if (c.status() == Status.LATE) {
                    lateIndex++;
                    boolean free = lateIndex <= FREE_LATES_PER_MONTH && c.lateMinutes() <= FREE_LATE_MAX_MINUTES;
                    if (!free && wage.signum() > 0) {
                        BigDecimal amount = wage.multiply(BigDecimal.valueOf(c.lateMinutes()))
                                .divide(BigDecimal.valueOf(WORK_MINUTES_PER_DAY), 0, RoundingMode.HALF_UP);
                        amount = roundThousand(amount.max(MIN_LATE_DEDUCTION).min(wage.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)));
                        deductions.add(new Item(day, "Muộn " + c.lateMinutes() + " phút (" + c.duty().getShift().getLabel().toLowerCase()
                                + ", lần " + lateIndex + ")", amount));
                    }
                } else if (c.status() == Status.ABSENT && wage.signum() > 0) {
                    deductions.add(new Item(day, "Vắng không báo (" + c.duty().getShift().getLabel().toLowerCase() + ")",
                            roundThousand(wage.multiply(ABSENCE_PENALTY_RATE))));
                }
            }
            deduction = deductions.stream().map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (done >= BONUS_MIN_SHIFTS && late == 0 && early == 0 && absent == 0) {
                bonus = PERFECT_BONUS;
                bonusReason = "Thưởng chuyên cần: " + done + " ca đúng giờ, không vắng";
            }
        }

        private static BigDecimal roundThousand(BigDecimal v) {
            return v.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000));
        }

        public User getStaff() { return staff; }
        public List<Check> getChecks() { return checks; }
        public List<Check> getViolations() { return checks.stream().filter(c -> c.status().isViolation()).toList(); }
        public int getScheduled() { return scheduled; }
        public int getDone() { return done; }
        public int getOnTime() { return onTime; }
        public int getLate() { return late; }
        public long getLateMinutes() { return lateMinutes; }
        public int getEarly() { return early; }
        public int getAbsent() { return absent; }
        public int getExcused() { return excused; }
        public int getUpcoming() { return upcoming; }
        public int getViolationCount() { return late + early + absent; }
        public List<Item> getDeductions() { return deductions; }
        public BigDecimal getDeduction() { return deduction; }
        public BigDecimal getBonus() { return bonus; }
        public String getBonusReason() { return bonusReason; }

        // % ca da qua co mat dung gio (bo ca nghi co phep)
        public int getRate() {
            return done == 0 ? 100 : (int) Math.round(100.0 * onTime / done);
        }

        // Ghi chu dien san vao phieu luong
        public String getPayNote() {
            List<String> parts = new ArrayList<>();
            if (late > 0) {
                parts.add("muộn " + late + " lần (" + lateMinutes + " phút)");
            }
            if (early > 0) {
                parts.add("về sớm " + early + " lần");
            }
            if (absent > 0) {
                parts.add("vắng không báo " + absent + " ca");
            }
            if (excused > 0) {
                parts.add("nghỉ có phép " + excused + " ca");
            }
            String s = parts.isEmpty() ? "Chuyên cần: đủ ca, đúng giờ" : "Chuyên cần: " + String.join(", ", parts);
            if (deduction.signum() > 0) {
                s += "; trừ " + String.format("%,d", deduction.longValue()).replace(',', '.') + " đ";
            }
            if (bonus.signum() > 0) {
                s += "; thưởng chuyên cần " + String.format("%,d", bonus.longValue()).replace(',', '.') + " đ";
            }
            return s.length() > 300 ? s.substring(0, 300) : s;
        }
    }
}
