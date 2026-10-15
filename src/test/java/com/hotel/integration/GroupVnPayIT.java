package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingGroupRepository;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.service.VnPayApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
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

// Coc gop ca doan qua VNPay: 1 giao dich = tong coc, xac nhan tat ca phong; hoan 1 phong = hoan mot phan giao dich
@TestPropertySource(properties = {"app.vnpay.tmn-code=ITCODE01", "app.vnpay.hash-secret=IT_SECRET_KEY"})
class GroupVnPayIT extends ItFixtures {

    @Autowired private BookingGroupRepository groupRepository;
    @Autowired private PricingSettingsRepository settingsRepository;
    @MockBean private VnPayApiClient apiClient;

    private BookingGroup group;

    @BeforeEach
    void seed() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setDepositPercent(30);
        settingsRepository.save(settings);
        RoomType type = persistRoomType("Hang Doan VNPay IT", 800_000, 2);
        Room a = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        Room b = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        LocalDate in = LocalDate.now().plusDays(25);
        MockHttpServletRequestBuilder req = post("/customer/bookings/group/new").with(csrf())
                .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Gia Đình Trần").param("guestPhone", "0912345678").param("guestEmail", "doan@mail.vn");
        for (Room r : List.of(a, b)) {
            req.param("roomIds", r.getId().toString()).param("guests_" + r.getId(), "2");
        }
        mockMvc.perform(req).andExpect(status().is3xxRedirection());
        group = groupRepository.findAll().stream().max(java.util.Comparator.comparing(BookingGroup::getId)).orElseThrow();
    }

    private List<Booking> bookings() {
        return bookingRepository.findByGroupId(group.getId());
    }

    private ResultActions callback(String path, String amountVnd) throws Exception {
        Map<String, String> p = new TreeMap<>(Map.of("vnp_TmnCode", "ITCODE01", "vnp_TxnRef", "G" + group.getId() + "_1760500000",
                "vnp_Amount", amountVnd + "00", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "77778888", "vnp_PayDate", "20261016100000"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("IT_SECRET_KEY".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String data = p.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        var req = get(path);
        p.forEach(req::param);
        req.param("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))));
        return mockMvc.perform(req);
    }

    @Test
    void payWholeGroupDeposit_confirmsAllRooms() throws Exception {
        mockMvc.perform(get("/customer/bookings/group/{id}", group.getId()).param("token", group.getAccessToken()))
                .andExpect(content().string(containsString("Thanh toán cọc cả đoàn qua VNPay")));
        mockMvc.perform(post("/customer/bookings/group/{id}/vnpay", group.getId()).with(csrf()).param("token", group.getAccessToken()))
                .andExpect(redirectedUrlPattern("https://**"))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("vnp_Amount=96000000").contains("vnp_TxnRef=G" + group.getId() + "_"));

        // Sai so tien -> khong xac nhan
        callback("/payment/vnpay/return", "480000")
                .andExpect(flash().attribute("errorMessage", containsString("Không xác thực")));
        assertThat(bookings()).allMatch(Booking::isAwaitingDeposit);

        callback("/payment/vnpay/return", "960000")
                .andExpect(redirectedUrl("/customer/bookings/group/" + group.getId() + "?token=" + group.getAccessToken()))
                .andExpect(flash().attribute("successMessage", containsString("cả đoàn")));
        assertThat(bookings()).hasSize(2).allMatch(b -> b.getStatus() == BookingStatus.CONFIRMED
                && "77778888".equals(b.getDepositOnlineRef()) && b.getDepositOnlineTxnRef().startsWith("G"));

        // IPN den sau: da ghi nhan roi
        callback("/payment/vnpay/ipn", "960000").andExpect(jsonPath("$.RspCode").value("02"));
    }

    @Test
    void refundOneRoom_isPartialRefundOfGroupTransaction() throws Exception {
        callback("/payment/vnpay/ipn", "960000").andExpect(jsonPath("$.RspCode").value("00"));
        Booking one = bookings().get(0);
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/bookings/{id}/cancel", one.getId()).with(csrf()).with(as(staff)).param("fullRefund", "true"));
        when(apiClient.post(anyString(), any())).thenReturn(Map.of("vnp_ResponseCode", "94", "vnp_Message", "test"));
        mockMvc.perform(post("/admin/bookings/{id}/refund-vnpay", one.getId()).with(csrf()).with(as(staff)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).post(anyString(), sent.capture());
        assertThat(sent.getValue()).containsEntry("vnp_TransactionType", "03").containsEntry("vnp_Amount", "48000000")
                .containsEntry("vnp_TxnRef", "G" + group.getId() + "_1760500000");
    }

    @Test
    void otherPeopleCannotStartGroupPayment() throws Exception {
        mockMvc.perform(post("/customer/bookings/group/{id}/vnpay", group.getId()).with(csrf()).param("token", "sai"))
                .andExpect(result -> assertThat(String.valueOf(result.getResponse().getRedirectedUrl())).doesNotContain("vnp_Amount"));
        assertThat(bookings()).allMatch(Booking::isAwaitingDeposit);
    }
}
