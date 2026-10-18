package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.UpgradeOfferRepository;
import com.hotel.service.UpgradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Ban nang hang phong: goi y loai cao hon con trong, khach dong y -> doi phong + cong tien (tru uu dai), tu gui truoc 2 ngay
class UpgradeOfferIT extends ItFixtures {

    @Autowired private UpgradeOfferRepository offerRepository;
    @Autowired private UpgradeService upgradeService;
    @Autowired private PricingSettingsRepository settingsRepository;

    private User guest;
    private User staff;
    private Room standard;
    private Room deluxe;
    private Room suite;
    private Booking booking;

    @BeforeEach
    void seed() {
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, null);
        standard = persistRoom(persistRoomType("Standard NH IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        deluxe = persistRoom(persistRoomType("Deluxe NH IT", 1_500_000, 2), 1_500_000, RoomStatus.AVAILABLE);
        suite = persistRoom(persistRoomType("Suite NH IT", 2_500_000, 2), 2_500_000, RoomStatus.AVAILABLE);
        // Phong re hon (khong phai nang hang)
        persistRoom(persistRoomType("Mini NH IT", 600_000, 2), 600_000, RoomStatus.AVAILABLE);
        booking = persistBooking(guest, standard, BookingStatus.CONFIRMED, 2, 2);
        booking.setRoomAmount(BigDecimal.valueOf(2_000_000));
        bookingRepository.save(booking);
        PricingSettings s = PricingSettings.defaults();
        s.setUpgradeDiscountPercent(20);
        settingsRepository.save(s);
    }

    @Test
    void staffSendsOffers_guestAccepts_roomMovedAndPriceDiscounted() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/upgrade-offers", booking.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("lời mời nâng hạng")));
        List<UpgradeOffer> offers = offerRepository.findByBooking(booking.getId());
        assertThat(offers).extracting(o -> o.getRoom().getRoomType().getName())
                .containsExactlyInAnyOrder("Deluxe NH IT", "Suite NH IT");
        UpgradeOffer toDeluxe = offers.stream().filter(o -> o.getRoom().getId().equals(deluxe.getId())).findFirst().orElseThrow();
        // Chenh lech 2 dem: 3.000.000 - 2.000.000 = 1.000.000, uu dai 20% -> 800.000
        assertThat(toDeluxe.getFullDifference()).isEqualByComparingTo("1000000");
        assertThat(toDeluxe.getPrice()).isEqualByComparingTo("800000");

        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(as(guest)))
                .andExpect(content().string(containsString("Nhận nâng hạng")))
                .andExpect(content().string(containsString("Thêm 800.000 VND cho cả kỳ nghỉ")));

        BigDecimal totalBefore = booking.getTotalAmount();
        mockMvc.perform(post("/customer/bookings/{id}/upgrade/{oid}/accept", booking.getId(), toDeluxe.getId()).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("upgradeSuccess", containsString(deluxe.getRoomNumber())));
        Booking upgraded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(upgraded.getRoom().getId()).isEqualTo(deluxe.getId());
        assertThat(upgraded.getTotalAmount()).isEqualByComparingTo(totalBefore.add(BigDecimal.valueOf(800_000)));
        assertThat(upgraded.getRoomAmount()).isEqualByComparingTo("2800000");
        assertThat(offerRepository.findByBooking(booking.getId())).extracting(UpgradeOffer::getStatus)
                .containsExactlyInAnyOrder(UpgradeOffer.Status.ACCEPTED, UpgradeOffer.Status.EXPIRED);

        // Loi moi da dong khong dung lai duoc
        mockMvc.perform(post("/customer/bookings/{id}/upgrade/{oid}/accept", booking.getId(), toDeluxe.getId()).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("upgradeError", containsString("không còn hiệu lực")));
        mockMvc.perform(get("/admin/upgrades").with(as(staff)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("800.000")));
    }

    @Test
    void roomTakenMeanwhile_offerExpires_guestKeepsRoom() throws Exception {
        List<UpgradeOffer> offers = upgradeService.createOffers(booking.getId(), "test");
        UpgradeOffer toSuite = offers.stream().filter(o -> o.getRoom().getId().equals(suite.getId())).findFirst().orElseThrow();
        // Khach khac dat mat phong suite cung ngay
        persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), suite, BookingStatus.CONFIRMED, 2, 2);
        mockMvc.perform(post("/customer/bookings/{id}/upgrade/{oid}/accept", booking.getId(), toSuite.getId()).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("upgradeError", containsString("vừa có khách khác đặt")));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getRoom().getId()).isEqualTo(standard.getId());

        // Tu choi: moi loi moi con mo dong lai
        UpgradeOffer other = offers.stream().filter(o -> o.getRoom().getId().equals(deluxe.getId())).findFirst().orElseThrow();
        mockMvc.perform(post("/customer/bookings/{id}/upgrade/{oid}/decline", booking.getId(), other.getId()).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("upgradeSuccess", containsString("giữ phòng hiện tại")));
        assertThat(upgradeService.openOffersOf(booking.getId())).isEmpty();
        // Nguoi la khong dung duoc loi moi cua don khac
        mockMvc.perform(post("/customer/bookings/{id}/upgrade/{oid}/accept", booking.getId(), other.getId()).with(csrf())
                .with(as(persistUser(UserRole.CUSTOMER, CustomerType.NEW))));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getRoom().getId()).isEqualTo(standard.getId());
    }

    @Test
    void autoOffers_onlyWhenEnabled_onceTwoDaysBefore_adminOnlySettings() throws Exception {
        assertThat(upgradeService.sendAutoOffers(LocalDate.now())).isZero();
        mockMvc.perform(post("/admin/upgrades/settings").with(csrf()).with(as(staff)).param("auto", "true")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/upgrades/settings").with(csrf()).with(as(persistUser(UserRole.ADMIN, null)))
                        .param("discountPercent", "20").param("auto", "true"))
                .andExpect(flash().attribute("successMessage", containsString("Đã lưu")));
        assertThat(upgradeService.sendAutoOffers(LocalDate.now())).isEqualTo(1);
        assertThat(offerRepository.findByBooking(booking.getId())).hasSize(2).allMatch(o -> "Tự động".equals(o.getCreatedBy()));
        // Chay lai cung ngay: khong gui trung
        assertThat(upgradeService.sendAutoOffers(LocalDate.now())).isZero();
    }
}
