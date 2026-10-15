package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.MaintenanceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bao hong phong: khoa phong -> canh bao khach sap den -> sua xong phong sang Cho don
class MaintenanceIT extends ItFixtures {

    @Autowired private MaintenanceTicketRepository ticketRepository;

    private User staff;
    private Room room;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang BT IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
    }

    private Room reload() {
        return roomRepository.findById(room.getId()).orElseThrow();
    }

    private MaintenanceTicket latest() {
        return ticketRepository.findAll().stream().filter(t -> t.getRoom().getId().equals(room.getId()))
                .max(java.util.Comparator.comparing(MaintenanceTicket::getId)).orElseThrow();
    }

    @Test
    void blockingTicket_locksRoom_warnsArrival_resolveSendsToCleaning() throws Exception {
        Booking arriving = persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CONFIRMED, 2, 2);

        mockMvc.perform(post("/admin/maintenance").with(csrf()).with(as(staff))
                        .param("roomId", room.getId().toString()).param("title", "Điều hòa chảy nước")
                        .param("priority", "URGENT").param("blocksRoom", "true"))
                .andExpect(flash().attribute("successMessage", containsString("khóa phòng")));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
        assertThat(latest().getReportedBy()).isEqualTo(staff.getFullName());

        mockMvc.perform(get("/admin/maintenance").with(as(staff)))
                .andExpect(content().string(containsString("Điều hòa chảy nước")))
                .andExpect(content().string(containsString("cần đổi phòng")))
                .andExpect(content().string(containsString("/admin/bookings/" + arriving.getId() + "/move")));

        mockMvc.perform(post("/admin/maintenance/{id}/start", latest().getId()).with(csrf()).with(as(staff)));
        assertThat(latest().getStatus()).isEqualTo(MaintenanceTicket.Status.IN_PROGRESS);

        mockMvc.perform(post("/admin/maintenance/{id}/resolve", latest().getId()).with(csrf()).with(as(staff))
                .param("note", "Thay ống thoát nước"));
        assertThat(latest().getStatus()).isEqualTo(MaintenanceTicket.Status.DONE);
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.NOT_READY);
    }

    @Test
    void roomStaysBlocked_untilLastBlockingTicketDone() throws Exception {
        for (String title : new String[]{"Vòi sen rò", "Bóng đèn hỏng"}) {
            mockMvc.perform(post("/admin/maintenance").with(csrf()).with(as(staff))
                    .param("roomId", room.getId().toString()).param("title", title).param("blocksRoom", "true"));
        }
        var tickets = ticketRepository.findByRoomIdAndStatusNot(room.getId(), MaintenanceTicket.Status.DONE);
        mockMvc.perform(post("/admin/maintenance/{id}/resolve", tickets.get(0).getId()).with(csrf()).with(as(staff)));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
        mockMvc.perform(post("/admin/maintenance/{id}/resolve", tickets.get(1).getId()).with(csrf()).with(as(staff)));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.NOT_READY);
    }

    @Test
    void occupiedRoom_cannotBeLocked_butNonBlockingTicketOk() throws Exception {
        room.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        mockMvc.perform(post("/admin/maintenance").with(csrf()).with(as(staff))
                        .param("roomId", room.getId().toString()).param("title", "Tivi mờ").param("blocksRoom", "true"))
                .andExpect(flash().attribute("errorMessage", containsString("đổi phòng cho khách trước")));
        mockMvc.perform(post("/admin/maintenance").with(csrf()).with(as(staff))
                        .param("roomId", room.getId().toString()).param("title", "Tivi mờ"))
                .andExpect(flash().attribute("successMessage", containsString("Đã ghi phiếu")));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.OCCUPIED);

        // Phieu khong khoa phong: xong cung khong doi trang thai phong
        mockMvc.perform(post("/admin/maintenance/{id}/resolve", latest().getId()).with(csrf()).with(as(staff)));
        assertThat(reload().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
    }
}
