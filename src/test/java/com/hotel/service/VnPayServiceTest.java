package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Ky / kiem tra chu ky VNPay va tao link thanh toan coc
@ExtendWith(MockitoExtension.class)
class VnPayServiceTest {

    @Mock private BookingService bookingService;

    private VnPayService service() {
        return new VnPayService(bookingService, "TESTCODE", "SECRETKEY123", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    }

    private Booking awaitingBooking(LocalDateTime deadline) {
        return Booking.builder().id(15L).status(BookingStatus.PENDING)
                .depositAmount(BigDecimal.valueOf(330000)).depositDeadline(deadline).build();
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().forEach((k, v) ->
                params.put(k, URLDecoder.decode(v.get(0), StandardCharsets.UTF_8)));
        return params;
    }

    @Test
    void disabledWithoutCredentials() {
        assertThat(new VnPayService(bookingService, "", " ", "x").isEnabled()).isFalse();
        assertThat(service().isEnabled()).isTrue();
    }

    @Test
    void paymentUrl_hasDepositAmountTimesHundred_andValidSignature() {
        VnPayService vnPay = service();
        String url = vnPay.createDepositPaymentUrl(awaitingBooking(LocalDateTime.now().plusHours(5)),
                "1.2.3.4", "http://localhost:8080/payment/vnpay/return", false);

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> p = queryParams(url);
        assertThat(p).containsEntry("vnp_Amount", "33000000").containsEntry("vnp_TmnCode", "TESTCODE")
                .containsEntry("vnp_Locale", "vn").containsEntry("vnp_IpAddr", "1.2.3.4")
                .containsEntry("vnp_ReturnUrl", "http://localhost:8080/payment/vnpay/return");
        assertThat(p.get("vnp_TxnRef")).startsWith("15_");
        assertThat(vnPay.verifySignature(p)).isTrue();
        verify(bookingService).ensureCanPayDepositOnline(any());
    }

    @Test
    void paymentLink_expiresNoLaterThanDepositDeadline() {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(5).withNano(0);
        Map<String, String> p = queryParams(service().createDepositPaymentUrl(awaitingBooking(deadline), "1.1.1.1", "http://x/r", true));

        assertThat(p.get("vnp_ExpireDate")).isEqualTo(deadline.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        assertThat(p).containsEntry("vnp_Locale", "en");
    }

    @Test
    void cannotPay_whenBookingNoLongerAwaitingDeposit() {
        doThrow(new BusinessException("Đơn này không còn chờ đặt cọc")).when(bookingService).ensureCanPayDepositOnline(any());

        assertThatThrownBy(() -> service().createDepositPaymentUrl(awaitingBooking(null), "1.1.1.1", "http://x/r", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void tamperedAmount_failsSignature_andIsNotRecorded() {
        VnPayService vnPay = service();
        Map<String, String> p = new HashMap<>(Map.of("vnp_TxnRef", "15_1", "vnp_Amount", "33000000",
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TmnCode", "TESTCODE", "vnp_TransactionNo", "999"));
        p.put("vnp_SecureHash", vnPay.sign(p));
        p.put("vnp_Amount", "100");

        assertThat(vnPay.handleCallback(p).outcome()).isEqualTo(VnPayService.Outcome.INVALID_SIGNATURE);
        verifyNoInteractions(bookingService);
    }

    @Test
    void successCallback_recordsDepositWithTransactionNo() {
        VnPayService vnPay = service();
        when(bookingService.confirmOnlineDeposit(15L, new BigDecimal("330000"), "14123456"))
                .thenReturn(BookingService.OnlineDepositResult.CONFIRMED);
        Map<String, String> p = new HashMap<>(Map.of("vnp_TxnRef", "15_1760000000", "vnp_Amount", "33000000",
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TmnCode", "TESTCODE",
                "vnp_TransactionNo", "14123456", "vnp_OrderInfo", "Dat coc don HB15"));
        p.put("vnp_SecureHash", vnPay.sign(p));
        p.put("vnp_SecureHashType", "HmacSHA512");

        assertThat(vnPay.handleCallback(p)).isEqualTo(new VnPayService.Result(VnPayService.Outcome.SUCCESS, 15L));
    }

    @Test
    void cancelledPayment_isReportedButNotRecorded() {
        VnPayService vnPay = service();
        Map<String, String> p = new HashMap<>(Map.of("vnp_TxnRef", "15_1", "vnp_Amount", "33000000",
                "vnp_ResponseCode", "24", "vnp_TransactionStatus", "02", "vnp_TmnCode", "TESTCODE"));
        p.put("vnp_SecureHash", vnPay.sign(p));

        assertThat(vnPay.handleCallback(p).outcome()).isEqualTo(VnPayService.Outcome.PAYMENT_FAILED);
        verifyNoInteractions(bookingService);
    }
}
