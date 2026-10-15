package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.VnPayApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Hoan coc qua API VNPay: coc tra qua VNPay -> huy don -> le tan bam "Hoan qua VNPay" (VNPay gia lap bang mock)
@TestPropertySource(properties = {"app.vnpay.tmn-code=ITCODE01", "app.vnpay.hash-secret=IT_SECRET_KEY"})
class VnPayRefundIT extends ItFixtures {

    private static final String SECRET = "IT_SECRET_KEY";
    private static final String[] REQUEST_FIELDS = {"vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
            "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo", "vnp_TransactionDate",
            "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo"};
    private static final String[] RESPONSE_FIELDS = {"vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
            "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"};

    @MockBean private VnPayApiClient apiClient;

    private User staff;
    private Booking booking;

    @BeforeEach
    void seed() throws Exception {
        staff = persistUser(UserRole.STAFF, null);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Hang Hoan IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        booking = persistBooking(guest, room, BookingStatus.PENDING, 20, 2);
        booking.setDepositAmount(BigDecimal.valueOf(600_000));
        booking.setDepositDeadline(LocalDateTime.now().plusHours(12));
        booking = bookingRepository.save(booking);

        // Khach coc qua VNPay (IPN)
        Map<String, String> ipn = new TreeMap<>(Map.of("vnp_TmnCode", "ITCODE01", "vnp_TxnRef", booking.getId() + "_1760500000",
                "vnp_Amount", "60000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "14567890", "vnp_PayDate", "20261016100000"));
        var req = get("/payment/vnpay/ipn");
        ipn.forEach(req::param);
        req.param("vnp_SecureHash", hmac(ipn.entrySet().stream()
                .map(e -> e.getKey() + "=" + java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"))));
        mockMvc.perform(req).andExpect(jsonPath("$.RspCode").value("00"));
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String pipe(Map<String, String> values, String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            sb.append(i > 0 ? "|" : "").append(values.getOrDefault(fields[i], ""));
        }
        return sb.toString();
    }

    private Map<String, String> vnpayReply(String code, boolean signed) throws Exception {
        Map<String, String> res = new HashMap<>(Map.of("vnp_ResponseId", "R1", "vnp_Command", "refund",
                "vnp_ResponseCode", code, "vnp_Message", "00".equals(code) ? "Success" : "Giao dich bi tu choi",
                "vnp_TmnCode", "ITCODE01", "vnp_TxnRef", booking.getId() + "_1760500000", "vnp_Amount", "60000000",
                "vnp_TransactionNo", "99887766", "vnp_TransactionType", "02", "vnp_TransactionStatus", "05"));
        res.put("vnp_SecureHash", signed ? hmac(pipe(res, RESPONSE_FIELDS)) : "sai-chu-ky");
        return res;
    }

    private void cancelFullRefund() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/cancel", booking.getId()).with(csrf()).with(as(staff)).param("fullRefund", "true"));
        Booking cancelled = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(cancelled.isRefundableViaVnpay()).isTrue();
        assertThat(cancelled.getDepositOnlinePayDate()).isEqualTo("20261016100000");
    }

    @Test
    void refundViaVnpay_success_marksRefundedWithTransactionNo() throws Exception {
        cancelFullRefund();
        when(apiClient.post(anyString(), any())).thenReturn(vnpayReply("00", true));

        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Hoàn qua VNPay")));
        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("VNPay đã hoàn tiền")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).post(contains("merchant_webapi"), sent.capture());
        Map<String, String> request = sent.getValue();
        assertThat(request).containsEntry("vnp_Command", "refund").containsEntry("vnp_TransactionType", "02")
                .containsEntry("vnp_Amount", "60000000").containsEntry("vnp_TxnRef", booking.getId() + "_1760500000")
                .containsEntry("vnp_TransactionNo", "14567890").containsEntry("vnp_TransactionDate", "20261016100000")
                .containsEntry("vnp_CreateBy", staff.getFullName());
        assertThat(request.get("vnp_SecureHash")).isEqualTo(hmac(pipe(request, REQUEST_FIELDS)));

        Booking refunded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(refunded.isRefunded()).isTrue();
        assertThat(refunded.getRefundOnlineRef()).isEqualTo("99887766");
        assertThat(refunded.getRefundNote()).contains("VNPay");
    }

    @Test
    void vnpayRejects_orBadSignature_notMarkedRefunded() throws Exception {
        cancelFullRefund();
        when(apiClient.post(anyString(), any())).thenReturn(vnpayReply("94", true), vnpayReply("00", false));

        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("mã 94")));
        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("sai chữ ký")));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().isRefunded()).isFalse();
    }

    @Test
    void partialRefund_usesTypeThree() throws Exception {
        // Huy theo chinh sach (khong hoan 100%) -> neu co tien hoan thi la hoan 1 phan
        Booking b = bookingRepository.findById(booking.getId()).orElseThrow();
        b.setStatus(BookingStatus.CANCELLED);
        b.setRefundPercent(50);
        b.setRefundAmount(BigDecimal.valueOf(300_000));
        bookingRepository.save(b);
        when(apiClient.post(anyString(), any())).thenReturn(vnpayReply("00", true));

        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", booking.getId()).with(csrf()).with(as(staff)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).post(anyString(), sent.capture());
        assertThat(sent.getValue()).containsEntry("vnp_TransactionType", "03").containsEntry("vnp_Amount", "30000000");
    }

    @Test
    void manualTransferDeposit_cannotUseVnpayRefund() throws Exception {
        Booking b = bookingRepository.findById(booking.getId()).orElseThrow();
        b.setDepositOnlineRef(null);
        bookingRepository.save(b);
        mockMvc.perform(post("/admin/bookings/{id}/cancel", booking.getId()).with(csrf()).with(as(staff)).param("fullRefund", "true"));

        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("chuyển khoản tay")));
        verifyNoInteractions(apiClient);
    }

    private static String contains(String part) {
        return org.mockito.ArgumentMatchers.contains(part);
    }
}
