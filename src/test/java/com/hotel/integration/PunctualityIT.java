package com.hotel.integration;

import com.hotel.entity.Attendance;
import com.hotel.entity.DutyShift;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.AttendanceRepository;
import com.hotel.repository.DutyShiftRepository;
import com.hotel.service.PunctualityService;
import com.hotel.service.PunctualityService.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Chuyen can: doi chieu lich truc voi cham cong -> dung gio / muon / ve som / vang / co phep, goi y tru / thuong khi tra luong
class PunctualityIT extends ItFixtures {

    @Autowired private PunctualityService service;
    @Autowired private DutyShiftRepository dutyRepository;
    @Autowired private AttendanceRepository attendanceRepository;

    // Thang da qua hoan toan -> moi ca deu da ket thuc
    private static final YearMonth MONTH = YearMonth.of(2026, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 10, 0);

    private User staffWithWage(UserRole role, long wage) {
        User u = persistUser(role, null);
        u.setDailyWage(BigDecimal.valueOf(wage));
        return userRepository.save(u);
    }

    private DutyShift duty(User u, int day, DutyShift.Shift shift) {
        return dutyRepository.save(DutyShift.builder().staff(u).workDate(MONTH.atDay(day)).shift(shift).createdBy("Test").build());
    }

    // Vao ca luc (gio bat dau + offset phut), ra luc gio het ca + endOffset phut
    private Attendance came(User u, DutyShift d, int lateMin, int endOffsetMin, boolean ended) {
        return attendanceRepository.save(Attendance.builder().staff(u).workDate(d.getWorkDate()).shift(d.getShift())
                .checkInAt(d.getStartAt().plusMinutes(lateMin)).checkOutAt(d.getEndAt().plusMinutes(endOffsetMin))
                .ended(ended).lateMinutes(Math.max(0, lateMin)).build());
    }

    @Test
    void classifiesEachShift_andSuggestsDeduction() {
        User a = staffWithWage(UserRole.STAFF, 480_000); // 1.000 d / phut
        came(a, duty(a, 1, DutyShift.Shift.MORNING), -10, 0, false);          // dung gio
        came(a, duty(a, 2, DutyShift.Shift.MORNING), 3, 0, false);            // tre 3 phut: trong 5 phut cho phep
        came(a, duty(a, 3, DutyShift.Shift.MORNING), 10, 0, false);           // muon lan 1 - mien
        came(a, duty(a, 4, DutyShift.Shift.AFTERNOON), 12, 0, false);         // muon lan 2 - mien
        came(a, duty(a, 5, DutyShift.Shift.MORNING), 10, 0, false);           // muon lan 3 - mien
        came(a, duty(a, 6, DutyShift.Shift.MORNING), 10, 0, false);           // muon lan 4 -> 10.000 nhung toi thieu 20.000
        came(a, duty(a, 7, DutyShift.Shift.NIGHT), 45, 0, false);             // muon lan 5, 45 phut -> 45.000
        duty(a, 8, DutyShift.Shift.MORNING);                                   // vang -> 50% luong ngay = 240.000
        came(a, duty(a, 9, DutyShift.Shift.MORNING), 0, -120, true);          // bam ket thuc ca som 2 tieng -> ve som, khong tru

        Map<Long, PunctualityService.Summary> m = service.month(MONTH, a.getId(), NOW);
        PunctualityService.Summary s = m.get(a.getId());
        assertThat(s.getScheduled()).isEqualTo(9);
        assertThat(s.getDone()).isEqualTo(9);
        assertThat(s.getOnTime()).isEqualTo(2);
        assertThat(s.getLate()).isEqualTo(5);
        assertThat(s.getLateMinutes()).isEqualTo(10 + 12 + 10 + 10 + 45);
        assertThat(s.getAbsent()).isEqualTo(1);
        assertThat(s.getEarly()).isEqualTo(1);
        assertThat(s.getDeductions()).hasSize(3);
        assertThat(s.getDeduction()).isEqualByComparingTo("305000");
        assertThat(s.getBonus()).isEqualByComparingTo("0");
        assertThat(s.getRate()).isEqualTo(22);
        assertThat(s.getPayNote()).contains("muộn 5 lần (87 phút)").contains("vắng không báo 1 ca").contains("trừ 305.000 đ");

        List<PunctualityService.Check> checks = s.getChecks();
        assertThat(checks).extracting(PunctualityService.Check::status).containsExactly(
                Status.ON_TIME, Status.ON_TIME, Status.LATE, Status.LATE, Status.LATE, Status.LATE, Status.LATE, Status.ABSENT, Status.EARLY_LEAVE);
        assertThat(checks.get(6).getShort()).isEqualTo("Muộn 45'");
        assertThat(checks.get(7).getShort()).isEqualTo("Vắng");
        assertThat(checks.get(8).earlyMinutes()).isEqualTo(120);
    }

    @Test
    void secondShiftSameDay_currentShift_andPerfectBonus() {
        User a = staffWithWage(UserRole.HOUSEKEEPER, 300_000);
        // Ca sang + ca chieu cung ngay, chi 1 ban cham cong 06:00 - 21:50 -> ca 2 vao luc dang lam: dung gio
        DutyShift m1 = duty(a, 10, DutyShift.Shift.MORNING);
        DutyShift a1 = duty(a, 10, DutyShift.Shift.AFTERNOON);
        came(a, m1, 0, 7 * 60 + 50, false);
        // Lam het ca sang roi ve, ca chieu khong vao -> vang ca chieu
        DutyShift m2 = duty(a, 11, DutyShift.Shift.MORNING);
        DutyShift a2 = duty(a, 11, DutyShift.Shift.AFTERNOON);
        attendanceRepository.save(Attendance.builder().staff(a).workDate(m2.getWorkDate()).shift(m2.getShift())
                .checkInAt(m2.getStartAt()).checkOutAt(m2.getEndAt().minusMinutes(10)).ended(true).lateMinutes(0).build());
        Map<Long, PunctualityService.Check> byDuty = service.byDuty(MONTH.atDay(10), MONTH.atDay(11), NOW);
        assertThat(byDuty.get(m1.getId()).status()).isEqualTo(Status.ON_TIME);
        assertThat(byDuty.get(a1.getId()).status()).isEqualTo(Status.ON_TIME);
        assertThat(byDuty.get(m2.getId()).status()).isEqualTo(Status.ON_TIME);
        assertThat(byDuty.get(a2.getId()).status()).isEqualTo(Status.ABSENT);

        // Ca dang dien ra: chua vao sau 5 phut -> "Chua vao"; ca sau -> chua toi
        User b = staffWithWage(UserRole.STAFF, 300_000);
        LocalDate today = NOW.toLocalDate();
        DutyShift now1 = dutyRepository.save(DutyShift.builder().staff(b).workDate(today).shift(DutyShift.Shift.MORNING).build());
        DutyShift later = dutyRepository.save(DutyShift.builder().staff(b).workDate(today).shift(DutyShift.Shift.AFTERNOON).build());
        Map<Long, PunctualityService.Check> todayChecks = service.byDuty(today, today, NOW);
        assertThat(todayChecks.get(now1.getId()).status()).isEqualTo(Status.NOT_YET_IN);
        assertThat(todayChecks.get(later.getId()).status()).isEqualTo(Status.UPCOMING);

        // 15 ca dung gio, khong vi pham -> goi y thuong chuyen can
        User c = staffWithWage(UserRole.ACCOUNTANT, 350_000);
        for (int d = 1; d <= 15; d++) {
            came(c, duty(c, d, DutyShift.Shift.MORNING), 0, 0, false);
        }
        PunctualityService.Summary sc = service.month(MONTH, c.getId(), NOW).get(c.getId());
        assertThat(sc.getRate()).isEqualTo(100);
        assertThat(sc.getBonus()).isEqualByComparingTo("300000");
        assertThat(sc.getPayNote()).contains("đủ ca, đúng giờ").contains("thưởng chuyên cần 300.000 đ");
    }

    @Test
    void excuse_turnsAbsenceIntoPaidLeave_withoutDeduction() {
        User a = staffWithWage(UserRole.STAFF, 400_000);
        DutyShift d = duty(a, 20, DutyShift.Shift.MORNING);
        assertThat(service.month(MONTH, a.getId(), NOW).get(a.getId()).getDeduction()).isEqualByComparingTo("200000");

        assertThatThrownBy(() -> service.excuse(a.getId(), d.getWorkDate(), d.getShift(), " ", "QL")).hasMessageContaining("lý do");
        assertThatThrownBy(() -> service.excuse(a.getId(), d.getWorkDate(), DutyShift.Shift.NIGHT, "Bị ốm", "QL")).hasMessageContaining("không có ca");
        service.excuse(a.getId(), d.getWorkDate(), d.getShift(), "Ốm, có giấy khám", "Quản lý A");

        PunctualityService.Summary s = service.month(MONTH, a.getId(), NOW).get(a.getId());
        assertThat(s.getExcused()).isEqualTo(1);
        assertThat(s.getAbsent()).isZero();
        assertThat(s.getDeduction()).isEqualByComparingTo("0");
        Attendance att = attendanceRepository.findByStaffIdAndWorkDate(a.getId(), d.getWorkDate()).orElseThrow();
        assertThat(att.getUnits()).isEqualByComparingTo("0");
        assertThat(att.getNote()).contains("Nghỉ có phép: Ốm, có giấy khám").contains("Quản lý A");
        // Ngay da co cham cong thi khong ghi nhan lai
        assertThatThrownBy(() -> service.excuse(a.getId(), d.getWorkDate(), d.getShift(), "Bị ốm", "QL")).hasMessageContaining("đã có chấm công");
    }

    @Test
    void pages_byRole_rosterShowsLateness_payrollPrefilled() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        User acc = persistUser(UserRole.ACCOUNTANT, null);
        User other = persistUser(UserRole.STAFF, null);
        User a = staffWithWage(UserRole.STAFF, 480_000);
        // Tuan nay: 1 ca muon 20 phut hom qua (ca sang da xong)
        LocalDate yesterday = LocalDate.now().minusDays(1);
        DutyShift d = dutyRepository.save(DutyShift.builder().staff(a).workDate(yesterday).shift(DutyShift.Shift.MORNING).build());
        attendanceRepository.save(Attendance.builder().staff(a).workDate(yesterday).shift(d.getShift())
                .checkInAt(d.getStartAt().plusMinutes(20)).checkOutAt(d.getEndAt()).ended(false).lateMinutes(20).build());
        // Thang 10: vang 1 ca -> goi y tru 240.000
        duty(a, 15, DutyShift.Shift.AFTERNOON);

        mockMvc.perform(get("/admin/duty-roster").param("week", yesterday.toString()).with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Muộn 20&#39;")))
                .andExpect(content().string(containsString("Tổng hợp chuyên cần tháng")));
        // Nhan vien khac khong thay ket qua cua dong nghiep
        mockMvc.perform(get("/admin/duty-roster").param("week", yesterday.toString()).with(as(other))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Muộn 20&#39;"))));
        // Chinh nguoi do thay ca cua minh
        mockMvc.perform(get("/admin/duty-roster").param("week", yesterday.toString()).with(as(a))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Muộn 20&#39;")));

        mockMvc.perform(get("/admin/punctuality").param("month", "2026-10").with(as(acc))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chuyên cần tháng")))
                .andExpect(content().string(containsString(a.getFullName())))
                .andExpect(content().string(containsString("Vắng không báo")));
        mockMvc.perform(get("/admin/punctuality").param("month", "2026-10").param("staffId", a.getId().toString()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Có phép")))
                .andExpect(content().string(containsString("-240.000")));
        mockMvc.perform(get("/admin/punctuality").with(as(other))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/punctuality/excuse").with(csrf()).with(as(acc)).param("staffId", a.getId().toString())
                .param("day", MONTH.atDay(15).toString()).param("shift", "AFTERNOON").param("reason", "Ốm")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/punctuality/excuse").with(csrf()).with(as(admin)).param("staffId", a.getId().toString())
                        .param("day", MONTH.atDay(15).toString()).param("shift", "AFTERNOON").param("reason", "Việc gia đình"))
                .andExpect(flash().attribute("successMessage", containsString("nghỉ có phép")));

        // Bang luong thang 11: muon 20 phut lan 1 -> mien tru, ghi chu dien san
        mockMvc.perform(get("/admin/payroll").param("month", YearMonth.from(yesterday).toString()).with(as(acc))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Muộn 1")))
                .andExpect(content().string(containsString("Chuyên cần: muộn 1 lần (20 phút)")));

        // Cham cong cua toi: thay lan muon cua minh
        mockMvc.perform(get("/admin/attendance/me").param("month", YearMonth.from(yesterday).toString()).with(as(a))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chuyên cần theo lịch trực")))
                .andExpect(content().string(containsString("Đi muộn 20 phút")));
    }
}
