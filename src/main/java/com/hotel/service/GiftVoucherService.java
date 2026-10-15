package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.GiftVoucher;
import com.hotel.exception.BusinessException;
import com.hotel.repository.GiftVoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

// The qua tang: mua -> thanh toan (VNPay tu dong / chuyen khoan le tan xac nhan) -> ACTIVE 1 nam, email ma cho nguoi nhan.
// Dat phong nhap ma -> tru toi da bang so du; huy don -> tra lai so du
@Service
public class GiftVoucherService {

    public static final BigDecimal MIN_AMOUNT = BigDecimal.valueOf(200_000);
    public static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(20_000_000);
    public static final int VALID_MONTHS = 12;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public enum OnlineResult { PAID, ALREADY_PAID, NOT_FOUND, WRONG_AMOUNT }

    private final GiftVoucherRepository repository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public GiftVoucherService(GiftVoucherRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Transactional
    public GiftVoucher purchase(BigDecimal amount, String purchaserName, String purchaserEmail,
                                String recipientName, String recipientEmail, String message) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw BusinessException.of("gv.error.amount");
        }
        String pName = clean(purchaserName, 100);
        String rName = clean(recipientName, 100);
        String pEmail = clean(purchaserEmail, 100);
        String rEmail = clean(recipientEmail, 100);
        if (pName == null || rName == null) {
            throw BusinessException.of("gv.error.name");
        }
        if (pEmail == null || rEmail == null || !EMAIL.matcher(pEmail).matches() || !EMAIL.matcher(rEmail).matches()) {
            throw BusinessException.of("gv.error.email");
        }
        BigDecimal value = amount.setScale(0, RoundingMode.HALF_UP);
        return repository.save(GiftVoucher.builder().code(newCode()).amount(value).balance(value)
                .status(GiftVoucher.Status.PENDING_PAYMENT).purchaserName(pName).purchaserEmail(pEmail)
                .recipientName(rName).recipientEmail(rEmail).message(clean(message, 300)).build());
    }

    public GiftVoucher findForPurchaser(Long id, String token) {
        GiftVoucher v = repository.findById(id).orElse(null);
        if (v == null || token == null || !java.security.MessageDigest.isEqual(token.getBytes(), v.getAccessToken().getBytes())) {
            throw BusinessException.of("err.bookingLinkInvalid");
        }
        return v;
    }

    public List<GiftVoucher> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    // VNPay bao thanh toan thanh cong (goi lap lai chi ghi nhan 1 lan)
    @Transactional
    public OnlineResult confirmOnlinePayment(Long id, BigDecimal paidAmount, String transactionNo) {
        GiftVoucher v = repository.findById(id).orElse(null);
        if (v == null || v.getStatus() == GiftVoucher.Status.CANCELLED) {
            return OnlineResult.NOT_FOUND;
        }
        if (v.getStatus() == GiftVoucher.Status.ACTIVE) {
            return OnlineResult.ALREADY_PAID;
        }
        if (paidAmount == null || paidAmount.compareTo(v.getAmount()) != 0) {
            return OnlineResult.WRONG_AMOUNT;
        }
        v.setOnlineRef(transactionNo);
        activate(v);
        return OnlineResult.PAID;
    }

    // Le tan thay tien chuyen khoan da vao
    @Transactional
    public GiftVoucher confirmBankTransfer(Long id) {
        GiftVoucher v = repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy thẻ quà tặng"));
        if (v.getStatus() != GiftVoucher.Status.PENDING_PAYMENT) {
            throw new BusinessException("Thẻ này không ở trạng thái chờ thanh toán");
        }
        return activate(v);
    }

    @Transactional
    public GiftVoucher cancel(Long id) {
        GiftVoucher v = repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy thẻ quà tặng"));
        if (v.getStatus() != GiftVoucher.Status.PENDING_PAYMENT) {
            throw new BusinessException("Chỉ hủy được thẻ chưa thanh toán");
        }
        v.setStatus(GiftVoucher.Status.CANCELLED);
        return repository.save(v);
    }

    // So tien the tru duoc cho don co so tien con lai = amount (chua tru, goi apply sau khi luu don)
    @Transactional(readOnly = true)
    public BigDecimal quote(String code, BigDecimal amount) {
        GiftVoucher v = requireUsable(code);
        return v.getBalance().min(amount == null ? BigDecimal.ZERO : amount).max(BigDecimal.ZERO);
    }

    // Tru so du the cho don vua luu
    @Transactional
    public void apply(Booking booking) {
        if (booking.getGiftVoucherCode() == null || booking.getGiftVoucherAmountOrZero().signum() <= 0) {
            return;
        }
        GiftVoucher v = requireUsable(booking.getGiftVoucherCode());
        if (v.getBalance().compareTo(booking.getGiftVoucherAmountOrZero()) < 0) {
            throw BusinessException.of("gv.error.balance");
        }
        v.setBalance(v.getBalance().subtract(booking.getGiftVoucherAmountOrZero()));
        repository.save(v);
    }

    // Don dung the bi huy -> tra lai so du (ke ca khi the da het han: gia han them de khach dung tiep)
    @Transactional
    public void refund(Booking booking) {
        if (booking.getGiftVoucherCode() == null || booking.getGiftVoucherAmountOrZero().signum() <= 0) {
            return;
        }
        repository.findByCodeIgnoreCase(booking.getGiftVoucherCode()).ifPresent(v -> {
            v.setBalance(v.getBalance().add(booking.getGiftVoucherAmountOrZero()));
            if (v.getExpiresAt() != null && v.getExpiresAt().isBefore(LocalDateTime.now().plusMonths(1))) {
                v.setExpiresAt(LocalDateTime.now().plusMonths(1));
            }
            repository.save(v);
        });
    }

    private GiftVoucher requireUsable(String code) {
        GiftVoucher v = repository.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> BusinessException.of("gv.error.invalid"));
        if (!v.isUsable()) {
            throw BusinessException.of("gv.error.invalid");
        }
        return v;
    }

    private GiftVoucher activate(GiftVoucher v) {
        v.setStatus(GiftVoucher.Status.ACTIVE);
        v.setPaidAt(LocalDateTime.now());
        v.setExpiresAt(LocalDateTime.now().plusMonths(VALID_MONTHS));
        GiftVoucher saved = repository.save(v);
        emailService.sendGiftVoucher(saved);
        return saved;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("QT-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) {
                    sb.append('-');
                }
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Khong tao duoc ma the qua tang");
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
