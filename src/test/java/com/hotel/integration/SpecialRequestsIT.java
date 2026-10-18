package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Yeu cau dac biet: khach chon khi dat / sua tren trang don, le tan thay o trang don / Hom nay / bang don phong;
// ghi chu so thich luu vao ho so khach, hien lai o don sau
class SpecialRequestsIT extends ItFixtures {

    @Autowired private StaffNotificationService notificationService;

    private User guest;
    private User staff;
    private Room room;

    @BeforeEach
    void seed() {
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang YC IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    @Test
    void guestPicksRequestsWhenBooking_thenEdits_staffNotified() throws Exception {
        LocalDate in = LocalDate.now().plusDays(15);
        mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(guest))
                        .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                        .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khách YC").param("guestPhone", "0909001122").param("guestEmail", guest.getEmail())
                        .param("requestTags", "CELEBRATION").param("requestTags", "EXTRA_BED")
                        .param("specialRequests", "Kỷ niệm 10 năm ngày cưới"))
                .andExpect(status().is3xxRedirection());
        Booking b = bookingRepository.findAll().stream().max(Comparator.comparing(Booking::getId)).orElseThrow();
        assertThat(b.getRequestTagList()).containsExactlyInAnyOrder(GuestRequestTag.CELEBRATION, GuestRequestTag.EXTRA_BED);
        assertThat(b.getSpecialRequests()).isEqualTo("Kỷ niệm 10 năm ngày cưới");

        long start = notificationService.lastId();
        mockMvc.perform(post("/customer/bookings/{id}/requests", b.getId()).with(csrf()).with(as(guest))
                        .param("requestTags", "HIGH_FLOOR").param("specialRequests", "Đến khoảng 22h"))
                .andExpect(flash().attribute("requestsSuccess", containsString("Đã lưu")));
        Booking edited = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(edited.getRequestTagList()).containsExactly(GuestRequestTag.HIGH_FLOOR);
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(notificationService.recent(start)).anySatisfy(n -> {
            assertThat(n.title()).contains("yêu cầu đặc biệt");
            assertThat(n.message()).contains("Tầng cao").contains("22h");
        });

        mockMvc.perform(post("/customer/bookings/{id}/requests", b.getId()).with(csrf()).with(as(guest))
                        .param("specialRequests", "x".repeat(501)))
                .andExpect(flash().attribute("requestsError", containsString("500")));
    }

    @Test
    void staffSeesRequestsAndProfileNotes_onTodayAndHousekeeping_closedAfterCheckIn() throws Exception {
        Booking today = persistBooking(guest, room, BookingStatus.CONFIRMED, 0, 2);
        today.setRequestTags("BABY_COT,QUIET_ROOM");
        today.setSpecialRequests("Bé 1 tuổi");
        bookingRepository.save(today);
        mockMvc.perform(post("/admin/bookings/{id}/guest-notes", today.getId()).with(csrf()).with(as(staff))
                        .param("staffNotes", "Thích phòng hướng biển, không ăn cay"))
                .andExpect(flash().attribute("successMessage", containsString("ghi chú về khách")));
        assertThat(userRepository.findById(guest.getId()).orElseThrow().getStaffNotes()).isEqualTo("Thích phòng hướng biển, không ăn cay");

        mockMvc.perform(get("/admin/today").with(as(staff)))
                .andExpect(content().string(containsString("Cũi em bé")))
                .andExpect(content().string(containsString("Bé 1 tuổi")))
                .andExpect(content().string(containsString("không ăn cay")));
        // Phong chua don + khach den hom nay -> bang don phong hien yeu cau can chuan bi
        room.setStatus(RoomStatus.NOT_READY);
        roomRepository.save(room);
        mockMvc.perform(get("/admin/housekeeping").with(as(persistUser(UserRole.HOUSEKEEPER, null))))
                .andExpect(content().string(containsString("Cũi em bé")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Phòng yên tĩnh"))));

        // Don sau cua cung khach: le tan thay ghi chu ho so
        Booking next = persistBooking(guest, persistRoom(room.getRoomType(), 1_000_000, RoomStatus.AVAILABLE), BookingStatus.PENDING, 40, 1);
        mockMvc.perform(get("/admin/bookings/{id}", next.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Thích phòng hướng biển")));

        // Da nhan phong: khach khong sua duoc, le tan van sua duoc
        today.setStatus(BookingStatus.CHECKED_IN);
        bookingRepository.save(today);
        mockMvc.perform(post("/customer/bookings/{id}/requests", today.getId()).with(csrf()).with(as(guest)).param("specialRequests", "Thêm gối"))
                .andExpect(flash().attribute("requestsError", containsString("lễ tân")));
        mockMvc.perform(post("/admin/bookings/{id}/requests", today.getId()).with(csrf()).with(as(staff)).param("specialRequests", "Thêm gối"))
                .andExpect(flash().attribute("successMessage", containsString("Đã lưu")));
        assertThat(bookingRepository.findById(today.getId()).orElseThrow().getSpecialRequests()).isEqualTo("Thêm gối");
    }
}
