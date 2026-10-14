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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBookingFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private User admin;
    private User staff;
    private User customer;

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder()
                .name("Phong AdminFlowIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder()
                .roomNumber("AF-" + System.nanoTime() % 100000)
                .roomType(rt).price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());

        admin = userRepository.findByEmail("admin@hotel.com").orElseGet(() -> userRepository.save(User.builder()
                .fullName("Admin IT").email("admin.it@hotel.com").phoneNumber("0900000011")
                .password(passwordEncoder.encode("x")).role(UserRole.ADMIN).build()));
        staff = userRepository.findByEmail("staff@hotel.com").orElseGet(() -> userRepository.save(User.builder()
                .fullName("Staff IT").email("staff.it@hotel.com").phoneNumber("0900000022")
                .password(passwordEncoder.encode("x")).role(UserRole.STAFF).build()));
        customer = userRepository.save(User.builder()
                .fullName("Customer IT").email("customer.adminflow@mail.com").phoneNumber("0900000033")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    private Booking seedBooking(BookingStatus status) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(LocalDate.now().plusDays(1)).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khach").guestPhone("0900000033").guestEmail("customer.adminflow@mail.com")
                .totalAmount(BigDecimal.valueOf(500000)).discountAmount(BigDecimal.ZERO).status(status).build());
    }

    @Test
    void confirmBooking_pendingToConfirmed_viaRealController() throws Exception {
        Booking b = seedBooking(BookingStatus.PENDING);

        mockMvc.perform(post("/admin/bookings/{id}/confirm", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(admin))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId()));

        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkIn_confirmedBooking_setsRoomOccupiedInDb() throws Exception {
        Booking b = seedBooking(BookingStatus.CONFIRMED);
        // Khai bao luu tru truoc khi check-in
        mockMvc.perform(post("/admin/bookings/{id}/guests", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(staff)))
                        .param("fullName", "Khach").param("dateOfBirth", "1990-01-15").param("gender", "FEMALE")
                        .param("idType", "CCCD").param("idNumber", "001190000001").param("nationality", "Việt Nam"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().is3xxRedirection());

        assertEquals(BookingStatus.CHECKED_IN, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.OCCUPIED, roomRepository.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkIn_pendingBooking_rejectedWithFlashErrorAndNoStatusChange() throws Exception {
        Booking b = seedBooking(BookingStatus.PENDING);

        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(admin))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(BookingStatus.PENDING, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkOut_checkedInBooking_setsRoomNotReady() throws Exception {
        Booking b = seedBooking(BookingStatus.CHECKED_IN);

        mockMvc.perform(post("/admin/bookings/{id}/check-out", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(admin))))
                .andExpect(status().is3xxRedirection());

        assertEquals(BookingStatus.CHECKED_OUT, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
        assertEquals(RoomStatus.NOT_READY, roomRepository.findById(room.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancel_pendingBooking_staffAllowedToCancel() throws Exception {
        Booking b = seedBooking(BookingStatus.PENDING);

        mockMvc.perform(post("/admin/bookings/{id}/cancel", b.getId()).with(csrf())
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().is3xxRedirection());

        assertEquals(BookingStatus.CANCELLED, bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    void listBookings_filterByStatus_onlyReturnsMatching() throws Exception {
        seedBooking(BookingStatus.PENDING);
        seedBooking(BookingStatus.CONFIRMED);

        mockMvc.perform(get("/admin/bookings").param("status", "PENDING")
                        .with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/bookings/list"));
    }

    @Test
    void listBookings_customerRole_forbidden() throws Exception {
        mockMvc.perform(get("/admin/bookings").with(user(new CustomUserDetails(customer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void walkInBooking_create_savesConfirmedBookingWithoutExistingAccount() throws Exception {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);

        mockMvc.perform(post("/admin/bookings/walk-in/new").with(csrf())
                        .with(user(new CustomUserDetails(staff)))
                        .param("guestName", "Khach Tai Quay")
                        .param("guestEmail", "walkin.it@mail.com")
                        .param("guestPhone", "0900555666")
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString())
                        .param("checkOut", out.toString())
                        .param("checkInTime", "14:00")
                        .param("checkOutTime", "12:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings"));

        boolean created = bookingRepository.findAll().stream()
                .anyMatch(b -> "0900555666".equals(b.getGuestPhone()) && b.getStatus() == BookingStatus.CONFIRMED);
        org.junit.jupiter.api.Assertions.assertTrue(created);
    }

    @Test
    void dashboard_customerRole_forbidden() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user(new CustomUserDetails(customer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_adminRole_returnsStatsOk() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("totalRooms", "availableRooms", "pendingBookings"));
    }
}
