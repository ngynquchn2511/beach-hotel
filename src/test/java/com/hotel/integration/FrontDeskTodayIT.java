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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Man hinh "Hom nay" cua le tan: khach den / di / qua han / khong den / phong cho don + thao tac nhanh quay ve trang
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FrontDeskTodayIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingGuestRepository guestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final LocalDate TODAY = LocalDate.now();
    private RoomType type;
    private User customer;
    private CustomUserDetails staff;

    @BeforeEach
    void seed() {
        type = roomTypeRepository.save(RoomType.builder().name("Phong TodayIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        customer = userRepository.save(User.builder().fullName("Khach Today").email("khach.today@mail.com").phoneNumber("0912000111")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        staff = new CustomUserDetails(userRepository.findByEmail("staff@hotel.com").orElseThrow());
    }

    private Room room(String number, RoomStatus status) {
        return roomRepository.save(Room.builder().roomNumber(number + "-" + System.nanoTime() % 10000).roomType(type)
                .price(BigDecimal.valueOf(500000)).status(status).build());
    }

    private Booking booking(Room room, LocalDate in, LocalDate out, BookingStatus status, String guest) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(2)
                .guestName(guest).guestPhone("0912000111").guestEmail("khach.today@mail.com")
                .totalAmount(BigDecimal.valueOf(500000)).discountAmount(BigDecimal.ZERO).status(status).newBooking(false).build());
    }

    private void declareGuest(Booking b) {
        guestRepository.save(BookingGuest.builder().booking(b).fullName("Le Van Khai").dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE).idType(IdDocumentType.CCCD).idNumber("012345678901").nationality("Việt Nam").build());
    }

    @Test
    void showsArrivalsDeparturesAttentionAndRoomsToClean() throws Exception {
        booking(room("A1", RoomStatus.AVAILABLE), TODAY, TODAY.plusDays(2), BookingStatus.CONFIRMED, "Khach Den Chua Khai Bao");
        booking(room("A2", RoomStatus.NOT_READY), TODAY, TODAY.plusDays(1), BookingStatus.CONFIRMED, "Khach Den Phong Ban");
        booking(room("D1", RoomStatus.OCCUPIED), TODAY.minusDays(2), TODAY, BookingStatus.CHECKED_IN, "Khach Di Hom Nay");
        booking(room("O1", RoomStatus.OCCUPIED), TODAY.minusDays(3), TODAY.minusDays(1), BookingStatus.CHECKED_IN, "Khach Qua Han");
        booking(room("N1", RoomStatus.AVAILABLE), TODAY.minusDays(1), TODAY.plusDays(1), BookingStatus.CONFIRMED, "Khach Khong Den");
        // Don da huy khong duoc hien
        booking(room("C1", RoomStatus.AVAILABLE), TODAY, TODAY.plusDays(1), BookingStatus.CANCELLED, "Khach Da Huy");

        mockMvc.perform(get("/admin/today").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khach Den Chua Khai Bao")))
                .andExpect(content().string(containsString("Chưa khai báo lưu trú")))
                .andExpect(content().string(containsString("Khai báo &amp; check-in")))
                .andExpect(content().string(containsString("Khach Den Phong Ban")))
                .andExpect(content().string(containsString("Phòng chưa dọn")))
                .andExpect(content().string(containsString("Khach Di Hom Nay")))
                .andExpect(content().string(containsString("Check-out &amp; thanh toán")))
                .andExpect(content().string(containsString("Khach Qua Han")))
                .andExpect(content().string(containsString("quá hạn trả phòng")))
                .andExpect(content().string(containsString("Khach Khong Den")))
                .andExpect(content().string(containsString("Đã dọn xong")))
                .andExpect(content().string(not(containsString("Khach Da Huy"))));
    }

    @Test
    void quickCheckIn_andMarkReady_returnToTodayPage() throws Exception {
        Room ready = room("Q1", RoomStatus.AVAILABLE);
        Booking b = booking(ready, TODAY, TODAY.plusDays(1), BookingStatus.CONFIRMED, "Khach Nhan Nhanh");
        declareGuest(b);

        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).param("back", "today").with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/today"))
                .andExpect(flash().attribute("successMessage", containsString("Đã check-in")));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        Room dirty = room("R1", RoomStatus.NOT_READY);
        mockMvc.perform(post("/admin/rooms/{id}/mark-ready", dirty.getId()).param("back", "today").with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/today"));
        assertThat(roomRepository.findById(dirty.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);

        // Khong co back=today -> giu duong cu; gia tri la khong duoc dung lam dich chuyen huong
        Room dirty2 = room("R2", RoomStatus.NOT_READY);
        mockMvc.perform(post("/admin/rooms/{id}/mark-ready", dirty2.getId()).param("back", "https://evil.example").with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/rooms"));
    }

    @Test
    void otherDay_sidebarBadge_andLegacyStaffPage() throws Exception {
        booking(room("T1", RoomStatus.AVAILABLE), TODAY.plusDays(1), TODAY.plusDays(2), BookingStatus.CONFIRMED, "Khach Ngay Mai");
        booking(room("T2", RoomStatus.AVAILABLE), TODAY, TODAY.plusDays(1), BookingStatus.CONFIRMED, "Khach Hom Nay");

        mockMvc.perform(get("/admin/today").param("date", TODAY.plusDays(1).toString()).with(user(staff)))
                .andExpect(content().string(containsString("Khach Ngay Mai")))
                .andExpect(content().string(containsString("Lịch trực")))
                .andExpect(content().string(not(containsString("Khach Hom Nay</a>"))));

        mockMvc.perform(get("/admin/bookings").with(user(staff)))
                .andExpect(model().attribute("arrivalsLeftToday", org.hamcrest.Matchers.greaterThanOrEqualTo(1L)));

        mockMvc.perform(get("/staff/bookings").with(user(staff))).andExpect(redirectedUrl("/admin/today"));
    }
}
