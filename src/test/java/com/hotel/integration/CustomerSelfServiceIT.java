package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.GiftVoucherRepository;
import com.hotel.repository.PaymentRepository;
import com.hotel.repository.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach tu phuc vu: lich su diem day du, hoa don da thanh toan, the qua tang da mua / duoc tang
class CustomerSelfServiceIT extends ItFixtures {

    @Autowired private PointTransactionRepository pointRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private GiftVoucherRepository voucherRepository;

    private User customer;
    private Room room;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        customer.setLoyaltyPoints(320);
        userRepository.save(customer);
        room = persistRoom(persistRoomType("Hang TPV IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private GiftVoucher voucher(String purchaserEmail, String recipientEmail, GiftVoucher.Status status, long balance) {
        return voucherRepository.save(GiftVoucher.builder().code("QT-TPV-" + seq()).amount(BigDecimal.valueOf(1_000_000))
                .balance(BigDecimal.valueOf(balance)).status(status).purchaserName("Người Mua").purchaserEmail(purchaserEmail)
                .recipientName("Người Nhận").recipientEmail(recipientEmail).accessToken("tpv" + seq())
                .paidAt(status == GiftVoucher.Status.ACTIVE ? LocalDateTime.now() : null)
                .expiresAt(status == GiftVoucher.Status.ACTIVE ? LocalDateTime.now().plusMonths(6) : null).build());
    }

    @Test
    void pointsPage_showsFullHistoryAndExpectedPoints() throws Exception {
        Booking done = persistBooking(customer, room, BookingStatus.CHECKED_OUT, -10, 2);
        for (int i = 0; i < 12; i++) {
            pointRepository.save(PointTransaction.builder().user(customer).booking(done).type(PointTransaction.Type.EARN)
                    .points(10 + i).balanceAfter(100 + i).build());
        }
        // Ky nghi sap toi 2.000.000 -> du kien 200 diem (hang NEW x1)
        persistBooking(customer, room, BookingStatus.CONFIRMED, 20, 2);
        mockMvc.perform(get("/customer/account/points").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("320")))
                .andExpect(content().string(containsString("khoảng 200 điểm")))
                .andExpect(content().string(containsString("+21")))
                .andExpect(content().string(containsString("+10")));
        mockMvc.perform(get("/customer/account").with(as(customer)))
                .andExpect(content().string(containsString("Xem toàn bộ lịch sử điểm")));
    }

    @Test
    void invoicesPage_listsOnlyPaidBookings_withVatStatus() throws Exception {
        Booking paid = persistBooking(customer, room, BookingStatus.CHECKED_OUT, -5, 2);
        paid.setInvoiceCompanyName("Công ty TNHH Sóng Biển");
        paid.setInvoiceTaxCode("0101234567");
        paid.setInvoiceAddress("Hạ Long");
        bookingRepository.save(paid);
        paymentRepository.save(Payment.builder().booking(paid).amount(BigDecimal.valueOf(2_150_000)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(LocalDateTime.now().minusDays(3)).build());
        Booking unpaid = persistBooking(customer, room, BookingStatus.CONFIRMED, 30, 1);
        Booking stranger = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CHECKED_OUT, -40, 1);
        paymentRepository.save(Payment.builder().booking(stranger).amount(BigDecimal.valueOf(999_000)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(LocalDateTime.now().minusDays(30)).build());

        mockMvc.perform(get("/customer/account/invoices").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2.150.000 VND")))
                .andExpect(content().string(containsString("/customer/bookings/" + paid.getId() + "/invoice.pdf")))
                .andExpect(content().string(containsString("Công ty TNHH Sóng Biển")))
                .andExpect(content().string(containsString("Đang chờ xuất")))
                .andExpect(content().string(not(containsString("/customer/bookings/" + unpaid.getId() + "/invoice.pdf"))))
                .andExpect(content().string(not(containsString("999.000"))));
    }

    @Test
    void giftVouchers_boughtAndReceived_resendOnlyByPurchaser() throws Exception {
        GiftVoucher bought = voucher(customer.getEmail().toUpperCase(), "ban@mail.vn", GiftVoucher.Status.ACTIVE, 600_000);
        GiftVoucher received = voucher("me@mail.vn", customer.getEmail(), GiftVoucher.Status.ACTIVE, 1_000_000);
        GiftVoucher pending = voucher(customer.getEmail(), "ban2@mail.vn", GiftVoucher.Status.PENDING_PAYMENT, 1_000_000);
        GiftVoucher other = voucher("x@mail.vn", "y@mail.vn", GiftVoucher.Status.ACTIVE, 1_000_000);
        Booking used = persistBooking(customer, room, BookingStatus.CONFIRMED, 15, 1);
        used.setGiftVoucherCode(bought.getCode());
        used.setGiftVoucherAmount(BigDecimal.valueOf(400_000));
        bookingRepository.save(used);

        mockMvc.perform(get("/customer/account/gift-vouchers").with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(bought.getCode())))
                .andExpect(content().string(containsString(received.getCode())))
                .andExpect(content().string(containsString("Số dư 600.000 / 1.000.000 VND")))
                .andExpect(content().string(containsString("Người Mua tặng bạn")))
                .andExpect(content().string(containsString("token=" + pending.getAccessToken())))
                .andExpect(content().string(containsString("#" + used.getId())))
                .andExpect(content().string(not(containsString(other.getCode()))));

        // Nguoi nhan khong gui lai duoc; the chua thanh toan khong gui lai duoc
        mockMvc.perform(post("/customer/account/gift-vouchers/{id}/resend", received.getId()).with(csrf()).with(as(customer)))
                .andExpect(flash().attribute("errorMessage", containsString("không đúng")));
        mockMvc.perform(post("/customer/account/gift-vouchers/{id}/resend", pending.getId()).with(csrf()).with(as(customer)))
                .andExpect(flash().attribute("errorMessage", containsString("đã thanh toán")));
    }

    @Test
    void pages_requireCustomerLogin() throws Exception {
        mockMvc.perform(get("/customer/account/gift-vouchers")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/customer/account/invoices").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
    }
}
