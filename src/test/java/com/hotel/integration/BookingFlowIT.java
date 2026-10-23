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

    // Khach vang lai chi xem: mo trang dat phong / gui form dat phong / kiem tra ma -> chuyen ve /login, khong tao don
    @Test
    void anonymousGuest_cannotBook_redirectedToLogin() throws Exception {
        LocalDate in = LocalDate.now().plusDays(5);
        long before = bookingRepository.count();

        mockMvc.perform(get("/customer/bookings/new").param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khach Vang Lai").param("guestPhone", "0909001122").param("guestEmail", "guest.flow@mail.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/customer/bookings/check-discount").param("code", "X").param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString()))
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/customer/bookings/group/new").param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString()))
                .andExpect(redirectedUrlPattern("**/login"));

        assertEquals(before, bookingRepository.count());
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmail("guest.flow@mail.com").isEmpty(),
                "khong con tu tao tai khoan an cho khach vang lai");
        // Xem phong van khong can dang nhap
        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đăng nhập để đặt phòng")));
    }

    // Bi chuyen ve /login tu trang dat phong: trang dang nhap bao ly do, dang nhap xong quay lai dung trang dat phong
    @Test
    void anonymousGuest_loginThenResumesBooking() throws Exception {
        persistCustomer("resume.flow@mail.com");
        LocalDate in = LocalDate.now().plusDays(6);
        String bookUrl = "/customer/bookings/new?roomId=" + room.getId() + "&checkIn=" + in + "&checkOut=" + in.plusDays(1);
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();

        mockMvc.perform(get(bookUrl).session(session)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/login").session(session))
                .andExpect(model().attribute("bookingLoginRequired", true));
        mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "resume.flow@mail.com").param("password", "Abc12345"))
                // Spring Security them "&continue" vao link quay lai
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost" + bookUrl)));
        // Mo /login binh thuong (khong tu trang dat phong) thi khong hien thong bao
        mockMvc.perform(get("/login")).andExpect(model().attributeDoesNotExist("bookingLoginRequired"));
    }

    @Test
    void createBooking_asCustomer_succeedsAndRedirectsToBookingView() throws Exception {
        LocalDate in = LocalDate.now().plusDays(5);
        LocalDate out = LocalDate.now().plusDays(7);
        User me = persistCustomer("guest.flow@mail.com");

        mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(user(new CustomUserDetails(me)))
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

        mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(user(new CustomUserDetails(persistCustomer("overlap2@mail.com"))))
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
    void viewBooking_emailLinkWithoutLogin_redirectsToLogin_ownerSeesAfterLogin() throws Exception {
        User owner = persistCustomer("guestacc@mail.com");
        Booking booking = pendingBookingOf(owner);

        // Link trong email (co ma truy cap) van bat dang nhap truoc khi xem don
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", booking.getAccessToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", booking.getAccessToken())
                        .with(user(new CustomUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/booking-success"));
    }

    @Test
    void viewBooking_anonymousRedirected_wrongTokenOrIdNotFound_noPersonalData() throws Exception {
        User owner = persistCustomer("secret.owner@mail.com");
        User other = persistCustomer("other.viewer@mail.com");
        Booking booking = bookingRepository.save(Booking.builder().customer(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(1)).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Nguoi Bi Lo Thong Tin").guestPhone("0911222333").guestEmail("secret.owner@mail.com")
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());

        // Chua dang nhap -> ve trang dang nhap, khong lo thong tin don
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()))
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0911222333"))));
        // Khach khac dang nhap: doi so id / ma sai khong xem duoc don nguoi khac
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(new CustomUserDetails(other))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0911222333"))));
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", "khongphaimacuadonnay00000000000")
                        .with(user(new CustomUserDetails(other))))
                .andExpect(status().isNotFound());
        // Ma cua don nay khong mo duoc don khac
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId() + 99999).param("token", booking.getAccessToken())
                        .with(user(new CustomUserDetails(other))))
                .andExpect(status().isNotFound());
        // Chu don dang nhap thi xem duoc khong can ma
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(new CustomUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0911222333")));
    }

    @Test
    void createBooking_redirectLinkContainsAccessToken_needsLoginToOpen() throws Exception {
        LocalDate in = LocalDate.now().plusDays(20);
        User customer = persistCustomer("token.flow@mail.com");
        String location = mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .with(user(new CustomUserDetails(customer)))
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", in.plusDays(1).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Khach Token").param("guestPhone", "0909003344").param("guestEmail", "token.flow@mail.com"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        org.junit.jupiter.api.Assertions.assertTrue(location.matches("/customer/bookings/\\d+\\?token=[0-9a-f]{32}"), location);
        mockMvc.perform(get(location)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get(location).with(user(new CustomUserDetails(customer))))
                .andExpect(status().isOk()).andExpect(view().name("customer/booking-success"));
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
        mockMvc.perform(get("/customer/bookings/check-discount").with(user(new CustomUserDetails(persistCustomer("disc.flow@mail.com"))))
                        .param("code", "KHONGTONTAI")
                        .param("roomId", room.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(1).toString())
                        .param("checkOut", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    private Booking pendingBookingOf(User owner) {
        return bookingRepository.save(Booking.builder().customer(owner).room(room)
                .checkInDate(LocalDate.now().plusDays(3)).checkOutDate(LocalDate.now().plusDays(4))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Owner").guestPhone("0900111222").guestEmail(owner.getEmail())
                .totalAmount(BigDecimal.valueOf(400000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build());
    }

    // Chua dang nhap thi khong huy duoc, ke ca co dung ma truy cap trong link email -> ve trang dang nhap
    @Test
    void cancelBooking_withoutLogin_redirectsToLogin_evenWithValidToken() throws Exception {
        Booking booking = pendingBookingOf(persistCustomer("notoken@mail.com"));

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).with(csrf()))
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).param("token", booking.getAccessToken()).with(csrf()))
                .andExpect(redirectedUrlPattern("**/login"));

        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.PENDING,
                bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
    }

    // Khach khac dang nhap, khong co / sai ma truy cap -> khong huy duoc, bao link khong hop le
    @Test
    void cancelBooking_otherCustomerWithoutValidToken_rejected() throws Exception {
        Booking booking = pendingBookingOf(persistCustomer("guestlink@mail.com"));
        User other = persistCustomer("guestlink.other@mail.com");

        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).with(csrf()).with(user(new CustomUserDetails(other))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post("/customer/bookings/{id}/cancel", booking.getId()).param("token", "sai-ma").with(csrf())
                        .with(user(new CustomUserDetails(other))))
                .andExpect(flash().attributeExists("errorMessage"));

        org.junit.jupiter.api.Assertions.assertEquals(BookingStatus.PENDING,
                bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelBooking_ownerCancelsPendingBooking_statusBecomesCancelled() throws Exception {
        User owner = persistCustomer("cancelowner@mail.com");
        Booking booking = pendingBookingOf(owner);

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
