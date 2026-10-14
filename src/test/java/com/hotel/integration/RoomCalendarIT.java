package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.RoomCalendarService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomCalendarIT {

    private static final LocalDate FROM = LocalDate.of(2030, 3, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomCalendarService roomCalendarService;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private User staff;
    private User customer;

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder()
                .name("Phong CalendarIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder()
                .roomNumber("CAL-" + System.nanoTime() % 100000)
                .roomType(rt).price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        staff = userRepository.save(User.builder()
                .fullName("Staff Cal").email("staff.cal@hotel.com").phoneNumber("0900000044")
                .password(passwordEncoder.encode("x")).role(UserRole.STAFF).build());
        customer = userRepository.save(User.builder()
                .fullName("Customer Cal").email("customer.cal@mail.com").phoneNumber("0900000055")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    private Booking seedBooking(LocalDate in, LocalDate out, BookingStatus status, String guestName) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName(guestName).guestPhone("0900000055").guestEmail("customer.cal@mail.com")
                .totalAmount(BigDecimal.valueOf(500000)).discountAmount(BigDecimal.ZERO).status(status).build());
    }

    private RoomCalendarService.RoomRow rowOf(RoomCalendarService.Calendar cal) {
        return cal.rows().stream().filter(r -> r.room().getId().equals(room.getId())).findFirst().orElseThrow();
    }

    @Test
    void bar_startsMidCheckInDay_endsMidCheckOutDay() {
        // Nhan 03/03, tra 05/03 trong khoang tu 01/03: ngay index 2 -> 4
        seedBooking(FROM.plusDays(2), FROM.plusDays(4), BookingStatus.CONFIRMED, "Khach A");

        var bar = rowOf(roomCalendarService.build(FROM, 7, FROM)).bars().get(0);

        assertEquals(6, bar.gridStart());  // nua sau ngay index 2 = nua-cot 5 (0-based) -> line 6
        assertEquals(10, bar.gridEnd());   // het nua dau ngay index 4 = line 10
        assertFalse(bar.clippedLeft());
        assertFalse(bar.clippedRight());
    }

    @Test
    void backToBackBookings_doNotOverlap() {
        seedBooking(FROM.plusDays(1), FROM.plusDays(3), BookingStatus.CHECKED_IN, "Khach truoc");
        seedBooking(FROM.plusDays(3), FROM.plusDays(5), BookingStatus.PENDING, "Khach sau");

        var bars = rowOf(roomCalendarService.build(FROM, 7, FROM)).bars();

        assertEquals(2, bars.size());
        assertTrue(bars.get(0).gridEnd() <= bars.get(1).gridStart());
    }

    @Test
    void bookingOutsideRange_isClippedAtEdges() {
        seedBooking(FROM.minusDays(2), FROM.plusDays(10), BookingStatus.CHECKED_IN, "Khach dai ngay");

        var bar = rowOf(roomCalendarService.build(FROM, 7, FROM)).bars().get(0);

        assertTrue(bar.clippedLeft());
        assertTrue(bar.clippedRight());
        assertEquals(1, bar.gridStart());
        assertEquals(15, bar.gridEnd()); // 7 ngay x 2 nua + 1
    }

    @Test
    void cancelledBooking_notShown_andFreeRoomCountReflectsOccupiedNights() {
        seedBooking(FROM, FROM.plusDays(1), BookingStatus.CANCELLED, "Khach huy");
        seedBooking(FROM.plusDays(1), FROM.plusDays(2), BookingStatus.CONFIRMED, "Khach o");

        var cal = roomCalendarService.build(FROM, 7, FROM);

        assertEquals(1, rowOf(cal).bars().size());
        // Dem index 1 it hon dem index 0 dung 1 phong (phong cua test nay bi chiem)
        assertEquals(cal.freeRooms().get(0) - 1, cal.freeRooms().get(1));
    }

    @Test
    void calendarPage_rendersBookingsForStaff() throws Exception {
        Booking b = seedBooking(FROM.plusDays(1), FROM.plusDays(3), BookingStatus.CONFIRMED, "Nguyen Van Lich");
        seedBooking(FROM.plusDays(4), FROM.plusDays(5), BookingStatus.CANCELLED, "Khach Da Huy");

        mockMvc.perform(get("/admin/room-calendar").param("from", FROM.toString()).param("days", "14")
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/room-calendar/index"))
                .andExpect(content().string(containsString("Nguyen Van Lich")))
                .andExpect(content().string(containsString("/admin/bookings/" + b.getId())))
                .andExpect(content().string(not(containsString("Khach Da Huy"))));
    }

    @Test
    void invalidDays_fallsBackTo14() throws Exception {
        mockMvc.perform(get("/admin/room-calendar").param("days", "999")
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(14, ((RoomCalendarService.Calendar)
                        result.getModelAndView().getModel().get("cal")).days()));
    }

    @Test
    void customer_cannotAccessCalendar() throws Exception {
        mockMvc.perform(get("/admin/room-calendar").with(user(new CustomUserDetails(customer))))
                .andExpect(status().isForbidden());
    }
}
