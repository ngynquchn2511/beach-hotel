package com.hotel.integration;

import com.hotel.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach tu khai bao luu tru truoc khi den: link co ma truy cap, gioi han so khach, le tan thay nhan "Khach tu khai"
class OnlinePreCheckInIT extends ItFixtures {

    private User staff;
    private Booking booking;
    private String token;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Hang KB IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        booking = persistBooking(guest, room, BookingStatus.CONFIRMED, 0, 2);
        token = booking.getAccessToken();
    }

    private ResultActions declare(String name, String idNumber) throws Exception {
        return mockMvc.perform(post("/customer/bookings/{id}/guests", booking.getId()).with(csrf())
                .param("token", token).param("fullName", name).param("dateOfBirth", "1992-05-20")
                .param("gender", "FEMALE").param("idType", "CCCD").param("idNumber", idNumber)
                .param("nationality", "Việt Nam"));
    }

    private List<BookingGuest> guests() {
        return bookingGuestRepository.findByBookingIdOrderByIdAsc(booking.getId());
    }

    @Test
    void guestDeclaresWithTokenLink_staffSeesSelfDeclared_andCanCheckIn() throws Exception {
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token))
                .andExpect(content().string(containsString("Khai báo lưu trú online")));

        declare("Trần Thị Mai", "001092012345")
                .andExpect(redirectedUrl("/customer/bookings/" + booking.getId() + "?token=" + token + "#khai-bao"))
                .andExpect(flash().attribute("guestSuccess", containsString("Đã thêm khách")));

        assertThat(guests()).hasSize(1);
        assertThat(guests().get(0).isSelfDeclared()).isTrue();

        // Trang cua khach chi hien 3 so cuoi
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token))
                .andExpect(content().string(containsString("345")))
                .andExpect(content().string(not(containsString("001092012345"))));

        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(as(staff)))
                .andExpect(content().string(containsString("Khách tự khai")))
                .andExpect(content().string(containsString("001092012345")));

        // Da khai bao -> le tan check-in duoc ngay
        mockMvc.perform(post("/admin/bookings/{id}/check-in", booking.getId()).with(csrf()).with(as(staff)));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        // Da nhan phong -> khach khong sua duoc nua
        declare("Nguyễn Văn B", "001092099999").andExpect(flash().attribute("guestError", containsString("tại quầy")));
    }

    @Test
    void limitToRoomCapacity_andDuplicateDocumentRejected() throws Exception {
        declare("Khách Một", "001092000001");
        declare("Khách Một Lại", "001092000001").andExpect(flash().attribute("guestError", containsString("đã được khai báo")));
        declare("Khách Hai", "001092000002");
        declare("Khách Ba", "001092000003").andExpect(flash().attribute("guestError", containsString("tối đa 2 khách")));
        assertThat(guests()).hasSize(2);
    }

    @Test
    void canRemoveOwnDeclaration_butNotStaffEntered() throws Exception {
        BookingGuest byStaff = declareGuest(booking);
        declare("Khách Tự Khai", "001092000010");
        BookingGuest own = guests().stream().filter(BookingGuest::isSelfDeclared).findFirst().orElseThrow();

        mockMvc.perform(post("/customer/bookings/{id}/guests/{gid}/delete", booking.getId(), byStaff.getId())
                        .with(csrf()).param("token", token))
                .andExpect(flash().attribute("guestError", containsString("Không tìm thấy")));
        mockMvc.perform(post("/customer/bookings/{id}/guests/{gid}/delete", booking.getId(), own.getId())
                .with(csrf()).param("token", token));

        assertThat(guests()).extracting(BookingGuest::getId).containsExactly(byStaff.getId());
    }

    @Test
    void pendingBookingOrWrongToken_cannotDeclare() throws Exception {
        booking.setStatus(BookingStatus.PENDING);
        bookingRepository.save(booking);
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token))
                .andExpect(content().string(not(containsString("id=\"khai-bao\""))));
        declare("Khách", "001092000020").andExpect(flash().attribute("guestError", containsString("chưa được xác nhận")));

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        token = "sai-ma";
        declare("Khách", "001092000021");
        assertThat(guests()).isEmpty();
    }

    @Test
    void pastCheckInDate_cannotDeclareOnline() throws Exception {
        booking.setCheckInDate(LocalDate.now().minusDays(1));
        bookingRepository.save(booking);
        declare("Khách", "001092000030").andExpect(flash().attribute("guestError", containsString("tại quầy")));
    }
}
