package com.hotel.config.demo;

import com.hotel.entity.Attendance;
import com.hotel.entity.DutyShift;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.AttendanceRepository;
import com.hotel.repository.DutyShiftRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.StaffShiftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

// Lich truc + cham cong demo: 6 tuan truoc -> 2 tuan toi cho nhan vien demo (@example.com), luong ngay theo vai tro.
// Le tan xoay 3 ca, buong phong ca sang / chieu, ke toan ca sang ngay thuong; moi nguoi nghi 1 ngay / tuan.
// Chay sau DemoDataSeeder, chi khi chua co lich truc nao (DB dev da co du lieu demo cu van duoc bo sung)
@Component
@Order(20)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class StaffRosterDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffRosterDemoSeeder.class);

    private final DutyShiftRepository duties;
    private final AttendanceRepository attendances;
    private final UserRepository users;

    public StaffRosterDemoSeeder(DutyShiftRepository duties, AttendanceRepository attendances, UserRepository users) {
        this.duties = duties;
        this.attendances = attendances;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (duties.count() > 0) {
            return;
        }
        List<User> staff = users.findByRoleInOrderByFullNameAsc(StaffShiftService.STAFF_ROLES).stream()
                .filter(u -> u.getRole() != UserRole.ADMIN && u.getEmail().endsWith("@example.com") && !Boolean.TRUE.equals(u.getLocked()))
                .toList();
        if (staff.isEmpty()) {
            return;
        }
        Random rnd = new Random(20261117L);
        LocalDateTime now = LocalDateTime.now();
        LocalDate from = StaffShiftService.weekStart(now.toLocalDate()).minusWeeks(6);
        LocalDate to = from.plusWeeks(8).plusDays(6);
        DutyShift.Shift[] rotation = DutyShift.Shift.values();
        int frontDesk = 0;
        int housekeeper = 0;
        int count = 0;
        for (User u : staff) {
            u.setDailyWage(BigDecimal.valueOf(switch (u.getRole()) {
                case STAFF -> 350_000;
                case HOUSEKEEPER -> 300_000;
                default -> 420_000;
            }));
            int index = u.getRole() == UserRole.STAFF ? frontDesk++ : u.getRole() == UserRole.HOUSEKEEPER ? housekeeper++ : 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                long week = java.time.temporal.ChronoUnit.WEEKS.between(from, d);
                // Ngay nghi xoay vong theo nguoi
                if ((d.getDayOfWeek().getValue() + index) % 7 == 0) {
                    continue;
                }
                DutyShift.Shift shift = switch (u.getRole()) {
                    case STAFF -> rotation[(int) ((index + week) % 3)];
                    case HOUSEKEEPER -> (index + week) % 2 == 0 ? DutyShift.Shift.MORNING : DutyShift.Shift.AFTERNOON;
                    default -> d.getDayOfWeek().getValue() <= 5 ? DutyShift.Shift.MORNING : null;
                };
                if (shift == null) {
                    continue;
                }
                duties.save(DutyShift.builder().staff(u).workDate(d).shift(shift).createdBy("Dữ liệu demo").build());
                count++;
                LocalDateTime start = shift.startOn(d);
                if (!start.isBefore(now) || rnd.nextInt(40) == 0) {
                    continue; // ca chua toi / thinh thoang vang
                }
                int lateMin = rnd.nextInt(10) < 8 ? -rnd.nextInt(15) : 6 + rnd.nextInt(25);
                LocalDateTime in = start.plusMinutes(lateMin);
                LocalDateTime out = shift.endOn(d).plusMinutes(rnd.nextInt(25) - 10);
                boolean running = out.isAfter(now);
                attendances.save(Attendance.builder().staff(u).workDate(d).shift(shift).checkInAt(in)
                        .checkOutAt(running ? now.minusMinutes(rnd.nextInt(10)) : out).ended(!running)
                        .lateMinutes(lateMin > StaffShiftService.LATE_GRACE_MINUTES ? lateMin : 0).build());
            }
        }
        log.info("Demo: da xep {} ca truc + cham cong cho {} nhan vien", count, staff.size());
    }
}
