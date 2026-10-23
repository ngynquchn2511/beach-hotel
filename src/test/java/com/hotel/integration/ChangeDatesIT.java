package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach tu doi ngay luu tru: xem truoc gia (JSON), luu, canh bao cho le tan - chay qua controller that tren H2
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangeDatesIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private User owner;
    private Booking booking;
    private static final LocalDate IN = LocalDate.now().plusDays(20);

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Doi ngay IT").basePrice(BigDecimal.valueOf(400000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder().roomNumber("CD-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(400000)).status(RoomStatus.AVAILABLE).build());
        owner = saveCustomer("doi.ngay@mail.com", "0955555555");
        booking = saveBooking(owner, IN, IN.plusDays(1), BookingStatus.CONFIRMED);
    }

    private User saveCustomer(String email, String phone) {
        return userRepository.save(User.builder().fullName("Khach Doi Ngay").email(email).phoneNumber(phone)
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    private Booking saveBooking(User customer, LocalDate in, LocalDate out, BookingStatus status) {
        long nights = java.time.temporal.ChronoUnit.DAYS.between(in, out);
        BigDecimal amount = BigDecimal.valueOf(400000 * nights);
        return bookingRepository.save(Booking.builder().customer(customer).room(room).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(2)
                .guestName("Khach Doi Ngay").guestPhone("0955555555").guestEmail(customer.getEmail())
                .roomAmount(amount).totalAmount(amount).discountAmount(BigDecimal.ZERO).status(status).newBooking(false).build());
    }

    @Test
    void ownerSeesForm_quotesPrice_changesDates_staffIsAlerted() throws Exception {
        CustomUserDetails me = new CustomUserDetails(owner);
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"changeDatesForm\"")))
                .andExpect(content().string(containsString("Đổi ngày lưu trú")));

        // Xem truoc: 2 dem thay vi 1 -> tang 400.000
        LocalDate newIn = IN.plusDays(3);
        mockMvc.perform(get("/customer/bookings/{id}/change-dates/quote", booking.getId()).with(user(me))
                        .param("checkIn", newIn.toString()).param("checkOut", newIn.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.total").value("800.000"))
                .andExpect(jsonPath("$.message").value(containsString("Tăng 400.000 VND")));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCheckInDate()).isEqualTo(IN);

        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).with(user(me))
                        .param("checkIn", newIn.toString()).param("checkOut", newIn.plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/bookings/" + booking.getId() + "#doi-ngay"))
                .andExpect(flash().attribute("successMessage", containsString("800.000")));

        Booking changed = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(changed.getCheckInDate()).isEqualTo(newIn);
        assertThat(changed.getCheckOutDate()).isEqualTo(newIn.plusDays(2));
        assertThat(changed.getTotalAmount()).isEqualByComparingTo("800000");
        assertThat(changed.isDatesChanged()).isTrue();

        // Le tan thay canh bao o danh sach; mo chi tiet thi tat
        User staff = userRepository.findByEmail("staff@hotel.com").orElseThrow();
        mockMvc.perform(get("/admin/bookings").with(user(new CustomUserDetails(staff))))
                .andExpect(content().string(containsString("Khách đổi ngày lưu trú")));
        mockMvc.perform(get("/admin/bookings/{id}", booking.getId()).with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk());
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().isDatesChanged()).isFalse();
    }

    @Test
    void quote_overlapsAnotherGuest_notOk_andPostRejected() throws Exception {
        User other = saveCustomer("khach.khac@mail.com", "0966666666");
        saveBooking(other, IN.plusDays(5), IN.plusDays(7), BookingStatus.PENDING);
        CustomUserDetails me = new CustomUserDetails(owner);

        mockMvc.perform(get("/customer/bookings/{id}/change-dates/quote", booking.getId()).with(user(me))
                        .param("checkIn", IN.plusDays(4).toString()).param("checkOut", IN.plusDays(6).toString()))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(containsString("chờ xác nhận")));

        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).with(user(me))
                        .param("checkIn", IN.plusDays(4).toString()).param("checkOut", IN.plusDays(6).toString()))
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCheckInDate()).isEqualTo(IN);
    }

    @Test
    void otherCustomerOrAnonymous_cannotChangeDates() throws Exception {
        User stranger = saveCustomer("nguoi.la@mail.com", "0977777777");
        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf())
                        .with(user(new CustomUserDetails(stranger)))
                        .param("checkIn", IN.plusDays(3).toString()).param("checkOut", IN.plusDays(4).toString()))
                .andExpect(flash().attributeExists("errorMessage"));
        // Khong dang nhap (ke ca co dung ma truy cap) -> ve trang dang nhap, khong xem gia, khong doi duoc
        mockMvc.perform(get("/customer/bookings/{id}/change-dates/quote", booking.getId())
                        .param("checkIn", IN.plusDays(3).toString()).param("checkOut", IN.plusDays(4).toString()))
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).param("token", booking.getAccessToken())
                        .param("checkIn", IN.plusDays(3).toString()).param("checkOut", IN.plusDays(4).toString()))
                .andExpect(redirectedUrlPattern("**/login"));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCheckInDate()).isEqualTo(IN);
    }

    // Chu don mo link trong email (sau khi dang nhap) tu doi ngay / gio / combo, link giu nguyen ma truy cap
    @Test
    void ownerWithEmailLink_seesForms_quotesAndChangesDates_afterLogin() throws Exception {
        String token = booking.getAccessToken();
        CustomUserDetails me = new CustomUserDetails(owner);
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"changeDatesForm\"")))
                .andExpect(content().string(containsString("change-dates/quote?token=" + token)))
                .andExpect(content().string(containsString("name=\"token\" value=\"" + token + "\"")))
                .andExpect(content().string(containsString("Hủy đặt phòng")));

        LocalDate newIn = IN.plusDays(2);
        mockMvc.perform(get("/customer/bookings/{id}/change-dates/quote", booking.getId()).param("token", token).with(user(me))
                        .param("checkIn", newIn.toString()).param("checkOut", newIn.plusDays(1).toString()))
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(post("/customer/bookings/{id}/change-dates", booking.getId()).with(csrf()).param("token", token).with(user(me))
                        .param("checkIn", newIn.toString()).param("checkOut", newIn.plusDays(1).toString()))
                .andExpect(redirectedUrl("/customer/bookings/" + booking.getId() + "?token=" + token + "#doi-ngay"))
                .andExpect(flash().attributeExists("successMessage"));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCheckInDate()).isEqualTo(newIn);

        mockMvc.perform(post("/customer/bookings/{id}/edit-time", booking.getId()).with(csrf()).param("token", token).with(user(me))
                        .param("checkInTime", "15:00").param("checkOutTime", "11:00"))
                .andExpect(redirectedUrl("/customer/bookings/" + booking.getId() + "?token=" + token));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
    }
}
