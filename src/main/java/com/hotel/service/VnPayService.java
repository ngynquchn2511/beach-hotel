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
    public record Result(Outcome outcome, Long bookingId) {
    }

    private final BookingService bookingService;
    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;

    public VnPayService(BookingService bookingService,
                        @Value("${app.vnpay.tmn-code:}") String tmnCode,
                        @Value("${app.vnpay.hash-secret:}") String hashSecret,
                        @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl) {
        this.bookingService = bookingService;
        this.tmnCode = tmnCode == null ? "" : tmnCode.trim();
        this.hashSecret = hashSecret == null ? "" : hashSecret.trim();
        this.payUrl = payUrl;
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

    // Xu ly tham so VNPay gui ve (trang tra ve hoac IPN): kiem chu ky -> ghi nhan coc neu giao dich thanh cong
    public Result handleCallback(Map<String, String> params) {
        if (!isEnabled() || !verifySignature(params)) {
            log.warn("VNPay: chu ky khong hop le, txnRef={}", params.get("vnp_TxnRef"));
            return new Result(Outcome.INVALID_SIGNATURE, null);
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
        Outcome outcome = switch (bookingService.confirmOnlineDeposit(bookingId, amount, params.get("vnp_TransactionNo"))) {
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
