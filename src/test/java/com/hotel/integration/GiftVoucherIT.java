package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.GiftVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// The qua tang: mua -> thanh toan (chuyen khoan le tan xac nhan / VNPay) -> dung tru tien dat phong, huy don tra lai so du
@TestPropertySource(properties = {"app.vnpay.tmn-code=ITCODE01", "app.vnpay.hash-secret=IT_SECRET_KEY"})
class GiftVoucherIT extends ItFixtures {

    @Autowired private GiftVoucherRepository voucherRepository;

    private User customer;
    private Room room;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        room = persistRoom(persistRoomType("Hang The IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private GiftVoucher buy(long amount) throws Exception {
        mockMvc.perform(post("/gift-vouchers").with(csrf()).param("amount", String.valueOf(amount))
                        .param("purchaserName", "Mẹ").param("purchaserEmail", "me@mail.vn")
                        .param("recipientName", "Con").param("recipientEmail", "con@mail.vn").param("message", "Chúc mừng sinh nhật"))
                .andExpect(status().is3xxRedirection());
        return voucherRepository.findAll().stream().max(java.util.Comparator.comparing(GiftVoucher::getId)).orElseThrow();
    }

    private GiftVoucher activeVoucher(long amount) throws Exception {
        GiftVoucher v = buy(amount);
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/gift-vouchers/{id}/confirm-transfer", v.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString(v.getCode())));
        return voucherRepository.findById(v.getId()).orElseThrow();
    }

    private ResultActions book(int days, String giftCode) throws Exception {
        LocalDate in = LocalDate.now().plusDays(days);
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(customer))
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", customer.getFullName()).param("guestPhone", customer.getPhoneNumber())
                .param("guestEmail", customer.getEmail()).param("giftCode", giftCode));
    }

    private Booking latestBooking() {
        return bookingRepository.findAll().stream().filter(b -> b.getCustomer().getId().equals(customer.getId()))
                .max(java.util.Comparator.comparing(Booking::getId)).orElseThrow();
    }

    @Test
    void purchase_pendingUntilStaffConfirmsTransfer() throws Exception {
        GiftVoucher v = buy(1_000_000);
        assertThat(v.getStatus()).isEqualTo(GiftVoucher.Status.PENDING_PAYMENT);
        assertThat(v.getCode()).matches("QT-[A-Z2-9]{4}-[A-Z2-9]{4}");

        mockMvc.perform(get("/gift-vouchers/{id}", v.getId()).param("token", v.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QT" + v.getId())))
                .andExpect(content().string(containsString("Thanh toán qua VNPay")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(v.getCode()))));
        mockMvc.perform(get("/gift-vouchers/{id}", v.getId()).param("token", "sai"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("QT" + v.getId() + "<"))));

        GiftVoucher active = activeVoucher(1_000_000);
        assertThat(active.getStatus()).isEqualTo(GiftVoucher.Status.ACTIVE);
        assertThat(active.getExpiresAt()).isAfter(LocalDateTime.now().plusMonths(11));
    }

    @Test
    void redeem_partialAndFull_thenCancelRestoresBalance() throws Exception {
        GiftVoucher v = activeVoucher(5_000_000);

        book(30, v.getCode().toLowerCase()).andExpect(status().is3xxRedirection());
        Booking first = latestBooking();
        assertThat(first.getGiftVoucherAmount()).isEqualByComparingTo("2000000");
        assertThat(first.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(voucherRepository.findById(v.getId()).orElseThrow().getBalance()).isEqualByComparingTo("3000000");

        mockMvc.perform(post("/customer/bookings/{id}/cancel", first.getId()).with(csrf()).with(as(customer)));
        assertThat(voucherRepository.findById(v.getId()).orElseThrow().getBalance()).isEqualByComparingTo("5000000");
    }

    @Test
    void invalidOrUnpaidCode_rejected() throws Exception {
        GiftVoucher unpaid = buy(500_000);
        book(40, unpaid.getCode()).andExpect(model().attribute("errorMessage", containsString("chưa thanh toán")));
        book(40, "QT-SAI-MA00").andExpect(model().attribute("errorMessage", containsString("không đúng")));

        mockMvc.perform(post("/gift-vouchers").with(csrf()).param("amount", "100")
                        .param("purchaserName", "A").param("purchaserEmail", "a@mail.vn")
                        .param("recipientName", "B").param("recipientEmail", "b@mail.vn"))
                .andExpect(flash().attribute("errorMessage", containsString("200.000")));
    }

    @Test
    void vnpayPayment_activatesVoucher() throws Exception {
        GiftVoucher v = buy(2_000_000);
        Map<String, String> p = new TreeMap<>(Map.of("vnp_TmnCode", "ITCODE01", "vnp_TxnRef", "V" + v.getId() + "_1760500000",
                "vnp_Amount", "200000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TransactionNo", "55554444"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("IT_SECRET_KEY".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String data = p.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        var req = get("/payment/vnpay/return");
        p.forEach(req::param);
        req.param("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(req).andExpect(redirectedUrl("/gift-vouchers/" + v.getId() + "/paid"))
                .andExpect(flash().attribute("successMessage", containsString("Thanh toán thành công")));
        GiftVoucher paid = voucherRepository.findById(v.getId()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(GiftVoucher.Status.ACTIVE);
        assertThat(paid.getOnlineRef()).isEqualTo("55554444");
        assertThat(paid.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
    }
}
