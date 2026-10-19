package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.QuickReplyRepository;
import com.hotel.service.QuickReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Mau tra loi nhanh: admin quan ly; le tan thay mau dang dung o trang don, bien da dien theo don; mau tam ngung khong hien
class QuickReplyIT extends ItFixtures {

    @Autowired private QuickReplyRepository repository;
    @Autowired private QuickReplyService service;

    @Test
    void renderReplacesVariables() {
        Room room = persistRoom(persistRoomType("Hang QR IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        Booking b = Booking.builder().id(77L).room(room).guestName("Trần Thu Hà").checkInDate(LocalDate.of(2026, 11, 2))
                .checkOutDate(LocalDate.of(2026, 11, 4)).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).build();
        String out = QuickReplyService.render("Chào {ten_khach}, đơn #{ma_don} phòng {so_phong} nhận {gio_nhan} {ngay_den}, trả {gio_tra} {ngay_di}.", b);
        assertThat(out).isEqualTo("Chào Trần Thu Hà, đơn #77 phòng " + room.getRoomNumber() + " nhận 14:00 02/11/2026, trả 12:00 04/11/2026.");
    }

    @Test
    void adminManages_staffUsesOnBookingPage() throws Exception {
        repository.deleteAll();
        User admin = persistUser(UserRole.ADMIN, null);
        User staff = persistUser(UserRole.STAFF, null);
        mockMvc.perform(post("/admin/quick-replies").with(as(admin)).with(csrf())
                        .param("title", "Phòng sẵn sàng").param("body", "Dạ phòng {so_phong} đã sẵn sàng ạ").param("sortOrder", "1"))
                .andExpect(redirectedUrl("/admin/quick-replies"));
        mockMvc.perform(post("/admin/quick-replies").with(as(admin)).with(csrf()).param("title", "").param("body", "x"))
                .andExpect(flash().attributeExists("errorMessage"));
        QuickReply hidden = service.create("Wifi tạm", "mật khẩu [điền]", 2);
        service.toggle(hidden.getId());
        assertThat(repository.count()).isEqualTo(2);

        Room room = persistRoom(persistRoomType("Hang QR2 IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        Booking b = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CONFIRMED, 3, 2);
        mockMvc.perform(get("/admin/bookings/" + b.getId()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dạ phòng " + room.getRoomNumber() + " đã sẵn sàng ạ")))
                .andExpect(content().string(not(containsString("Wifi tạm"))));

        // Le tan khong duoc sua mau
        mockMvc.perform(get("/admin/quick-replies").with(as(staff))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/quick-replies/" + hidden.getId() + "/delete").with(as(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin/quick-replies"));
        assertThat(repository.count()).isEqualTo(1);
    }
}
