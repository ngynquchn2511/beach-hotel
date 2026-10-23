package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingGroupRepository;
import com.hotel.repository.GiftVoucherRepository;
import com.hotel.repository.PricingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Don doan dung ma giam gia, the qua tang, diem thuong va gia thanh vien (moi phong 800k x 2 dem = 1.600.000)
class GroupBookingExtrasIT extends ItFixtures {

    @Autowired private BookingGroupRepository groupRepository;
    @Autowired private PricingSettingsRepository settingsRepository;
    @Autowired private GiftVoucherRepository voucherRepository;

    private Room a;
    private Room b;
    private LocalDate in;
    private LocalDate out;
    // Moi lan tao doan dung khoang ngay rieng (cung 2 phong)
    private int offset;

    @BeforeEach
    void seed() {
        RoomType type = persistRoomType("Hang Doan UD IT", 800_000, 2);
        a = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        b = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        in = LocalDate.now().plusDays(30);
        out = in.plusDays(2);
    }

    private ResultActions create(User who, String... extras) throws Exception {
        MockHttpServletRequestBuilder req = post("/customer/bookings/group/new").with(csrf())
                .param("checkIn", in.plusDays(offset).toString()).param("checkOut", out.plusDays(offset).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Đoàn Ưu Đãi").param("guestPhone", "0912345678").param("guestEmail", "uudai" + seq() + "@mail.vn");
        for (Room r : List.of(a, b)) {
            req.param("roomIds", r.getId().toString()).param("guests_" + r.getId(), "2");
        }
        for (int i = 0; i < extras.length; i += 2) {
            req.param(extras[i], extras[i + 1]);
        }
        req.with(who != null ? as(who) : asNewCustomer());
        offset += 5;
        return mockMvc.perform(req);
    }

    private List<Booking> latestGroupBookings() {
        BookingGroup g = groupRepository.findAll().stream().max(Comparator.comparing(BookingGroup::getId)).orElseThrow();
        return bookingRepository.findByGroupId(g.getId()).stream().sorted(Comparator.comparing(Booking::getId)).toList();
    }

    @Test
    void percentCode_appliesToEveryRoom_fixedCodeOnlyOnce() throws Exception {
        DiscountCode pct = persistCode("DOAN" + seq(), DiscountType.PERCENTAGE, 10, null, true);
        create(null, "discountCode", pct.getCode().toLowerCase()).andExpect(status().is3xxRedirection());
        List<Booking> rooms = latestGroupBookings();
        assertThat(rooms).hasSize(2).allSatisfy(bk -> {
            assertThat(bk.getDiscountAmount()).isEqualByComparingTo("160000");
            assertThat(bk.getTotalAmount()).isEqualByComparingTo("1440000");
        });

        DiscountCode fixed = persistCode("GIAM" + seq(), DiscountType.FIXED_AMOUNT, 300_000, null, true);
        create(null, "discountCode", fixed.getCode()).andExpect(status().is3xxRedirection());
        rooms = latestGroupBookings();
        assertThat(rooms.get(0).getTotalAmount()).isEqualByComparingTo("1300000");
        assertThat(rooms.get(1).getTotalAmount()).isEqualByComparingTo("1600000");

        long before = bookingRepository.count();
        create(null, "discountCode", "KHONGCO").andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("không tồn tại")));
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @Test
    void member_getsMemberPriceAndSplitsPoints_overCapRollsBack() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setMemberDiscountNew(5);
        settingsRepository.save(settings);
        User member = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        member.setLoyaltyPoints(2000);
        userRepository.save(member);

        // Moi phong: 1.600.000 - 5% = 1.520.000 -> toi da 760 diem / phong, ca doan 1.520 diem
        mockMvc.perform(get("/customer/bookings/group/new").with(as(member)).param("checkIn", in.toString())
                        .param("checkOut", out.toString()).param("roomIds", a.getId().toString()).param("roomIds", b.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá thành viên -5%")))
                .andExpect(content().string(containsString("tối đa 1.520 điểm")));

        create(member, "pointsToUse", "1000").andExpect(status().is3xxRedirection());
        List<Booking> rooms = latestGroupBookings();
        assertThat(rooms).allSatisfy(bk -> assertThat(bk.getMemberDiscountAmount()).isEqualByComparingTo("80000"));
        assertThat(rooms.get(0).getPointsUsed()).isEqualTo(760);
        assertThat(rooms.get(1).getPointsUsed()).isEqualTo(240);
        assertThat(rooms.get(0).getTotalAmount()).isEqualByComparingTo("760000");
        assertThat(rooms.get(1).getTotalAmount()).isEqualByComparingTo("1280000");
        assertThat(userRepository.findById(member.getId()).orElseThrow().getPointsBalance()).isEqualTo(1000);
    }

    // Vuot 50% tien moi phong -> bao loi kem so toi da. (Test chay trong 1 giao dich nen khong thay duoc rollback;
    // chay that thi createGroupBooking rollback ca doan: khong don nao, khong tru diem)
    @Test
    void member_pointsOverGroupCap_rejectedWithMax() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setMemberDiscountNew(5);
        settingsRepository.save(settings);
        User member = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        member.setLoyaltyPoints(2000);
        userRepository.save(member);
        create(member, "pointsToUse", "1600").andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tối đa 1.520 điểm")));
        create(member, "pointsToUse", "30").andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", containsString("tối thiểu 50")));
    }

    @Test
    void customerWithoutMemberDiscountOrPoints_giftCardSpreadsAcrossRooms() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setMemberDiscountNew(0);
        settingsRepository.save(settings);
        GiftVoucher v = voucherRepository.save(GiftVoucher.builder().code("QT-DOAN-" + seq()).amount(BigDecimal.valueOf(2_000_000))
                .balance(BigDecimal.valueOf(2_000_000)).status(GiftVoucher.Status.ACTIVE).purchaserName("A").purchaserEmail("a@mail.vn")
                .recipientName("B").recipientEmail("b@mail.vn").accessToken("tok" + seq()).paidAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMonths(6)).build());

        create(null, "giftCode", v.getCode()).andExpect(status().is3xxRedirection());
        List<Booking> rooms = latestGroupBookings();
        assertThat(rooms).allSatisfy(bk -> {
            assertThat(bk.getMemberDiscountAmount()).isNull();
            assertThat(bk.getPointsUsed()).isNull();
        });
        assertThat(rooms.get(0).getGiftVoucherAmount()).isEqualByComparingTo("1600000");
        assertThat(rooms.get(1).getGiftVoucherAmount()).isEqualByComparingTo("400000");
        assertThat(rooms.get(1).getTotalAmount()).isEqualByComparingTo("1200000");
        assertThat(voucherRepository.findById(v.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void nonRefundableGroup_eachRoomDiscounted_prepaidInFull_cancelKeepsAll() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setDepositPercent(30);
        settings.setNonRefundablePercent(10);
        settingsRepository.save(settings);
        mockMvc.perform(get("/customer/bookings/group/new").with(asNewCustomer()).param("checkIn", in.toString()).param("checkOut", out.toString())
                        .param("roomIds", a.getId().toString()).param("roomIds", b.getId().toString()))
                .andExpect(content().string(containsString("Giá không hoàn tiền - giảm thêm 10% (khoảng 320.000 VND)")));

        create(null, "nonRefundable", "true").andExpect(status().is3xxRedirection());
        List<Booking> rooms = latestGroupBookings();
        assertThat(rooms).hasSize(2).allSatisfy(bk -> {
            assertThat(bk.isNonRefundable()).isTrue();
            assertThat(bk.getTotalAmount()).isEqualByComparingTo("1440000");
            assertThat(bk.getDepositAmount()).isEqualByComparingTo("1440000");
        });
    }
}
