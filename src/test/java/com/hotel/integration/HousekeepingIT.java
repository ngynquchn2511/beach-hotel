package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bang don phong: tra phong -> Cho don -> Dang don -> Sach; phong chua sach khong nhan phong duoc
class HousekeepingIT extends ItFixtures {

    private User staff;
    private Room room;
    private Booking leaving;
    private Booking arriving;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        User guestA = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        User guestB = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        room = persistRoom(persistRoomType("Hang Don IT", 800_000, 2), 800_000, RoomStatus.OCCUPIED);
        leaving = persistBooking(guestA, room, BookingStatus.CHECKED_IN, -2, 2);
        declareGuest(leaving);
        arriving = persistBooking(guestB, room, BookingStatus.CONFIRMED, 0, 1);
        declareGuest(arriving);
    }

    private Room reload() {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    @Test
    void checkout_thenCleanFlow_thenNextGuestCanCheckIn() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/check-out", leaving.getId()).with(csrf()).with(as(staff))
                .param("paymentMethod", "CASH"));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.NOT_READY);
        assertThat(reload().getDirtySince()).isNotNull();

        // Khach moi den nhung phong chua sach -> khong cho nhan phong
        mockMvc.perform(post("/admin/bookings/{id}/check-in", arriving.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("chưa dọn xong")));
        assertThat(bookingRepository.findById(arriving.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Bang don phong: phong o "Cho don", duoc danh dau uu tien vi co khach den hom nay
        mockMvc.perform(get("/admin/housekeeping").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng " + room.getRoomNumber())))
                .andExpect(content().string(containsString("Ưu tiên: khách " + arriving.getGuestName() + " đến hôm nay")));

        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/housekeeping"));
        assertThat(reload().isBeingCleaned()).isTrue();
        assertThat(reload().getCleaningBy()).isEqualTo(staff.getFullName());

        // Nhan lai phong dang co nguoi don -> bao loi
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("đang được")));

        mockMvc.perform(post("/admin/housekeeping/{id}/finish", room.getId()).with(csrf()).with(as(staff)));
        Room clean = reload();
        assertThat(clean.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(clean.getLastCleanedBy()).isEqualTo(staff.getFullName());
        assertThat(clean.getCleaningBy()).isNull();
        assertThat(clean.getDirtySince()).isNull();

        mockMvc.perform(get("/admin/housekeeping").with(as(staff)))
                .andExpect(content().string(containsString("Đã sạch hôm nay")))
                .andExpect(content().string(containsString(staff.getFullName() + " · ")));

        mockMvc.perform(post("/admin/bookings/{id}/check-in", arriving.getId()).with(csrf()).with(as(staff)));
        assertThat(bookingRepository.findById(arriving.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
    }

    @Test
    void release_putsRoomBackToWaiting() throws Exception {
        room.setStatus(RoomStatus.NOT_READY);
        roomRepository.save(room);
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(staff)));
        mockMvc.perform(post("/admin/housekeeping/{id}/release", room.getId()).with(csrf()).with(as(staff)));

        assertThat(reload().getStatus()).isEqualTo(RoomStatus.NOT_READY);
        assertThat(reload().isBeingCleaned()).isFalse();
    }

    @Test
    void cleanRoom_cannotBeStarted_andCustomerCannotOpenBoard() throws Exception {
        room.setStatus(RoomStatus.AVAILABLE);
        roomRepository.save(room);
        mockMvc.perform(post("/admin/housekeeping/{id}/start", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("không ở trạng thái chờ dọn")));

        User customer = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        mockMvc.perform(get("/admin/housekeeping").with(as(customer)))
                .andExpect(status().isForbidden());
    }
}
