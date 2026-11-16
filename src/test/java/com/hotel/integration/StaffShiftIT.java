package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.AttendanceRepository;
import com.hotel.repository.DutyShiftRepository;
import com.hotel.repository.ExpenseRepository;
import com.hotel.service.PayrollService;
import com.hotel.service.StaffShiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Phan ca, ai dang truc, cham cong tu dong, tinh / tra luong theo ngay cong
class StaffShiftIT extends ItFixtures {

    @Autowired private StaffShiftService service;
    @Autowired private PayrollService payroll;
    @Autowired private DutyShiftRepository dutyRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ExpenseRepository expenseRepository;

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);

    @Test
    void assign_skipsDuplicates_weekdayFilter_copyWeek_andGaps() {
        User a = persistUser(UserRole.STAFF, null);
        LocalDate from = StaffShiftService.weekStart(LocalDate.now());
        int n = service.assign(a.getId(), from, from.plusDays(6), DutyShift.Shift.MORNING,
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "Quầy", "Admin");
        assertThat(n).isEqualTo(2);
        assertThat(service.assign(a.getId(), from, from.plusDays(6), DutyShift.Shift.MORNING, null, null, "Admin")).isEqualTo(5);
        StaffShiftService.Week week = service.week(from.plusDays(3));
        StaffShiftService.Row row = week.rows().stream().filter(r -> r.staff().getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(row.days()).allMatch(cell -> cell.size() == 1);
        assertThat(week.gaps()).anyMatch(g -> g.startsWith("Ca đêm"));

        assertThat(service.copyWeek(from, from.plusWeeks(1), "Admin")).isEqualTo(7);
        assertThat(service.copyWeek(from, from.plusWeeks(1), "Admin")).isZero();
        assertThatThrownBy(() -> service.assign(a.getId(), from, from.minusDays(1), DutyShift.Shift.NIGHT, null, null, "A"))
                .hasMessageContaining("Ngày kết thúc");
        assertThatThrownBy(() -> service.assign(persistUser(UserRole.CUSTOMER, CustomerType.NEW).getId(), from, null,
                DutyShift.Shift.NIGHT, null, null, "A")).hasMessageContaining("nhân viên");
    }

    @Test
    void touch_recordsCheckIn_late_lastSeen_nightShiftBelongsToStartDay_andOnDuty() {
        User a = persistUser(UserRole.STAFF, null);
        User hk = persistUser(UserRole.HOUSEKEEPER, null);
        LocalDate day = LocalDate.now().minusDays(2);
        service.assign(a.getId(), day, day, DutyShift.Shift.MORNING, null, null, "A");
        service.assign(hk.getId(), day, day, DutyShift.Shift.NIGHT, null, null, "A");

        // Vao 06:20 -> muon 20 phut; thao tac 11:05 -> gio ra 11:05, 4h45 = 1 cong
        Attendance in = service.touch(a.getId(), day.atTime(6, 20));
        assertThat(in.getShift()).isEqualTo(DutyShift.Shift.MORNING);
        assertThat(in.getLateMinutes()).isEqualTo(20);
        service.touch(a.getId(), day.atTime(11, 5));
        Attendance after = attendanceRepository.findByStaffIdAndWorkDate(a.getId(), day).orElseThrow();
        assertThat(after.getWorkedText()).isEqualTo("4h45");
        assertThat(after.getUnits()).isEqualByComparingTo("1");

        // Ca dem: vao 21:58 (khong muon), 02:00 sang hom sau van tinh ngay bat dau ca
        Attendance night = service.touch(hk.getId(), day.atTime(21, 58));
        assertThat(night.getLateMinutes()).isZero();
        Attendance late = service.touch(hk.getId(), day.plusDays(1).atTime(2, 0));
        assertThat(late.getWorkDate()).isEqualTo(day);
        assertThat(late.getUnits()).isEqualByComparingTo("1");

        // Ai dang truc luc 10:00: a co mat (thao tac 11:05 > 10:00 -> trong 30 phut), hk chua co ca nay
        StaffShiftService.OnDuty od = service.onDuty(day.atTime(11, 20));
        assertThat(od.shift()).isEqualTo(DutyShift.Shift.MORNING);
        assertThat(od.scheduled()).anyMatch(d -> d.staff().getId().equals(a.getId()) && d.present());
        // 2 tieng sau khong thao tac -> khong con "co mat"
        assertThat(service.onDuty(day.atTime(13, 30)).scheduled()).noneMatch(StaffShiftService.Duty::present);

        // Ket thuc ca + sua cong
        service.touch(a.getId(), day.atTime(12, 0));
        Attendance ended = service.endShift(a.getId(), day.atTime(12, 30));
        assertThat(ended.isEnded()).isTrue();
        assertThatThrownBy(() -> service.adjust(ended.getId(), new BigDecimal("0.5"), " ")).hasMessageContaining("lý do");
        assertThatThrownBy(() -> service.adjust(ended.getId(), new BigDecimal("0.3"), "x")).hasMessageContaining("0,5");
        assertThat(service.adjust(ended.getId(), new BigDecimal("0.5"), "Về sớm").getUnits()).isEqualByComparingTo("0.5");
    }

    @Test
    void shortDay_isHalfUnit_offScheduleHasNoShift() {
        User a = persistUser(UserRole.ACCOUNTANT, null);
        LocalDate day = LocalDate.now().minusDays(1);
        Attendance x = service.touch(a.getId(), day.atTime(9, 0));
        service.touch(a.getId(), day.atTime(10, 30));
        assertThat(x.getShift()).isNull();
        assertThat(attendanceRepository.findByStaffIdAndWorkDate(a.getId(), day).orElseThrow().getUnits()).isEqualByComparingTo("0.5");
    }

    @Test
    void payroll_unitsTimesWage_plusBonus_createsExpense_cancelRemovesIt() {
        User a = persistUser(UserRole.HOUSEKEEPER, null);
        YearMonth m = YearMonth.now().minusMonths(1);
        service.touch(a.getId(), m.atDay(3).atTime(8, 0));
        service.touch(a.getId(), m.atDay(3).atTime(15, 0));
        service.touch(a.getId(), m.atDay(4).atTime(8, 0));
        service.addManual(a.getId(), m.atDay(5), DutyShift.Shift.AFTERNOON, BigDecimal.ONE, "Quên đăng nhập");

        assertThatThrownBy(() -> payroll.pay(a.getId(), m, null, null, null, "KT")).hasMessageContaining("lương ngày");
        payroll.setDailyWage(a.getId(), new BigDecimal("300000"));
        PayrollService.Line line = payroll.sheet(m).lines().stream().filter(l -> l.staff().getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(line.units()).isEqualByComparingTo("2.5");
        assertThat(line.gross()).isEqualByComparingTo("750000");

        Payslip p = payroll.pay(a.getId(), m, new BigDecimal("100000"), new BigDecimal("50000"), "Chuyên cần", "Kế toán B");
        assertThat(p.getAmount()).isEqualByComparingTo("800000");
        Expense e = expenseRepository.findById(p.getExpenseId()).orElseThrow();
        assertThat(e.getCategory()).isEqualTo(Expense.Category.PAYROLL);
        assertThat(e.getExpenseDate()).isEqualTo(m.atEndOfMonth());
        assertThat(e.getDescription()).contains(a.getFullName()).contains("2.5 công");
        assertThatThrownBy(() -> payroll.pay(a.getId(), m, null, null, null, "KT")).hasMessageContaining("Đã trả");
        assertThatThrownBy(() -> payroll.pay(a.getId(), YearMonth.now().plusMonths(1), null, null, null, "KT")).hasMessageContaining("Chưa tới");

        payroll.cancel(p.getId());
        assertThat(expenseRepository.findById(e.getId())).isEmpty();
    }

    @Test
    void pages_accessByRole_andInterceptorChecksIn() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        User staff = persistUser(UserRole.STAFF, null);
        User hk = persistUser(UserRole.HOUSEKEEPER, null);
        User acc = persistUser(UserRole.ACCOUNTANT, null);

        // Mo trang bat ky trong cong quan tri -> tu cham cong
        mockMvc.perform(get("/admin/duty-roster").with(as(hk))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch trực ca")))
                .andExpect(content().string(containsString("Đang trực")));
        assertThat(service.current(hk.getId(), LocalDateTime.now())).isPresent();
        mockMvc.perform(get("/admin/attendance/me").with(as(hk))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Kết thúc ca")));
        mockMvc.perform(post("/admin/attendance/end").with(csrf()).with(as(hk)))
                .andExpect(flash().attribute("successMessage", containsString("Đã kết thúc ca")));

        // Xep ca chi ADMIN; cham cong tat ca chi ADMIN; luong ADMIN + ke toan
        mockMvc.perform(post("/admin/duty-roster/assign").with(csrf()).with(as(staff)).param("staffId", staff.getId().toString())
                .param("shift", "MORNING").param("from", MON.toString())).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/duty-roster/assign").with(csrf()).with(as(admin)).param("staffId", staff.getId().toString())
                        .param("shift", "NIGHT").param("from", LocalDate.now().toString()))
                .andExpect(flash().attribute("successMessage", "Đã xếp 1 ca"));
        assertThat(dutyRepository.existsByStaffIdAndWorkDateAndShift(staff.getId(), LocalDate.now(), DutyShift.Shift.NIGHT)).isTrue();
        mockMvc.perform(get("/admin/attendance").with(as(staff))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/attendance").with(as(admin))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/payroll").with(as(acc))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Bảng lương")));
        mockMvc.perform(get("/admin/payroll").with(as(staff))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/payroll/wage").with(csrf()).with(as(acc)).param("staffId", hk.getId().toString())
                .param("dailyWage", "1")).andExpect(status().isForbidden());

        // Hom nay + tong quan hien khung "Dang truc"
        mockMvc.perform(get("/admin/today").with(as(staff))).andExpect(status().isOk())
                .andExpect(content().string(containsString("dang-truc")));
        mockMvc.perform(get("/admin/dashboard").with(as(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("dang-truc")));
    }
}
