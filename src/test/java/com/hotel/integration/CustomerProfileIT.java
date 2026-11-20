package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.CustomerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Ho so khach: lich su dat phong day du + thong ke; hang khach tu dong theo so lan luu tru hoan tat
class CustomerProfileIT extends ItFixtures {

    @Autowired CustomerProfileService profileService;

    @Test
    void profileListsAllBookings_andTierFollowsCompletedStays() throws Exception {
        Room room = persistRoom(persistRoomType("Hang Ho So IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.VIP); // VIP chinh tay truoc day, chua o lan nao
        guest.setFullName("Khách Hồ Sơ IT");
        userRepository.save(guest);

        Booking s1 = persistBooking(guest, room, BookingStatus.CHECKED_OUT, -40, 2);
        persistBooking(guest, room, BookingStatus.CHECKED_OUT, -20, 3);
        persistBooking(guest, room, BookingStatus.CANCELLED, -10, 1);
        persistBooking(guest, room, BookingStatus.CONFIRMED, 15, 2);

        // Dong bo: 2 lan o xong -> Khach quen (ha tu VIP chinh tay)
        profileService.syncAllTiers();
        assertThat(userRepository.findById(guest.getId()).orElseThrow().getCustomerType()).isEqualTo(CustomerType.REGULAR);

        CustomerProfileService.Profile p = profileService.profile(guest.getId());
        assertThat(p.totalBookings()).isEqualTo(4);
        assertThat(p.stays()).isEqualTo(2);
        assertThat(p.nights()).isEqualTo(5);
        assertThat(p.cancelled()).isEqualTo(1);
        assertThat(p.upcoming()).isEqualTo(1);
        assertThat(p.totalSpent()).isEqualByComparingTo("5000000");
        assertThat(p.next()).isEqualTo(CustomerType.VIP);
        assertThat(p.staysToNext()).isEqualTo(CustomerType.VIP_MIN_STAYS - 2);

        CustomerProfileService.Row row = profileService.list("hồ sơ it", null).stream()
                .filter(r -> r.customer().getId().equals(guest.getId())).findFirst().orElseThrow();
        assertThat(row.summary().bookings()).isEqualTo(4);
        assertThat(row.summary().stays()).isEqualTo(2);
        assertThat(profileService.list(null, CustomerType.VIP)).noneMatch(r -> r.customer().getId().equals(guest.getId()));

        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(get("/admin/customers").with(as(staff)).param("q", "Hồ Sơ IT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khách Hồ Sơ IT")))
                .andExpect(content().string(containsString("/admin/customers/" + guest.getId())))
                .andExpect(content().string(not(containsString("update-type"))));

        mockMvc.perform(get("/admin/customers/{id}", guest.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch sử đặt phòng")))
                .andExpect(content().string(containsString(s1.getCode())))
                .andExpect(content().string(containsString("Khách quen")))
                .andExpect(content().string(containsString("lần lưu trú nữa để lên")));

        // Khong phai tai khoan khach -> 400/404 (BusinessException)
        mockMvc.perform(get("/admin/customers/{id}", staff.getId()).with(as(staff)))
                .andExpect(content().string(not(containsString("lich-su-dat-phong"))));
        mockMvc.perform(get("/admin/customers/{id}", guest.getId()).with(as(persistUser(UserRole.HOUSEKEEPER, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkoutSetsTierFromCompletedStays() {
        Room room = persistRoom(persistRoomType("Hang Tra Phong IT", 500_000, 2), 500_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        for (int i = 0; i < CustomerType.VIP_MIN_STAYS; i++) {
            persistBooking(guest, room, BookingStatus.CHECKED_OUT, -60 + i * 5, 1);
        }
        assertThat(profileService.syncTier(guest)).isEqualTo(CustomerType.VIP);
        assertThat(userRepository.findById(guest.getId()).orElseThrow().getCustomerType()).isEqualTo(CustomerType.VIP);
    }
}
