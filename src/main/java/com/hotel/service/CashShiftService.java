package com.hotel.service;

import com.hotel.entity.CashShift;
import com.hotel.entity.PaymentMethod;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.CashShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// So quy ca le tan: moi luc chi co 1 ca mo; dong ca chot so thu theo phuong thuc, doi chieu tien mat, ghi chu ban giao
@Service
public class CashShiftService {

    // Lech tien mat tu muc nay tro len thi bat buoc ghi ly do
    public static final BigDecimal NOTE_REQUIRED_DIFF = BigDecimal.valueOf(50_000);

    private final CashShiftRepository repository;
    private final PaymentService paymentService;

    public CashShiftService(CashShiftRepository repository, PaymentService paymentService) {
        this.repository = repository;
        this.paymentService = paymentService;
    }

    public Optional<CashShift> current() {
        return repository.findOpen();
    }

    public Optional<CashShift> lastClosed() {
        return repository.findFirstByStatusOrderByClosedAtDesc(CashShift.Status.CLOSED);
    }

    public List<CashShift> all() {
        return repository.findAllForList();
    }

    public CashShift find(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy ca"));
    }

    // Cac khoan thu / chi trong ca (ca dang mo tinh den hien tai)
    public List<PaymentService.LedgerEntry> ledger(CashShift shift) {
        return paymentService.ledger(shift.getOpenedAt(), shift.isOpen() ? LocalDateTime.now() : shift.getClosedAt());
    }

    @Transactional
    public CashShift open(User staff, BigDecimal openingCash) {
        if (repository.findOpen().isPresent()) {
            throw new BusinessException("Đang có ca chưa đóng - người trực ca trước cần đóng ca trước");
        }
        BigDecimal cash = requireAmount(openingCash, "tiền mặt đầu ca");
        return repository.save(CashShift.builder().status(CashShift.Status.OPEN).openedBy(staff).openedAt(LocalDateTime.now())
                .openingCash(cash).previousLeftCash(lastClosed().map(CashShift::getLeftCash).orElse(null)).build());
    }

    @Transactional
    public CashShift close(Long shiftId, User staff, BigDecimal countedCash, BigDecimal cashToSafe, String note) {
        CashShift shift = find(shiftId);
        if (!shift.isOpen()) {
            throw new BusinessException("Ca này đã đóng");
        }
        BigDecimal counted = requireAmount(countedCash, "tiền mặt đếm được");
        BigDecimal safe = cashToSafe == null ? BigDecimal.ZERO : requireAmount(cashToSafe, "tiền nộp két");
        if (safe.compareTo(counted) > 0) {
            throw new BusinessException("Tiền nộp két không thể lớn hơn tiền mặt đếm được");
        }
        LocalDateTime now = LocalDateTime.now();
        Map<PaymentMethod, BigDecimal> totals = PaymentService.totalsByMethod(paymentService.ledger(shift.getOpenedAt(), now));
        shift.setCashIn(totals.get(PaymentMethod.CASH));
        shift.setTransferIn(totals.get(PaymentMethod.BANK_TRANSFER));
        shift.setOnlineIn(totals.get(PaymentMethod.ONLINE));
        shift.setCountedCash(counted);
        shift.setCashToSafe(safe);
        String text = note == null ? null : note.trim();
        text = text == null || text.isEmpty() ? null : text.substring(0, Math.min(1000, text.length()));
        if (shift.getCashDifference().abs().compareTo(NOTE_REQUIRED_DIFF) >= 0 && text == null) {
            throw new BusinessException("Tiền mặt lệch " + String.format("%,.0f", shift.getCashDifference()).replace(',', '.')
                    + " đ so với sổ - vui lòng ghi lý do trong ghi chú bàn giao");
        }
        shift.setHandoverNote(text);
        shift.setClosedBy(staff);
        shift.setClosedAt(now);
        shift.setStatus(CashShift.Status.CLOSED);
        return repository.save(shift);
    }

    private static BigDecimal requireAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("Vui lòng nhập " + label + " (không âm)");
        }
        return amount;
    }
}
