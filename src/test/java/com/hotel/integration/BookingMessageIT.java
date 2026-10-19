package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingMessageRepository;
import com.hotel.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Nhan tin khach <-> le tan tren trang don: bao tuc thoi, dem chua doc, tra loi, chong spam, khoa khi don huy
class BookingMessageIT extends ItFixtures {

    @Autowired private BookingMessageRepository messageRepository;
    @Autowired private StaffNotificationService notificationService;

    private User guest;
    private User staff;
    private Booking booking;

    @BeforeEach
    void seed() {
        guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, null);
        booking = persistBooking(guest, persistRoom(persistRoomType("Hang TN IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE),
                BookingStatus.CONFIRMED, 5, 2);
    }

    @Test
    void guestAsks_staffSeesUnread_replies_guestSeesAnswer() throws Exception {
        long start = notificationService.lastId();
        mockMvc.perform(post("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(guest))
                        .param("body", "Resort có xe đón ở bến tàu Tuần Châu không ạ?"))
                .andExpect(flash().attribute("messageSuccess", containsString("Đã gửi")));
        TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s.getClass().getName().startsWith(StaffNotificationService.class.getName()))
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(notificationService.recent(start)).anySatisfy(n -> {
            assertThat(n.kind()).isEqualTo("message");
            assertThat(n.message()).contains("Tuần Châu");
            assertThat(n.url()).isEqualTo("/admin/bookings/" + booking.getId() + "#tin-nhan");
        });

        // Hop tin nhan + badge chua doc
        mockMvc.perform(get("/admin/messages").with(as(staff)).param("unread", "true"))
                .andExpect(content().string(containsString("Tuần Châu")))
                .andExpect(content().string(containsString("Tin nhắn chưa đọc")));
        // Mo don -> da doc
        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Tuần Châu")));
        assertThat(messageRepository.countByFromGuestTrueAndReadAtIsNull()).isZero();

        mockMvc.perform(post("/admin/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(staff))
                        .param("body", "Có ạ, xe đón miễn phí 9h-17h, anh chị báo giờ tàu giúp em."))
                .andExpect(flash().attribute("successMessage", containsString("Đã gửi trả lời")));
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(as(guest)))
                .andExpect(content().string(containsString("xe đón miễn phí")));
        assertThat(messageRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId()))
                .allSatisfy(m -> assertThat(m.getReadAt()).isNotNull());
    }

    @Test
    void validation_rateLimit_cancelledBookingClosed_strangerBlocked() throws Exception {
        mockMvc.perform(post("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(guest)).param("body", "  "))
                .andExpect(flash().attribute("messageError", containsString("nhập nội dung")));
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(guest)).param("body", "Tin " + i));
        }
        mockMvc.perform(post("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(guest)).param("body", "Tin 11"))
                .andExpect(flash().attribute("messageError", containsString("1 giờ")));

        User stranger = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        long before = messageRepository.count();
        mockMvc.perform(post("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(stranger)).param("body", "Xin chào"));
        assertThat(messageRepository.count()).isEqualTo(before);

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        Booking other = persistBooking(guest, booking.getRoom(), BookingStatus.CANCELLED, 30, 1);
        mockMvc.perform(post("/customer/bookings/{id}/messages", other.getId()).with(csrf()).with(as(guest)).param("body", "Còn phòng không?"))
                .andExpect(flash().attribute("messageError", containsString("hộp thư liên hệ")));
    }

    @Test
    void guestSendsPhoto_staffSeesIt_wrongTypeRejected() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        // Chi anh, khong chu -> van gui duoc
        mockMvc.perform(multipart("/customer/bookings/{id}/messages", booking.getId())
                        .file(new MockMultipartFile("image", "voi-sen.png", "image/png", png)).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("messageSuccess", containsString("Đã gửi")));
        BookingMessage m = messageRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId()).get(0);
        assertThat(m.getImageUrl()).startsWith("/uploads/messages/").endsWith(".png");
        assertThat(m.getBody()).isEmpty();
        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(content().string(containsString(m.getImageUrl())));
        mockMvc.perform(get("/admin/messages").with(as(staff)))
                .andExpect(content().string(containsString("bi-image")));

        // Kem chu + anh sai loai (doi duoi) -> tu choi, khong luu
        mockMvc.perform(multipart("/customer/bookings/{id}/messages", booking.getId())
                        .file(new MockMultipartFile("image", "virus.png", "application/x-msdownload", new byte[]{1, 2}))
                        .param("body", "Xem giúp em").with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("messageError", containsString("JPG, PNG hoặc WEBP")));
        mockMvc.perform(multipart("/customer/bookings/{id}/messages", booking.getId())
                        .file(new MockMultipartFile("image", "ghi-chu.pdf", "application/pdf", new byte[]{1}))
                        .with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("messageError", containsString("JPG, PNG hoặc WEBP")));
        // Khong chu, khong anh -> bao trong
        mockMvc.perform(multipart("/customer/bookings/{id}/messages", booking.getId()).with(csrf()).with(as(guest)))
                .andExpect(flash().attribute("messageError", containsString("nhập nội dung")));
        assertThat(messageRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId())).hasSize(1);
    }
}
