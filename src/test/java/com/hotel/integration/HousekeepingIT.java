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

    // Kiem phong truoc khi tra: le tan yeu cau -> buong phong bao do mat / hu hong -> le tan thay tren man tra phong
    @Test
    void roomCheckBeforeCheckout_housekeeperReportsDamage_frontDeskSeesIt() throws Exception {
        User housekeeper = persistUser(UserRole.HOUSEKEEPER, null);
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(staff))
                        .param("assigneeId", housekeeper.getId().toString()))
                .andExpect(redirectedUrl("/admin/bookings/" + leaving.getId() + "/check-out#kiem-phong"));
        Booking b = bookingRepository.findById(leaving.getId()).orElseThrow();
        assertThat(b.isRoomCheckPending()).isTrue();
        assertThat(b.getRoomCheckRequestedBy()).isEqualTo(staff.getFullName());

        // Buong phong thay yeu cau tren bang don phong
        mockMvc.perform(get("/admin/housekeeping").with(as(housekeeper)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kiểm trước khi trả phòng")));

        // Bao "co mat / hong" ma khong ghi ro -> loi
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(housekeeper))
                        .param("issue", "true"))
                .andExpect(flash().attribute("errorMessage", containsString("đồ bị mất")));
        assertThat(bookingRepository.findById(leaving.getId()).orElseThrow().isRoomCheckPending()).isTrue();

        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(housekeeper))
                        .param("issue", "true").param("note", "Mất 1 khăn tắm, vỡ cốc"))
                .andExpect(flash().attribute("successMessage", containsString("Đã báo")));
        b = bookingRepository.findById(leaving.getId()).orElseThrow();
        assertThat(b.isRoomCheckDone()).isTrue();
        assertThat(b.isRoomCheckHasIssue()).isTrue();
        assertThat(b.getRoomCheckedBy()).isEqualTo(housekeeper.getFullName());

        // Le tan mo man tra phong thay ket qua de them phu phi
        mockMvc.perform(get("/admin/bookings/{id}/check-out", leaving.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mất 1 khăn tắm, vỡ cốc")));

        // Bao lai lan nua khi khong con yeu cau -> loi; buong phong khong tu yeu cau kiem duoc
        mockMvc.perform(post("/admin/housekeeping/room-checks/{id}", leaving.getId()).with(csrf()).with(as(housekeeper))
                        .param("issue", "false"))
                .andExpect(flash().attribute("errorMessage", containsString("không có yêu cầu")));
        mockMvc.perform(post("/admin/bookings/{id}/room-check", leaving.getId()).with(csrf()).with(as(housekeeper)))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomCheck_onlyForStayingBooking() throws Exception {
        mockMvc.perform(post("/admin/bookings/{id}/room-check", arriving.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("đang ở")));
        assertThat(bookingRepository.findById(arriving.getId()).orElseThrow().getRoomCheckRequestedAt()).isNull();
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
