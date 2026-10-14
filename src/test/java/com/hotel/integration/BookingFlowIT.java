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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;

    @BeforeEach
    void seedRoom() {
        RoomType rt = roomTypeRepository.save(RoomType.builder()
                .name("Phong BookingFlowIT").basePrice(BigDecimal.valueOf(400000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder()
                .roomNumber("BF-" + System.nanoTime() % 100000)
                .roomType(rt).price(BigDecimal.valueOf(400000)).status(RoomStatus.AVAILABLE).build());
    }

    private User persistCustomer(String email) {
        return userRepository.save(User.builder()
                .fullName("Khach " + email).email(email).phoneNumber("090" + (Math.abs(email.hashCode()) % 10000000))
                .password(passwordEncoder.encode("Abc12345")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    @Test
    void createBooking_asGuest_succeedsAndRedirectsToBookingView() throws Exception {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = LocalDate.now().plusDays(7);

        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString())
                        .param("checkOut", out.toString())
                        .param("guests", "2")
                        .param("checkInTime", "14:00")
                        .param("checkOutTime", "12:00")
                        .param("guestName", "Khach Vang Lai")
                        .param("guestPhone", "0909001122")
                        .param("guestEmail", "guest.flow@mail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/customer/bookings/*"));

        assertEquals(1, bookingRepository.findAll().stream()
                .filter(b -> b.getRoom().getId().equals(room.getId())).count());
    }

    @Test
    void createBooking_overlappingDates_rendersErrorAndDoesNotCreateSecondBooking() throws Exception {
        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = LocalDate.now().plusDays(12);
        User customer = persistCustomer("owner.overlap@mail.com");
        bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(in).checkOutDate(out).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .numberOfGuests(2).guestName("A").guestPhone("0900000000").guestEmail("a@mail.com")
                .totalAmount(BigDecimal.valueOf(800000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());
        long before = bookingRepository.count();

        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString())
                        .param("checkOut", out.toString())
                        .param("guests", "2")
                        .param("checkInTime", "14:00")
                        .param("checkOutTime", "12:00")
                        .param("guestName", "Khach Khac")
                        .param("guestPhone", "0909002233")
                        .param("guestEmail", "overlap2@mail.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-confirm"))
                .andExpect(model().attributeExists("errorMessage"));

        org.junit.jupiter.api.Assertions.assertEquals(before, bookingRepository.count());
    }

    @Test
    void viewBooking_anonymousGuestLink_accessibleWithoutLogin() throws Exception {
        User guestAccount = userRepository.save(User.builder()
                .fullName("Guest Acc").email("guestacc@khachvanglai.local").phoneNumber("0900111222")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        Booking booking = bookingRepository.save(Booking.builder().customer(guestAccount).room(room)
                .checkInDate(LocalDate.now().plusDays(1)).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Guest").guestPhone("0900111222").guestEmail("guestacc@khachvanglai.local")
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());

        // Khach vang lai xem duoc don khi link co dung ma truy cap
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", booking.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-success"));
    }

    @Test
    void viewBooking_anonymousWithoutOrWithWrongToken_notFoundAndNoPersonalData() throws Exception {
        User owner = persistCustomer("secret.owner@mail.com");
        Booking booking = bookingRepository.save(Booking.builder().customer(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(1)).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Nguoi Bi Lo Thong Tin").guestPhone("0911222333").guestEmail("secret.owner@mail.com")
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());

        // Doi so id tren URL khong con xem duoc don nguoi khac
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0911222333"))));
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", "khongphaimacuadonnay00000000000"))
                .andExpect(status().isNotFound());
        // Ma cua don nay khong mo duoc don khac
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId() + 99999).param("token", booking.getAccessToken()))
                .andExpect(status().isNotFound());
        // Chu don dang nhap thi xem duoc khong can ma
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(new CustomUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0911222333")));
    }

    @Test
    void createBooking_asGuest_redirectLinkContainsWorkingAccessToken() throws Exception {
        LocalDate in = LocalDate.now().plusDays(20);
        String location = mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khach Token").param("guestPhone", "0909003344").param("guestEmail", "token.flow@mail.com"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        org.junit.jupiter.api.Assertions.assertTrue(location.matches("/customer/bookings/\\d+\\?token=[0-9a-f]{32}"), location);
        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(view().name("customer/booking-success"));
    }

    @Test
    void viewBooking_loggedInNonOwner_rejected() throws Exception {
        User owner = persistCustomer("realowner@mail.com");
        User intruder = persistCustomer("intruder@mail.com");
        Booking booking = bookingRepository.save(Booking.builder().customer(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(1)).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Owner").guestPhone("0900111222").guestEmail("realowner@mail.com")
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());

        mockMvc.perform(get("/customer/bookings/{id}", booking.getId())
                        .with(user(new CustomUserDetails(intruder))))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(result.getResponse().getStatus() < 500));
    }

    @Test
    void checkDiscountCode_unknownCode_returnsInvalidJson() throws Exception {
        mockMvc.perform(get("/customer/bookings/check-discount")
                        .param("code", "KHONGTONTAI")
                        .param("roomId", room.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(1).toString())
                        .param("checkOut", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void cancelBooking_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/customer/bookings/{id}/cancel", 1L).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void cancelBooking_ownerCancelsPendingBooking_statusBecomesCancelled() throws Exception {
        User owner = persistCustomer("cancelowner@mail.com");
        Booking booking = bookingRepository.save(Booking.builder().customer(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(3)).checkOutDate(LocalDate.now().plusDays(4))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Owner").guestPhone("0900111222").guestEmail("cancelowner@mail.com")
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).with(csrf())
                        .with(user(new CustomUserDetails(owner))))
                .andExpect(status().is3xxRedirection());

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.CANCELLED, reloaded.getStatus());
    }

    private static void assertEquals(long expected, long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
