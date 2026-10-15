package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.LostItemRepository;
import com.hotel.service.EmailService;
import com.hotel.service.LostItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// So do that lac: tu gan khach vua tra phong, bao khach qua email, tra khach / thanh ly
class LostItemIT extends ItFixtures {

    @MockBean private EmailService emailService;
    @Autowired private LostItemRepository itemRepository;

    private User staff;
    private Room room;
    private Booking lastStay;

    @BeforeEach
    void seed() {
        when(emailService.sendLostItemFound(any())).thenReturn(true);
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang Do IT", 900_000, 2), 900_000, RoomStatus.NOT_READY);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        persistBooking(guest, room, BookingStatus.CHECKED_OUT, -20, 2);
        lastStay = persistBooking(guest, room, BookingStatus.CHECKED_OUT, -3, 3);
    }

    private LostItem latest() {
        return itemRepository.findAll().stream().max(java.util.Comparator.comparing(LostItem::getId)).orElseThrow();
    }

    @Test
    void recordByRoom_linksLatestGuest_notify_thenReturned() throws Exception {
        mockMvc.perform(post("/admin/lost-found").with(csrf()).with(as(staff))
                        .param("roomId", room.getId().toString()).param("description", "Sạc điện thoại trắng")
                        .param("storageLocation", "Tủ lễ tân"))
                .andExpect(flash().attribute("successMessage", containsString("#" + lastStay.getId())));
        LostItem item = latest();
        assertThat(item.getBooking().getId()).isEqualTo(lastStay.getId());
        assertThat(item.getFoundBy()).isEqualTo(staff.getFullName());

        mockMvc.perform(post("/admin/lost-found/{id}/notify", item.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Đã gửi email")));
        assertThat(latest().getGuestNotifiedAt()).isNotNull();

        mockMvc.perform(post("/admin/lost-found/{id}/close", item.getId()).with(csrf()).with(as(staff))
                .param("status", "RETURNED").param("note", "Khách lấy tại quầy"));
        assertThat(latest().getStatus()).isEqualTo(LostItem.Status.RETURNED);

        // Da xu ly -> khong xu ly lai
        mockMvc.perform(post("/admin/lost-found/{id}/close", item.getId()).with(csrf()).with(as(staff)).param("status", "DISPOSED"))
                .andExpect(flash().attribute("errorMessage", containsString("đã được xử lý")));
    }

    @Test
    void noRecentGuest_cannotNotify_overdueFlagged() throws Exception {
        Room empty = persistRoom(room.getRoomType(), 900_000, RoomStatus.AVAILABLE);
        mockMvc.perform(post("/admin/lost-found").with(csrf()).with(as(staff))
                        .param("roomId", empty.getId().toString()).param("description", "Ô màu xanh"))
                .andExpect(flash().attribute("successMessage", containsString("chưa gắn")));
        LostItem item = latest();
        mockMvc.perform(post("/admin/lost-found/{id}/notify", item.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("chưa gắn với đơn")));

        item.setFoundAt(LocalDateTime.now().minusDays(LostItemService.KEEP_DAYS + 1));
        itemRepository.save(item);
        mockMvc.perform(get("/admin/lost-found").with(as(staff)))
                .andExpect(content().string(containsString("Quá hạn cất giữ")));
        verify(emailService, never()).sendLostItemFound(any());
    }

    @Test
    void emptyDescription_rejected_customerForbidden() throws Exception {
        mockMvc.perform(post("/admin/lost-found").with(csrf()).with(as(staff)).param("description", " "))
                .andExpect(flash().attribute("errorMessage", containsString("mô tả")));
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(get("/admin/lost-found").with(as(customer))).andExpect(status().isForbidden());
    }
}
