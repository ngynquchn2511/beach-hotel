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
    // Trang lich chi xem duoc trong 6 thang toi -> test qua trang dung ngay gan
    private static final LocalDate NEAR = LocalDate.now().plusDays(10);

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

    private static org.hamcrest.Matcher<String> roomSelected(Long roomId) {
        return org.hamcrest.Matchers.matchesPattern("(?s).*<option value=\"" + roomId + "\"\\s+selected=\"selected\".*");
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
        Booking b = seedBooking(NEAR.plusDays(1), NEAR.plusDays(3), BookingStatus.CONFIRMED, "Nguyen Van Lich");
        seedBooking(NEAR.plusDays(4), NEAR.plusDays(5), BookingStatus.CANCELLED, "Khach Da Huy");

        mockMvc.perform(get("/admin/room-calendar").param("from", NEAR.toString()).param("days", "14")
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/room-calendar/index"))
                .andExpect(content().string(containsString("Nguyen Van Lich")))
                .andExpect(content().string(containsString("/admin/bookings/" + b.getId())))
                .andExpect(content().string(not(containsString("Khach Da Huy"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sixMonthView_monthMode_overview_andClampedNavigation() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusMonths(6);
        java.time.YearMonth last = java.time.YearMonth.from(horizon);
        // Don xa (5 thang toi) phai thay duoc o che do xem ca thang va o tong quan
        LocalDate far = today.plusMonths(5).withDayOfMonth(10);
        seedBooking(far, far.plusDays(2), BookingStatus.CONFIRMED, "Khach Dat Xa");

        mockMvc.perform(get("/admin/room-calendar").param("month", java.time.YearMonth.from(far).toString())
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khach Dat Xa")))
                .andExpect(content().string(containsString("Tổng quan đặt phòng 6 tháng tới")))
                .andExpect(content().string(containsString("Xem cả tháng")))
                .andExpect(result -> {
                    var cal = (RoomCalendarService.Calendar) result.getModelAndView().getModel().get("cal");
                    assertEquals(far.withDayOfMonth(1), cal.from());
                    assertEquals(far.lengthOfMonth(), cal.days());
                    var overview = (java.util.List<RoomCalendarService.MonthLoad>) result.getModelAndView().getModel().get("overview");
                    assertEquals(7, overview.size());
                    var day = overview.stream().flatMap(m -> m.days().stream()).filter(d -> d.date().equals(far)).findFirst().orElseThrow();
                    assertTrue(day.booked() >= 1);
                    assertTrue(day.bookable());
                    var closed = overview.get(6).days().stream().filter(d -> d.date().isAfter(horizon)).findFirst();
                    closed.ifPresent(d -> assertFalse(d.bookable()));
                });

        // Thang qua 6 thang -> ve thang cuoi cung xem duoc
        mockMvc.perform(get("/admin/room-calendar").param("month", last.plusMonths(3).toString())
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(result -> assertEquals(last, result.getModelAndView().getModel().get("month")));
        // Khung ngay qua 6 thang -> keo lui de ket thuc dung ngay gioi han, nut "khoang sau" tat
        mockMvc.perform(get("/admin/room-calendar").param("from", today.plusYears(2).toString()).param("days", "30")
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(result -> {
                    var cal = (RoomCalendarService.Calendar) result.getModelAndView().getModel().get("cal");
                    assertEquals(horizon.plusDays(1), cal.to());
                    assertEquals(false, result.getModelAndView().getModel().get("canNext"));
                });
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

    @Test
    void bookedNight_hasNoWalkInLink_freeNightsKeepIt() throws Exception {
        // Khach nhan 03/03 -> nua dau o 03/03 van lo ra nhung KHONG duoc tro toi dem da co khach
        seedBooking(NEAR.plusDays(2), NEAR.plusDays(4), BookingStatus.CONFIRMED, "Khach Dem");
        String base = "/admin/bookings/walk-in/new?roomId=" + room.getId() + "&amp;checkIn=";

        mockMvc.perform(get("/admin/room-calendar").param("from", NEAR.toString()).param("days", "7")
                        .with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(base + NEAR.plusDays(1))))
                .andExpect(content().string(not(containsString(base + NEAR.plusDays(2) + "&amp;"))))
                .andExpect(content().string(not(containsString(base + NEAR.plusDays(3) + "&amp;"))))
                // Ngay tra phong 05/03: dem 05/03 con trong -> dat tiep duoc
                .andExpect(content().string(containsString(base + NEAR.plusDays(4) + "&amp;")));

        RoomCalendarService.RoomRow row = rowOf(roomCalendarService.build(NEAR, 7, NEAR));
        assertFalse(row.isFree(NEAR.plusDays(2)));
        assertTrue(row.isFree(NEAR.plusDays(4)));
    }

    @Test
    void walkInForm_preselectedRoomTaken_warnsAndForcesChoice_insteadOfSilentlyPickingAnother() throws Exception {
        seedBooking(FROM.plusDays(2), FROM.plusDays(4), BookingStatus.CONFIRMED, "Khach Dem");
        // Phong khac con trong - truoc day dropdown tu chon phong nay ma khong bao gi
        Room other = roomRepository.save(Room.builder().roomNumber("CAL2-" + System.nanoTime() % 100000)
                .roomType(room.getRoomType()).price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());

        mockMvc.perform(get("/admin/bookings/walk-in/new").with(user(new CustomUserDetails(staff)))
                        .param("roomId", room.getId().toString()).param("src", "calendar")
                        .param("checkIn", FROM.plusDays(2).toString()).param("checkOut", FROM.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không còn trống")))
                .andExpect(content().string(containsString("-- Chọn phòng --")))
                .andExpect(content().string(containsString("Quay lại lịch phòng")))
                .andExpect(content().string(not(roomSelected(other.getId()))))
                .andExpect(content().string(not(roomSelected(room.getId()))));

        // Phong con trong -> chon san dung phong, khong co o "-- Chon phong --"
        mockMvc.perform(get("/admin/bookings/walk-in/new").with(user(new CustomUserDetails(staff)))
                        .param("roomId", room.getId().toString())
                        .param("checkIn", FROM.plusDays(5).toString()).param("checkOut", FROM.plusDays(6).toString()))
                .andExpect(content().string(not(containsString("-- Chọn phòng --"))))
                .andExpect(content().string(roomSelected(room.getId())));
    }

    @Test
    void walkInPost_error_keepsEverythingTyped() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/bookings/walk-in/new")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(user(new CustomUserDetails(staff)))
                        .param("guestName", "Tran Van Quay").param("guestPhone", "0911222333").param("guestEmail", "email-sai")
                        .param("roomId", room.getId().toString()).param("src", "calendar")
                        .param("checkIn", FROM.plusDays(5).toString()).param("checkOut", FROM.plusDays(6).toString())
                        .param("checkInTime", "16:00").param("checkOutTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("value=\"Tran Van Quay\"")))
                .andExpect(content().string(containsString("value=\"0911222333\"")))
                .andExpect(content().string(containsString("value=\"16:00\" selected=\"selected\"")))
                .andExpect(content().string(roomSelected(room.getId())));
    }
}
