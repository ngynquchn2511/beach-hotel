package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Thanh toan tien coc qua cong VNPay (ban 2.1.0). Khach bam "Thanh toan coc qua VNPay" -> sang trang VNPay tra tien
// -> VNPay goi IPN (server-to-server) va dua khach ve trang tra ve; ca 2 deu kiem tra chu ky roi ghi nhan coc 1 lan.
// Chua cau hinh VNPAY_TMN_CODE / VNPAY_HASH_SECRET thi tinh nang an di, khach chuyen khoan QR nhu cu.
@Service
public class VnPayService {

    private static final Logger log = LoggerFactory.getLogger(VnPayService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // Link thanh toan het han sau 15 phut (hoac som hon neu han chuyen coc den truoc)
    private static final int LINK_MINUTES = 15;

    public enum Outcome { SUCCESS, ALREADY_PAID, PAYMENT_FAILED, INVALID_SIGNATURE, NOT_FOUND, WRONG_AMOUNT, NOT_AWAITING, PAID_BUT_ROOM_TAKEN }

    // Ket qua xu ly 1 lan VNPay bao ve; bookingId = null khi khong doc duoc ma don
    // voucherId != null: giao dich mua the qua tang (vnp_TxnRef bat dau bang "V")
    public record Result(Outcome outcome, Long bookingId, Long voucherId, Long groupId) {
        public Result(Outcome outcome, Long bookingId, Long voucherId) {
            this(outcome, bookingId, voucherId, null);
        }

        public Result(Outcome outcome, Long bookingId) {
            this(outcome, bookingId, null);
        }
    }

    private final BookingService bookingService;
    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private VnPayApiClient apiClient;
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

    public VnPayService(BookingService bookingService,
                        @Value("${app.vnpay.tmn-code:}") String tmnCode,
                        @Value("${app.vnpay.hash-secret:}") String hashSecret,
                        @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl) {
        this.bookingService = bookingService;
        this.tmnCode = tmnCode == null ? "" : tmnCode.trim();
        this.hashSecret = hashSecret == null ? "" : hashSecret.trim();
        this.payUrl = payUrl;
    }

    private GiftVoucherService giftVoucherService;

    @org.springframework.beans.factory.annotation.Autowired
    void setGiftVoucherService(GiftVoucherService giftVoucherService) {
        this.giftVoucherService = giftVoucherService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setApiClient(VnPayApiClient apiClient,
                      @Value("${app.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl) {
        this.apiClient = apiClient;
        this.apiUrl = apiUrl;
    }

    // ===== Hoan coc qua API VNPay (toan bo hoac 1 phan theo chinh sach huy) =====

    // Thu tu truong ky / kiem tra chu ky theo tai lieu API hoan tien (ban 2.1.0), noi bang dau |
    static final String[] REFUND_REQUEST_FIELDS = {"vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
            "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo", "vnp_TransactionDate",
            "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"};
    static final String[] REFUND_RESPONSE_FIELDS = {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"};

    // Goi VNPay hoan tien coc cua don da huy; thanh cong -> ghi nhan da hoan + email khach. Loi -> BusinessException kem ma loi VNPay
    public void refundDeposit(Long bookingId, String staffName, String clientIp) {
        if (!isEnabled()) {
            throw new BusinessException("Chưa cấu hình VNPay (VNPAY_TMN_CODE / VNPAY_HASH_SECRET)");
        }
        com.hotel.entity.Booking booking = bookingService.findById(bookingId);
        if (!booking.isRefundableViaVnpay()) {
            throw new BusinessException("Đơn này không hoàn được qua VNPay (cọc không trả qua VNPay hoặc không còn chờ hoàn) - hãy chuyển khoản tay");
        }
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        java.math.BigDecimal amount = booking.getRefundAmount().setScale(0, RoundingMode.HALF_UP);
        boolean groupTxn = booking.getDepositOnlineTxnRef() != null && booking.getDepositOnlineTxnRef().startsWith("G");
        boolean full = !groupTxn && amount.compareTo(booking.getDepositAmount().setScale(0, RoundingMode.HALF_UP)) >= 0;
        Map<String, String> req = new java.util.LinkedHashMap<>();
        req.put("vnp_RequestId", booking.getId() + "R" + now.toEpochSecond());
        req.put("vnp_Version", "2.1.0");
        req.put("vnp_Command", "refund");
        req.put("vnp_TmnCode", tmnCode);
        req.put("vnp_TransactionType", full ? "02" : "03");
        req.put("vnp_TxnRef", booking.getDepositOnlineTxnRef());
        req.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toPlainString());
        req.put("vnp_TransactionNo", booking.getDepositOnlineRef());
        req.put("vnp_TransactionDate", booking.getDepositOnlinePayDate());
        req.put("vnp_CreateBy", staffName == null || staffName.isBlank() ? "le tan" : staffName);
        req.put("vnp_CreateDate", now.format(VNP_TIME));
        req.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        req.put("vnp_OrderInfo", "Hoan coc don HB" + booking.getId());
        req.put("vnp_SecureHash", hmacSha512(pipeJoin(req, REFUND_REQUEST_FIELDS)));

        Map<String, String> res;
        try {
            res = apiClient.post(apiUrl, req);
        } catch (IllegalStateException ex) {
            log.error("VNPay hoan tien don #{} loi ket noi", bookingId, ex);
            throw new BusinessException("Không kết nối được VNPay, vui lòng thử lại sau");
        }
        String received = res.get("vnp_SecureHash");
        if (received != null && !received.equalsIgnoreCase(hmacSha512(pipeJoin(res, REFUND_RESPONSE_FIELDS)))) {
            log.warn("VNPay hoan tien don #{}: chu ky phan hoi khong hop le", bookingId);
            throw new BusinessException("Phản hồi từ VNPay không hợp lệ (sai chữ ký) - chưa ghi nhận hoàn tiền");
        }
        if (!"00".equals(res.get("vnp_ResponseCode"))) {
            throw new BusinessException("VNPay từ chối hoàn tiền (mã " + res.get("vnp_ResponseCode") + ": "
                    + res.getOrDefault("vnp_Message", "") + ")");
        }
        bookingService.confirmRefundOnline(bookingId, res.get("vnp_TransactionNo"));
    }

    static String pipeJoin(Map<String, String> values, String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            String v = values.get(fields[i]);
            sb.append(v == null ? "" : v);
        }
        return sb.toString();
    }

    // Dung trong test de gia lap VNPay ky phan hoi hoan tien
    String signPipe(Map<String, String> values, String[] fields) {
        return hmacSha512(pipeJoin(values, fields));
    }

    public boolean isEnabled() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
    }

    // Link sang trang thanh toan VNPay cho tien coc cua don
    public String createDepositPaymentUrl(Booking booking, String clientIp, String returnUrl, boolean english) {
        if (!isEnabled()) {
            throw new BusinessException("Thanh toán online chưa được bật");
        }
        bookingService.ensureCanPayDepositOnline(booking);
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        ZonedDateTime expire = now.plusMinutes(LINK_MINUTES);
        if (booking.getDepositDeadline() != null) {
            ZonedDateTime deadline = booking.getDepositDeadline().atZone(VN_ZONE);
            if (deadline.isBefore(expire)) {
                expire = deadline;
            }
        }
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        // So tien gui VNPay = VND x 100
        params.put("vnp_Amount", booking.getDepositAmount().setScale(0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).toPlainString());
        params.put("vnp_CurrCode", "VND");
        // Ma giao dich duy nhat moi lan bam: <ma don>_<thoi diem>, khach bam lai van tao giao dich moi
        params.put("vnp_TxnRef", booking.getId() + "_" + now.toEpochSecond());
        params.put("vnp_OrderInfo", "Dat coc don HB" + booking.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", english ? "en" : "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_TIME));
        params.put("vnp_ExpireDate", expire.format(VNP_TIME));
        String query = canonicalQuery(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    // Coc gop ca doan: 1 giao dich = tong coc cac phong dang cho coc (vnp_TxnRef "G<ma doan>_<thoi diem>")
    public String createGroupDepositPaymentUrl(com.hotel.entity.BookingGroup group, List<Booking> bookings, String clientIp,
                                               String returnUrl, boolean english) {
        if (!isEnabled()) {
            throw new BusinessException("Thanh toán online chưa được bật");
        }
        List<Booking> awaiting = bookings.stream().filter(Booking::isAwaitingDeposit).toList();
        if (awaiting.isEmpty()) {
            throw new BusinessException("Đoàn không còn phòng nào chờ đặt cọc");
        }
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        ZonedDateTime expire = now.plusMinutes(LINK_MINUTES);
        BigDecimal amount = BigDecimal.ZERO;
        for (Booking b : awaiting) {
            bookingService.ensureCanPayDepositOnline(b);
            amount = amount.add(b.getDepositAmount());
            if (b.getDepositDeadline() != null && b.getDepositDeadline().atZone(VN_ZONE).isBefore(expire)) {
                expire = b.getDepositDeadline().atZone(VN_ZONE);
            }
        }
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amount.setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "G" + group.getId() + "_" + now.toEpochSecond());
        params.put("vnp_OrderInfo", "Dat coc doan " + group.getCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", english ? "en" : "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_TIME));
        params.put("vnp_ExpireDate", expire.format(VNP_TIME));
        String query = canonicalQuery(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    // Link thanh toan the qua tang (het han sau 15 phut)
    public String createVoucherPaymentUrl(com.hotel.entity.GiftVoucher voucher, String clientIp, String returnUrl, boolean english) {
        if (!isEnabled()) {
            throw new BusinessException("Thanh toán online chưa được bật");
        }
        if (voucher.getStatus() != com.hotel.entity.GiftVoucher.Status.PENDING_PAYMENT) {
            throw new BusinessException("Thẻ này không ở trạng thái chờ thanh toán");
        }
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", voucher.getAmount().setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "V" + voucher.getId() + "_" + now.toEpochSecond());
        params.put("vnp_OrderInfo", "Mua the qua tang QT" + voucher.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", english ? "en" : "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(VNP_TIME));
        params.put("vnp_ExpireDate", now.plusMinutes(LINK_MINUTES).format(VNP_TIME));
        String query = canonicalQuery(params);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(query);
    }

    // Xu ly tham so VNPay gui ve (trang tra ve hoac IPN): kiem chu ky -> ghi nhan coc neu giao dich thanh cong
    public Result handleCallback(Map<String, String> params) {
        if (!isEnabled() || !verifySignature(params)) {
            log.warn("VNPay: chu ky khong hop le, txnRef={}", params.get("vnp_TxnRef"));
            return new Result(Outcome.INVALID_SIGNATURE, null);
        }
        String txnRef = params.get("vnp_TxnRef");
        if (txnRef != null && txnRef.startsWith("V")) {
            return handleVoucherCallback(params, parseBookingId(txnRef.substring(1)));
        }
        if (txnRef != null && txnRef.startsWith("G")) {
            return handleGroupCallback(params, parseBookingId(txnRef.substring(1)));
        }
        Long bookingId = parseBookingId(params.get("vnp_TxnRef"));
        if (bookingId == null || !tmnCode.equals(params.get("vnp_TmnCode"))) {
            return new Result(Outcome.NOT_FOUND, bookingId);
        }
        boolean paid = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        if (!paid) {
            return new Result(Outcome.PAYMENT_FAILED, bookingId);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        } catch (RuntimeException ex) {
            return new Result(Outcome.WRONG_AMOUNT, bookingId);
        }
        Outcome outcome = switch (bookingService.confirmOnlineDeposit(bookingId, amount, params.get("vnp_TransactionNo"),
                params.get("vnp_TxnRef"), params.get("vnp_PayDate"))) {
            case CONFIRMED -> Outcome.SUCCESS;
            case ALREADY_PAID -> Outcome.ALREADY_PAID;
            case NOT_FOUND -> Outcome.NOT_FOUND;
            case WRONG_AMOUNT -> Outcome.WRONG_AMOUNT;
            case NOT_AWAITING -> Outcome.NOT_AWAITING;
            case PAID_BUT_ROOM_TAKEN -> Outcome.PAID_BUT_ROOM_TAKEN;
        };
        if (outcome == Outcome.NOT_AWAITING || outcome == Outcome.PAID_BUT_ROOM_TAKEN) {
            log.warn("VNPay: khach da tra coc nhung don #{} khong con cho coc ({}), ma GD {}", bookingId, outcome,
                    params.get("vnp_TransactionNo"));
        }
        return new Result(outcome, bookingId);
    }

    private Result handleGroupCallback(Map<String, String> params, Long groupId) {
        if (groupId == null || !tmnCode.equals(params.get("vnp_TmnCode"))) {
            return new Result(Outcome.NOT_FOUND, null, null, groupId);
        }
        if (!"00".equals(params.get("vnp_ResponseCode")) || !"00".equals(params.get("vnp_TransactionStatus"))) {
            return new Result(Outcome.PAYMENT_FAILED, null, null, groupId);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        } catch (RuntimeException ex) {
            return new Result(Outcome.WRONG_AMOUNT, null, null, groupId);
        }
        Outcome outcome = switch (bookingService.confirmOnlineGroupDeposit(groupId, amount, params.get("vnp_TransactionNo"),
                params.get("vnp_TxnRef"), params.get("vnp_PayDate"))) {
            case CONFIRMED -> Outcome.SUCCESS;
            case ALREADY_PAID -> Outcome.ALREADY_PAID;
            case NOT_FOUND -> Outcome.NOT_FOUND;
            case WRONG_AMOUNT -> Outcome.WRONG_AMOUNT;
            case NOT_AWAITING -> Outcome.NOT_AWAITING;
            case PAID_BUT_ROOM_TAKEN -> Outcome.PAID_BUT_ROOM_TAKEN;
        };
        if (outcome == Outcome.NOT_AWAITING || outcome == Outcome.PAID_BUT_ROOM_TAKEN) {
            log.warn("VNPay: doan #{} da tra coc nhung co phong khong con cho coc ({}), ma GD {}", groupId, outcome,
                    params.get("vnp_TransactionNo"));
        }
        return new Result(outcome, null, null, groupId);
    }

    private Result handleVoucherCallback(Map<String, String> params, Long voucherId) {
        if (voucherId == null || giftVoucherService == null || !tmnCode.equals(params.get("vnp_TmnCode"))) {
            return new Result(Outcome.NOT_FOUND, null, voucherId);
        }
        if (!"00".equals(params.get("vnp_ResponseCode")) || !"00".equals(params.get("vnp_TransactionStatus"))) {
            return new Result(Outcome.PAYMENT_FAILED, null, voucherId);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        } catch (RuntimeException ex) {
            return new Result(Outcome.WRONG_AMOUNT, null, voucherId);
        }
        Outcome outcome = switch (giftVoucherService.confirmOnlinePayment(voucherId, amount, params.get("vnp_TransactionNo"))) {
            case PAID -> Outcome.SUCCESS;
            case ALREADY_PAID -> Outcome.ALREADY_PAID;
            case NOT_FOUND -> Outcome.NOT_FOUND;
            case WRONG_AMOUNT -> Outcome.WRONG_AMOUNT;
        };
        return new Result(outcome, null, voucherId);
    }

    // Chu ky = HMAC-SHA512(hashSecret, cac tham so vnp_* tru 2 truong hash, sap xep theo ten, ma hoa URL)
    boolean verifySignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null) {
            return false;
        }
        Map<String, String> fields = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
                    && v != null && !v.isEmpty()) {
                fields.put(k, v);
            }
        });
        String expected = hmacSha512(canonicalQuery(fields));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    // Dung trong test de gia lap VNPay ky tham so gui ve
    String sign(Map<String, String> params) {
        return hmacSha512(canonicalQuery(new TreeMap<>(params)));
    }

    private static String canonicalQuery(Map<String, String> sorted) {
        return sorted.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Khong tao duoc chu ky VNPay", ex);
        }
    }

    private static Long parseBookingId(String txnRef) {
        if (txnRef == null) {
            return null;
        }
        int sep = txnRef.indexOf('_');
        try {
            return Long.valueOf(sep > 0 ? txnRef.substring(0, sep) : txnRef);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
