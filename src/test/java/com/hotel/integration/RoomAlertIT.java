package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.RoomAlertRepository;
import com.hotel.service.RoomAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// "Bao toi khi phong trong lai": dang ky o trang phong da kin -> don huy -> gui email 1 lan
class RoomAlertIT extends ItFixtures {

    @Autowired private RoomAlertRepository alertRepository;
    @Autowired private RoomAlertService roomAlertService;

    private Room room;
    private Booking booking;
    private LocalDate in;
    private LocalDate out;

    @BeforeEach
    void seed() {
        User other = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        room = persistRoom(persistRoomType("Hang Alert IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        booking = persistBooking(other, room, BookingStatus.CONFIRMED, 10, 3);
        in = booking.getCheckInDate();
        out = booking.getCheckOutDate();
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(String email, LocalDate checkIn, LocalDate checkOut) throws Exception {
        return mockMvc.perform(post("/customer/rooms/{id}/alerts", room.getId()).with(csrf())
                .param("email", email).param("checkIn", checkIn.toString()).param("checkOut", checkOut.toString())
                .param("guests", "2"));
    }

    @Test
    void takenDates_showForm_prefilledForLoggedInCustomer() throws Exception {
        User me = persistUser(UserRole.CUSTOMER, CustomerType.REGULAR);
        mockMvc.perform(get("/customer/rooms/{id}", room.getId()).param("checkIn", in.toString())
                        .param("checkOut", out.toString()).with(as(me)))
                .andExpect(content().string(containsString("Báo tôi qua email khi phòng trống lại")))
                .andExpect(content().string(containsString("value=\"" + me.getEmail() + "\"")));

        // Ngay con trong -> khong hien form
        mockMvc.perform(get("/customer/rooms/{id}", room.getId()).param("checkIn", out.plusDays(5).toString())
                        .param("checkOut", out.plusDays(7).toString()))
                .andExpect(content().string(not(containsString("Báo tôi qua email khi phòng trống lại"))));
    }

    @Test
    void subscribe_thenCancelledBooking_notifiesOnce() throws Exception {
        subscribe(" khach.cho@mail.vn ", in, out)
                .andExpect(redirectedUrl("/customer/rooms/" + room.getId() + "?checkIn=" + in + "&checkOut=" + out + "&guests=2#dat-phong"))
                .andExpect(flash().attribute("alertSuccess", containsString("khach.cho@mail.vn")));
        // Bam lai -> khong tao trung
        subscribe("KHACH.CHO@mail.vn", in, out).andExpect(flash().attributeExists("alertSuccess"));
        assertThat(alertRepository.findPending(LocalDate.now())).filteredOn(a -> a.getRoom().getId().equals(room.getId())).hasSize(1);

        // Phong van kin -> chua gui
        assertThat(roomAlertService.notifyAvailable(LocalDate.now())).isZero();

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        assertThat(roomAlertService.notifyAvailable(LocalDate.now())).isEqualTo(1);
        RoomAlert alert = alertRepository.findAll().stream().filter(a -> a.getRoom().getId().equals(room.getId())).findFirst().orElseThrow();
        assertThat(alert.getNotifiedAt()).isNotNull();
        assertThat(alert.getEmail()).isEqualTo("khach.cho@mail.vn");
        assertThat(alert.getGuests()).isEqualTo(2);

        // Lan sau khong gui lai
        assertThat(roomAlertService.notifyAvailable(LocalDate.now())).isZero();
    }

    @Test
    void subscribe_rejectsAvailableDatesBadEmailAndTooMany() throws Exception {
        subscribe("a@b.vn", out.plusDays(3), out.plusDays(5))
                .andExpect(flash().attribute("alertError", containsString("đang trống")));
        subscribe("khong-phai-email", in, out)
                .andExpect(flash().attribute("alertError", containsString("Email không hợp lệ")));
        subscribe("a@b.vn", in, in.plusDays(40))
                .andExpect(flash().attribute("alertError", containsString("tối đa 30 đêm")));

        for (int i = 0; i < RoomAlertService.MAX_PENDING_PER_EMAIL; i++) {
            Room r = persistRoom(room.getRoomType(), 900_000, RoomStatus.MAINTENANCE);
            mockMvc.perform(post("/customer/rooms/{id}/alerts", r.getId()).with(csrf())
                            .param("email", "nhieu@mail.vn").param("checkIn", in.toString()).param("checkOut", out.toString()))
                    .andExpect(flash().attributeExists("alertSuccess"));
        }
        subscribe("nhieu@mail.vn", in, out).andExpect(flash().attribute("alertError", containsString("tối đa 5")));
    }

    @Test
    void maintenanceEnds_triggersAlert_andPastAlertsAreDropped() {
        room.setStatus(RoomStatus.MAINTENANCE);
        roomRepository.save(room);
        LocalDate later = out.plusDays(10);
        alertRepository.save(RoomAlert.builder().room(room).email("baotri@mail.vn").checkIn(later).checkOut(later.plusDays(2)).language("en").build());
        RoomAlert past = alertRepository.save(RoomAlert.builder().room(room).email("cu@mail.vn")
                .checkIn(LocalDate.now().minusDays(1)).checkOut(LocalDate.now().plusDays(1)).build());

        assertThat(roomAlertService.notifyAvailable(LocalDate.now())).isZero();
        assertThat(alertRepository.findById(past.getId())).isEmpty();

        room.setStatus(RoomStatus.AVAILABLE);
        roomRepository.save(room);
        assertThat(roomAlertService.notifyAvailable(LocalDate.now())).isEqualTo(1);
    }

    // Trang le tan: phong duoc san nhieu nhat len dau, email che bot, xoa duoc yeu cau; khach khong vao duoc
    @Test
    void adminPage_showsDemandMaskedEmails_andDelete() throws Exception {
        Room other = persistRoom(room.getRoomType(), 900_000, RoomStatus.MAINTENANCE);
        alertRepository.save(RoomAlert.builder().room(other).email("mot@mail.vn").checkIn(in).checkOut(out).build());
        subscribe("hai@mail.vn", in, out);
        subscribe("ba@mail.vn", in, out);
        User staff = persistUser(UserRole.STAFF, null);

        var result = mockMvc.perform(get("/admin/room-alerts").with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng đang được săn")))
                .andExpect(content().string(containsString("h***@mail.vn")))
                .andExpect(content().string(not(containsString("hai@mail.vn"))))
                .andReturn();
        @SuppressWarnings("unchecked")
        java.util.List<RoomAlertService.Demand> demand =
                (java.util.List<RoomAlertService.Demand>) result.getModelAndView().getModel().get("demand");
        assertThat(demand.get(0).room().getId()).isEqualTo(room.getId());
        assertThat(demand.get(0).requests()).isEqualTo(2);

        RoomAlert first = alertRepository.findPending(LocalDate.now()).stream()
                .filter(a -> a.getEmail().equals("mot@mail.vn")).findFirst().orElseThrow();
        mockMvc.perform(post("/admin/room-alerts/{id}/delete", first.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Đã xóa")));
        assertThat(alertRepository.findById(first.getId())).isEmpty();

        User customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(get("/admin/room-alerts").with(as(customer))).andExpect(status().isForbidden());
    }
}
