package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Thanh toan khi khach tra phong (check-out): tien mat / chuyen khoan (QR) / the (chua ho tro)
@Service
public class PaymentService {

    // Noi dung chuyen khoan kem ma don de nhan vien doi chieu tien vao la cua don nao, VD: "HB15"
    private static final String TRANSFER_PREFIX = "HB";
    private static final BigDecimal MAX_CHARGE = new BigDecimal("100000000");

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final EmailService emailService;
    private final BookingChargeRepository chargeRepository;
    private final BookingRepository bookingRepository;

    @Value("${app.payment.bank-id:MB}")
    private String bankId;

    @Value("${app.payment.account-no:0000000000}")
    private String accountNo;

    @Value("${app.payment.account-name:SONG XANH RESORT}")
    private String accountName;

    public PaymentService(PaymentRepository paymentRepository, BookingService bookingService, EmailService emailService,
                          BookingChargeRepository chargeRepository, BookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.emailService = emailService;
        this.chargeRepository = chargeRepository;
        this.bookingRepository = bookingRepository;
    }

    public Optional<Payment> findByBookingId(Long bookingId) {
        return paymentRepository.findByBookingId(bookingId);
    }

    // Tra ve map bookingId -> Payment, dung cho cac trang danh sach (tranh truy van tung don)
    public Map<Long, Payment> findByBookings(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bookings.stream().map(Booking::getId).toList();
        return paymentRepository.findByBookingIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity()));
    }

    public boolean isPaid(Long bookingId) {
        return findByBookingId(bookingId).map(p -> p.getStatus() == PaymentStatus.PAID).orElse(false);
    }

    public String transferContent(Booking booking) {
        return TRANSFER_PREFIX + booking.getId();
    }

    // Noi dung chuyen coc, VD "HB15 COC" - phan biet voi tien thanh toan khi tra phong
    public String depositTransferContent(Booking booking) {
        return TRANSFER_PREFIX + booking.getId() + " COC";
    }

    // Anh QR VietQR (chuan Napas) - app ngan hang nao quet cung tu dien san so tien va noi dung chuyen khoan
    public String buildQrUrl(Booking booking) {
        return vietQrUrl(amountDue(booking), transferContent(booking));
    }

    // QR chuyen tien coc, hien cho khach ngay sau khi dat phong
    public String buildDepositQrUrl(Booking booking) {
        return vietQrUrl(booking.getDepositAmount(), depositTransferContent(booking));
    }

    private String vietQrUrl(BigDecimal amount, String content) {
        return "https://img.vietqr.io/image/" + bankId + "-" + accountNo + "-compact2.png"
                + "?amount=" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                + "&addInfo=" + URLEncoder.encode(content, StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode(accountName, StandardCharsets.UTF_8);
    }

    public String getBankId() {
        return bankId;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getAccountName() {
        return accountName;
    }

    // ===== Phu phi phat sinh (minibar, giat ui, hu hong, tra phong muon...) =====

    public List<BookingCharge> findCharges(Long bookingId) {
        return chargeRepository.findByBookingIdOrderByCreatedAtAsc(bookingId);
    }

    public BigDecimal chargesTotal(Long bookingId) {
        return findCharges(bookingId).stream().map(BookingCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Tong gia tri don = tien don (phong + combo - giam gia) + phu phi
    public BigDecimal grandTotal(Booking booking) {
        return booking.getTotalAmount().add(chargesTotal(booking.getId()));
    }

    // Con phai thu khi check-out = tong gia tri don - tien coc da nhan
    public BigDecimal amountDue(Booking booking) {
        return grandTotal(booking).subtract(booking.getDepositPaidAmount()).max(BigDecimal.ZERO);
    }

    @Transactional
    public BookingCharge addCharge(Long bookingId, ChargeType type, String description, BigDecimal amount) {
        Booking booking = requireChargeable(bookingId);
        if (type == null) {
            throw new BusinessException("Vui lòng chọn loại phụ phí");
        }
        if (type.isServiceCharge()) {
            throw new BusinessException("Tiền tour lặn / đưa đón sân bay được tính tự động khi xác nhận dịch vụ, không thêm tay");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tiền phụ phí phải lớn hơn 0");
        }
        if (amount.compareTo(MAX_CHARGE) > 0) {
            throw new BusinessException("Số tiền phụ phí quá lớn, vui lòng kiểm tra lại");
        }
        String text = description == null ? null : description.trim();
        if (text != null && text.length() > 200) {
            throw new BusinessException("Ghi chú phụ phí tối đa 200 ký tự");
        }
        return chargeRepository.save(BookingCharge.builder()
                .booking(booking)
                .type(type)
                .description(text == null || text.isEmpty() ? null : text)
                .amount(amount.setScale(0, RoundingMode.HALF_UP))
                .build());
    }

    @Transactional
    public void removeCharge(Long bookingId, Long chargeId) {
        requireChargeable(bookingId);
        BookingCharge charge = chargeRepository.findById(chargeId)
                .filter(c -> c.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new BusinessException("Không tìm thấy phụ phí"));
        if (charge.getType().isServiceCharge()) {
            throw new BusinessException("Muốn bỏ tiền dịch vụ này, hãy hủy dịch vụ ở mục Dịch vụ thêm của đơn");
        }
        chargeRepository.delete(charge);
    }

    // Chi them/xoa phu phi khi khach dang o (CHECKED_IN) va chua thanh toan - tranh lech so tien da thu
    private Booking requireChargeable(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ thêm/xóa phụ phí cho đơn đang ở trạng thái Đã nhận phòng");
        }
        if (isPaid(bookingId)) {
            throw new BusinessException("Đơn đã thanh toán, không thể thay đổi phụ phí");
        }
        return booking;
    }

    // Nhan vien kiem tra app ngan hang thay tien da vao -> ghi nhan da thanh toan chuyen khoan
    @Transactional
    public Payment confirmBankTransfer(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ ghi nhận thanh toán cho đơn đang ở trạng thái Đã nhận phòng");
        }
        return markPaid(booking, PaymentMethod.BANK_TRANSFER);
    }

    // Hoan tat check-out kem thanh toan:
    // - Tien mat: nhan vien thu tien tai quay -> ghi nhan PAID luon
    // - Chuyen khoan: phai co xac nhan da nhan tien truoc (QR da hien "thanh cong")
    // - The: chua ho tro
    @Transactional
    public Booking checkOutWithPayment(Long bookingId, PaymentMethod method) {
        if (method == null) {
            throw new BusinessException("Vui lòng chọn phương thức thanh toán");
        }
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ có thể check-out đơn đã check-in (CHECKED_IN)");
        }
        // Da nhan chuyen khoan truoc do thi khong thu them lan nua
        if (!isPaid(bookingId)) {
            switch (method) {
                case CASH -> markPaid(booking, PaymentMethod.CASH);
                case BANK_TRANSFER -> throw new BusinessException(
                        "Chưa nhận được tiền chuyển khoản cho đơn này. Vui lòng chờ khách chuyển khoản thành công");
                default -> throw new BusinessException("Phương thức thanh toán bằng thẻ hiện chưa được hỗ trợ");
            }
        }
        Booking checkedOut = bookingService.checkOut(bookingId);
        // Gui hoa don cho khach qua email (thay cho email bao "da check-out" dang chu thuong)
        findByBookingId(bookingId).ifPresent(payment -> emailService.sendInvoice(checkedOut, payment, findCharges(bookingId)));
        return checkedOut;
    }

    // Nhan vien bam "Gui lai hoa don" - tra ve email da gui toi
    @Transactional(readOnly = true)
    public String resendInvoice(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        Payment payment = findByBookingId(bookingId)
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException("Đơn chưa được thanh toán nên chưa có hóa đơn để gửi"));
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            throw new BusinessException("Đơn này không có email người nhận phòng");
        }
        if (!emailService.sendInvoice(booking, payment, findCharges(bookingId))) {
            throw new BusinessException("Gửi email thất bại. Kiểm tra cấu hình MAIL_USERNAME / MAIL_PASSWORD");
        }
        return booking.getGuestEmail();
    }

    // Du lieu hien thi hoa don - chi xuat duoc khi don da thanh toan
    public Map<String, Object> buildInvoiceData(Booking booking) {
        Payment payment = findByBookingId(booking.getId())
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .orElseThrow(() -> new BusinessException("Đơn chưa được thanh toán nên chưa thể xuất hóa đơn"));
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        Map<String, Object> data = new HashMap<>();
        data.put("booking", booking);
        data.put("payment", payment);
        data.put("nights", nights);
        data.put("roomAmount", booking.getEffectiveRoomAmount());
        // Gia moi dem co the khac nhau (cuoi tuan / ngay le) -> hoa don hien don gia trung binh
        data.put("roomUnitPrice", nights > 0
                ? booking.getEffectiveRoomAmount().divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP)
                : booking.getRoom().getPrice());
        data.put("grandTotal", grandTotal(booking));
        data.put("depositPaid", booking.getDepositPaidAmount());
        data.put("invoiceNo", payment.getInvoiceNo());
        data.put("charges", findCharges(booking.getId()));
        return data;
    }

    // Thong ke tien DA THU theo phuong thuc, loc theo ngay thanh toan trong [fromDate, toDate]
    // Gom ca tien coc (chuyen khoan / VNPay) tinh theo ngay nhan coc - ke ca don sau do bi huy ma resort giu coc,
    // tru di tien coc da hoan lai cho khach (tinh theo ngay hoan)
    public Map<PaymentMethod, BigDecimal> getPaidRevenueByMethod(LocalDate fromDate, LocalDate toDate) {
        Map<PaymentMethod, BigDecimal> result = new LinkedHashMap<>();
        result.put(PaymentMethod.CASH, BigDecimal.ZERO);
        result.put(PaymentMethod.BANK_TRANSFER, BigDecimal.ZERO);
        result.put(PaymentMethod.ONLINE, BigDecimal.ZERO);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay().minusNanos(1);
        for (Payment p : paymentRepository.findByStatusAndPaymentDateBetween(PaymentStatus.PAID, from, to)) {
            result.merge(p.getPaymentMethod(), p.getAmount(), BigDecimal::add);
        }
        // Coc tra qua VNPay tinh vao "online", coc chuyen khoan QR tinh vao "chuyen khoan"
        for (Booking b : bookingRepository.findByDepositPaidAtBetween(from, to)) {
            result.merge(b.isDepositPaidOnline() ? PaymentMethod.ONLINE : PaymentMethod.BANK_TRANSFER,
                    b.getDepositPaidAmount(), BigDecimal::add);
        }
        // Tien coc da chuyen tra lai khach (don huy) - tru theo ngay hoan
        for (Booking b : bookingRepository.findByRefundedAtBetween(from, to)) {
            if (b.getRefundAmount() != null) {
                result.merge(PaymentMethod.BANK_TRANSFER, b.getRefundAmount().negate(), BigDecimal::add);
            }
        }
        return result;
    }

    private Payment markPaid(Booking booking, PaymentMethod method) {
        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseGet(() -> Payment.builder().booking(booking).build());
        payment.setAmount(amountDue(booking));
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentDate(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}
