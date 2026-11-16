package com.hotel.service;

import com.hotel.entity.Attendance;
import com.hotel.entity.Expense;
import com.hotel.entity.Payslip;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ExpenseRepository;
import com.hotel.repository.PayslipRepository;
import com.hotel.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

// Tinh luong theo ngay cong: so cong (cham cong tu dong, quan tri sua duoc) x luong ngay + thuong - khau tru.
// Tra luong -> luu phieu luong + ghi khoan chi "Luong & bao hiem" (vao lai lo thang do)
@Service
public class PayrollService {

    public static final BigDecimal MAX_DAILY_WAGE = new BigDecimal("10000000");

    private final StaffShiftService shiftService;
    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;
    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;

    public PayrollService(StaffShiftService shiftService, PayslipRepository payslipRepository, UserRepository userRepository,
                          ExpenseService expenseService, ExpenseRepository expenseRepository) {
        this.shiftService = shiftService;
        this.payslipRepository = payslipRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public Sheet sheet(YearMonth month) {
        List<Attendance> list = shiftService.attendances(month.atDay(1), month.atEndOfMonth(), null);
        Map<Long, StaffShiftService.Summary> sums = StaffShiftService.summarize(list);
        Map<Long, Payslip> paid = new HashMap<>();
        payslipRepository.findForMonth(month.atDay(1)).forEach(p -> paid.put(p.getStaff().getId(), p));
        List<Line> lines = new ArrayList<>();
        for (User u : shiftService.staffList()) {
            StaffShiftService.Summary s = sums.get(u.getId());
            Payslip p = paid.get(u.getId());
            if (s == null && p == null && Boolean.TRUE.equals(u.getLocked())) {
                continue;
            }
            BigDecimal units = s != null ? s.getUnits() : BigDecimal.ZERO;
            BigDecimal wage = u.getDailyWage() != null ? u.getDailyWage() : BigDecimal.ZERO;
            lines.add(new Line(u, s, units, wage, money(units.multiply(wage)), p));
        }
        BigDecimal totalDue = lines.stream().filter(l -> l.payslip() == null).map(Line::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = lines.stream().filter(l -> l.payslip() != null).map(l -> l.payslip().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Sheet(month, lines, totalDue, totalPaid);
    }

    @Transactional
    public void setDailyWage(Long staffId, BigDecimal wage) {
        User u = userRepository.findById(staffId).filter(x -> StaffShiftService.STAFF_ROLES.contains(x.getRole()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên"));
        if (wage != null && (wage.signum() < 0 || wage.compareTo(MAX_DAILY_WAGE) > 0)) {
            throw new BusinessException("Lương ngày từ 0 đến 10.000.000 đ");
        }
        u.setDailyWage(wage == null ? null : money(wage));
    }

    // Chot va tra luong thang cho 1 nhan vien
    @Transactional
    public Payslip pay(Long staffId, YearMonth month, BigDecimal bonus, BigDecimal deduction, String note, String by) {
        if (month.isAfter(YearMonth.now())) {
            throw new BusinessException("Chưa tới tháng " + month.getMonthValue() + "/" + month.getYear());
        }
        if (payslipRepository.findByStaffIdAndPayMonth(staffId, month.atDay(1)).isPresent()) {
            throw new BusinessException("Đã trả lương tháng này cho nhân viên");
        }
        Line line = sheet(month).lines().stream().filter(l -> l.staff().getId().equals(staffId)).findFirst()
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên"));
        if (line.wage().signum() <= 0) {
            throw new BusinessException("Chưa đặt lương ngày cho " + line.staff().getFullName());
        }
        BigDecimal b = nonNegative(bonus, "Thưởng");
        BigDecimal d = nonNegative(deduction, "Khấu trừ");
        BigDecimal amount = money(line.gross().add(b).subtract(d));
        if (amount.signum() <= 0) {
            throw new BusinessException("Số tiền lương phải lớn hơn 0");
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        if (text != null && text.length() > 300) {
            throw new BusinessException("Ghi chú tối đa 300 ký tự");
        }
        // Chi phi tinh vao thang luong (tra sau thi ghi ngay cuoi thang do)
        LocalDate expenseDate = month.equals(YearMonth.now()) ? LocalDate.now() : month.atEndOfMonth();
        String desc = "Lương T" + month.getMonthValue() + "/" + month.getYear() + " - " + line.staff().getFullName()
                + " (" + line.units().stripTrailingZeros().toPlainString() + " công)";
        Expense e = expenseService.create(Expense.Category.PAYROLL, desc.length() > 200 ? desc.substring(0, 200) : desc, amount,
                Expense.Recurrence.ONE_OFF, expenseDate, null, null, by);
        return payslipRepository.save(Payslip.builder().staff(line.staff()).payMonth(month.atDay(1)).workUnits(line.units())
                .dailyWage(line.wage()).bonus(b).deduction(d).amount(amount).note(text).expenseId(e.getId())
                .paidAt(LocalDateTime.now()).paidBy(by).build());
    }

    // Huy phieu luong tra nham (xoa luon khoan chi da ghi)
    @Transactional
    public void cancel(Long payslipId) {
        Payslip p = payslipRepository.findById(payslipId).orElseThrow(() -> new BusinessException("Không tìm thấy phiếu lương"));
        if (p.getExpenseId() != null) {
            expenseRepository.findById(p.getExpenseId()).ifPresent(expenseRepository::delete);
        }
        payslipRepository.delete(p);
    }

    private static BigDecimal nonNegative(BigDecimal v, String label) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v.signum() < 0 || v.compareTo(ExpenseService.MAX_AMOUNT) > 0) {
            throw new BusinessException(label + " không được âm");
        }
        return money(v);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    public record Line(User staff, StaffShiftService.Summary summary, BigDecimal units, BigDecimal wage, BigDecimal gross,
                       Payslip payslip) {
    }

    public record Sheet(YearMonth month, List<Line> lines, BigDecimal totalDue, BigDecimal totalPaid) {
    }
}
