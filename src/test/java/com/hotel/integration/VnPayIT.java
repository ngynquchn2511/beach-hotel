package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dat coc online qua VNPay: khach bam nut -> sang VNPay -> VNPay goi IPN / dua khach ve -> don tu xac nhan
@TestPropertySource(properties = {"app.vnpay.tmn-code=ITCODE01", "app.vnpay.hash-secret=IT_SECRET_KEY"})
class VnPayIT extends ItFixtures {

    private static final String SECRET = "IT_SECRET_KEY";

    private User customer;
    private Booking booking;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        Room room = persistRoom(persistRoomType("Hang VNPay IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        booking = persistBooking(customer, room, BookingStatus.PENDING, 10, 2);
        booking.setDepositAmount(BigDecimal.valueOf(600_000));
        booking.setDepositDeadline(LocalDateTime.now().plusHours(12));
        booking = bookingRepository.save(booking);
    }

    // Gia lap VNPay ky ket qua (tinh HMAC doc lap voi code chinh)
    private static String sign(Map<String, String> params) throws Exception {
        String data = new TreeMap<>(params).entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> vnpResult(Long bookingId, long amountVnd, String responseCode) {
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_TmnCode", "ITCODE01");
        p.put("vnp_TxnRef", bookingId + "_1760500000");
        p.put("vnp_Amount", String.valueOf(amountVnd * 100));
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
        p.put("vnp_TransactionNo", "14567890");
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Dat coc don HB" + bookingId);
        p.put("vnp_PayDate", "20261016100000");
        return p;
    }

    private ResultActions call(String path, Map<String, String> params, boolean signed) throws Exception {
        var req = get(path);
        params.forEach(req::param);
        req.param("vnp_SecureHash", signed ? sign(params) : "deadbeef");
        return mockMvc.perform(req);
    }

    @Test
    void bookingPage_showsVnPayButton_andStartRedirectsToSandbox() throws Exception {
        String token = booking.getAccessToken();
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token).with(as(booking.getCustomer())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thanh toán cọc qua VNPay")));

        mockMvc.perform(post("/customer/bookings/{id}/vnpay", booking.getId()).param("token", token).with(as(booking.getCustomer())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")))
                .andExpect(header().string("Location", containsString("vnp_Amount=60000000")))
                .andExpect(header().string("Location", containsString("vnp_SecureHash=")));
    }

    @Test
    void start_withoutLogin_redirectsToLogin_notToVnPay() throws Exception {
        mockMvc.perform(post("/customer/bookings/{id}/vnpay", booking.getId()).param("token", booking.getAccessToken()).with(csrf()))
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void ipnSuccess_confirmsBookingOnce() throws Exception {
        call("/payment/vnpay/ipn", vnpResult(booking.getId(), 600_000, "00"), true)
                .andExpect(jsonPath("$.RspCode").value("00"));

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.isDepositPaidOnline()).isTrue();
        assertThat(saved.getDepositOnlineRef()).isEqualTo("14567890");

        // VNPay goi lai IPN -> bao da xac nhan, khong ghi nhan lan 2
        LocalDateTime paidAt = saved.getDepositPaidAt();
        call("/payment/vnpay/ipn", vnpResult(booking.getId(), 600_000, "00"), true)
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getDepositPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void ipn_rejectsBadSignatureWrongAmountAndUnknownOrder() throws Exception {
        call("/payment/vnpay/ipn", vnpResult(booking.getId(), 600_000, "00"), false)
                .andExpect(jsonPath("$.RspCode").value("97"));
        call("/payment/vnpay/ipn", vnpResult(booking.getId(), 1_000, "00"), true)
                .andExpect(jsonPath("$.RspCode").value("04"));
        call("/payment/vnpay/ipn", vnpResult(987654321L, 600_000, "00"), true)
                .andExpect(jsonPath("$.RspCode").value("01"));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void returnUrl_success_backToBookingPageWithMessage() throws Exception {
        call("/payment/vnpay/return", vnpResult(booking.getId(), 600_000, "00"), true)
                .andExpect(redirectedUrl("/customer/bookings/" + booking.getId() + "?token=" + booking.getAccessToken()))
                .andExpect(flash().attribute("successMessage", containsString("Đã nhận tiền cọc qua VNPay")));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void returnUrl_cancelledPayment_bookingStillAwaitingDeposit() throws Exception {
        call("/payment/vnpay/return", vnpResult(booking.getId(), 600_000, "24"), true)
                .andExpect(flash().attribute("errorMessage", containsString("chưa thành công")));

        Booking saved = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(saved.isAwaitingDeposit()).isTrue();
    }

    @Test
    void staffSeesVnPayTransactionOnBookingDetail() throws Exception {
        call("/payment/vnpay/ipn", vnpResult(booking.getId(), 600_000, "00"), true);
        User staff = persistUser(UserRole.STAFF, null);

        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VNPay, mã GD 14567890")));
    }
}
