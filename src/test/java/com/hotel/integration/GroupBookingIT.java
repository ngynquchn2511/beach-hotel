package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingGroupRepository;
import com.hotel.repository.PricingSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dat nhieu phong 1 lan: tao tat ca hoac khong tao gi, coc gop, huy ca doan
class GroupBookingIT extends ItFixtures {

    @Autowired private BookingGroupRepository groupRepository;
    @Autowired private PricingSettingsRepository settingsRepository;

    private Room a;
    private Room b;
    private Room c;
    private LocalDate in;
    private LocalDate out;

    @BeforeEach
    void seed() {
        RoomType type = persistRoomType("Hang Doan IT", 800_000, 2);
        a = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        b = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        c = persistRoom(type, 800_000, RoomStatus.AVAILABLE);
        in = LocalDate.now().plusDays(25);
        out = in.plusDays(2);
    }

    private ResultActions create(Room... rooms) throws Exception {
        MockHttpServletRequestBuilder req = post("/customer/bookings/group/new").with(csrf()).with(asNewCustomer())
                .param("checkIn", in.toString()).param("checkOut", out.toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", "Gia Đình Nguyễn").param("guestPhone", "0912345678").param("guestEmail", "doan@mail.vn");
        for (Room r : rooms) {
            req.param("roomIds", r.getId().toString()).param("guests_" + r.getId(), "2");
        }
        return mockMvc.perform(req);
    }

    private BookingGroup latestGroup() {
        return groupRepository.findAll().stream().max(java.util.Comparator.comparing(BookingGroup::getId)).orElseThrow();
    }

    private List<Booking> groupBookings(BookingGroup g) {
        return bookingRepository.findByGroupId(g.getId());
    }

    @Test
    void guestBooksTwoRooms_withGroupDeposit_staffConfirmsAll() throws Exception {
        PricingSettings settings = PricingSettings.defaults();
        settings.setDepositPercent(30);
        settingsRepository.save(settings);

        mockMvc.perform(get("/customer/bookings/group/new").with(asNewCustomer()).param("checkIn", in.toString()).param("checkOut", out.toString())
                        .param("roomIds", a.getId().toString()).param("roomIds", b.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3.200.000 VND")));

        create(a, b).andExpect(status().is3xxRedirection());
        BookingGroup group = latestGroup();
        List<Booking> bookings = groupBookings(group);
        assertThat(bookings).hasSize(2).allMatch(bk -> bk.getStatus() == BookingStatus.PENDING && bk.isAwaitingDeposit());

        mockMvc.perform(get("/customer/bookings/group/{id}", group.getId()).param("token", group.getAccessToken()).with(as(group.getCustomer())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HB " + group.getCode() + " COC")))
                .andExpect(content().string(containsString("960.000")));

        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/booking-groups/{id}/confirm-deposit", group.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("xác nhận 2 phòng")));
        assertThat(groupBookings(group)).allMatch(bk -> bk.getStatus() == BookingStatus.CONFIRMED && bk.isDepositPaid());
    }

    @Test
    void oneRoomTaken_noBookingCreated() throws Exception {
        persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), b, BookingStatus.CONFIRMED, 25, 1);

        create(a, b).andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));

        // Phong a da duoc tao truoc khi gap phong b bi trung -> ca giao dich bi danh dau huy (chay that: khong luu gi).
        // Test chay trong 1 giao dich chung nen kiem tra co huy, khong dem dong
        assertThat(org.springframework.test.context.transaction.TestTransaction.isFlaggedForRollback()).isTrue();
    }

    @Test
    void roomCountAndGuestLimits() throws Exception {
        create(a).andExpect(model().attribute("errorMessage", containsString("từ 2 đến 5")));
        assertThat(groupRepository.count()).isZero();
        mockMvc.perform(post("/customer/bookings/group/new").with(csrf()).with(asNewCustomer())
                        .param("checkIn", in.toString()).param("checkOut", out.toString())
                        .param("guestName", "X").param("guestPhone", "0912345678").param("guestEmail", "x@mail.vn")
                        .param("roomIds", a.getId().toString()).param("guests_" + a.getId(), "5")
                        .param("roomIds", b.getId().toString()))
                .andExpect(model().attribute("errorMessage", containsString("tối đa 2 khách")));
        assertThat(org.springframework.test.context.transaction.TestTransaction.isFlaggedForRollback()).isTrue();
    }

    @Test
    void customerCancelsWholeGroup_wrongTokenRejected() throws Exception {
        create(a, b, c);
        BookingGroup group = latestGroup();

        mockMvc.perform(get("/customer/bookings/group/{id}", group.getId()).param("token", "sai"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Hủy cả đoàn"))));

        mockMvc.perform(post("/customer/bookings/group/{id}/cancel", group.getId()).with(csrf()).param("token", group.getAccessToken()).with(as(group.getCustomer())))
                .andExpect(flash().attribute("successMessage", containsString("Đã hủy 3 phòng")));
        assertThat(groupBookings(group)).allMatch(bk -> bk.getStatus() == BookingStatus.CANCELLED);
    }

    @Test
    void staffSeesGroupFromBooking_andCancels() throws Exception {
        create(a, b);
        BookingGroup group = latestGroup();
        User staff = persistUser(UserRole.STAFF, null);

        mockMvc.perform(get("/admin/bookings/{id}", groupBookings(group).get(0).getId()).with(as(staff)))
                .andExpect(content().string(containsString("Xem đoàn " + group.getCode())));
        mockMvc.perform(post("/admin/booking-groups/{id}/cancel", group.getId()).with(csrf()).with(as(staff)).param("fullRefund", "true"))
                .andExpect(flash().attribute("successMessage", containsString("Đã hủy 2 phòng")));
        assertThat(groupBookings(group)).allMatch(bk -> bk.getStatus() == BookingStatus.CANCELLED);
    }

    @Test
    void searchResults_offerBookTogether() throws Exception {
        mockMvc.perform(get("/customer/rooms").param("checkIn", in.toString()).param("checkOut", out.toString()))
                .andExpect(content().string(containsString("Đặt các phòng đã chọn")));
    }
}
