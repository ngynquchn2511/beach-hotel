package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.PaymentService;
import com.hotel.service.PricingService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Khach o qua ngay tra phong: bat buoc tinh tien dem o them truoc khi thu tien; gia han o them (kiem phong trong, mien co ly do, hoan tac)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StayExtensionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingChargeRepository chargeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PricingService pricingService;
    @Autowired private PaymentService paymentService;

    private static final LocalDate TODAY = LocalDate.now();
    private static final BigDecimal PRICE = BigDecimal.valueOf(800000);
    private RoomType type;
    private User customer;
    private CustomUserDetails staff;

    @BeforeEach
    void seed() {
        type = roomTypeRepository.save(RoomType.builder().name("Phong GiaHanIT").basePrice(PRICE).maxGuests(2).build());
        customer = userRepository.save(User.builder().fullName("Khach Gia Han").email("khach.giahan@mail.com").phoneNumber("0912000222")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        staff = new CustomUserDetails(userRepository.findByEmail("staff@hotel.com").orElseThrow());
    }

    private Room room() {
        return roomRepository.save(Room.builder().roomNumber("GH-" + System.nanoTime() % 100000).roomType(type)
                .price(PRICE).status(RoomStatus.OCCUPIED).build());
    }

    private Booking booking(Room room, LocalDate in, LocalDate out, BookingStatus status, String guest) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(11, 0)).numberOfGuests(2)
                .guestName(guest).guestPhone("0912000222").guestEmail("khach.giahan@mail.com")
                .roomAmount(PRICE.multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(in, out))))
                .totalAmount(PRICE.multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(in, out))))
                .discountAmount(BigDecimal.ZERO).status(status).newBooking(false).build());
    }

    private List<BookingCharge> extensions(Booking b) {
        return chargeRepository.findByBookingIdOrderByCreatedAtAsc(b.getId()).stream()
                .filter(c -> c.getType() == ChargeType.EXTENDED_STAY).toList();
    }

    @Test
    void overdueGuest_mustBillExtraNightsBeforeCheckOut() throws Exception {
        Room r = room();
        Booking b = booking(r, TODAY.minusDays(4), TODAY.minusDays(2), BookingStatus.CHECKED_IN, "Khach Qua Han Hai Dem");
        BigDecimal extra = pricingService.roomAmount(PRICE, TODAY.minusDays(2), TODAY);

        mockMvc.perform(get("/admin/bookings/{id}/check-out", b.getId()).with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khách ở quá hạn")))
                .andExpect(content().string(containsString("Tính tiền ở thêm")));
        mockMvc.perform(get("/admin/bookings/{id}", b.getId()).with(user(staff)))
                .andExpect(content().string(containsString("quá ngày trả")))
                .andExpect(content().string(containsString("Gia hạn / ở thêm")));

        // Chua tinh tien dem qua han -> khong cho thu tien / check-out
        mockMvc.perform(post("/admin/bookings/{id}/check-out", b.getId()).param("paymentMethod", "CASH").with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId() + "/check-out"))
                .andExpect(flash().attribute("errorMessage", containsString("quá ngày trả phòng 2 đêm")));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        // Tinh tien o them den hom nay -> quay lai man hinh thu tien
        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.toString()).param("back", "checkout")
                        .with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId() + "/check-out"))
                .andExpect(flash().attribute("successMessage", containsString("tiền ở thêm")));
        Booking after = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(after.getCheckOutDate()).isEqualTo(TODAY);
        List<BookingCharge> ext = extensions(b);
        assertThat(ext).hasSize(1);
        assertThat(ext.get(0).getAmount()).isEqualByComparingTo(extra);
        assertThat(ext.get(0).getDescription()).contains("2 đêm").contains("quá hạn 2 đêm");
        // Dem da o roi -> khong hoan tac duoc
        assertThat(paymentService.amountDue(after)).isEqualByComparingTo(PRICE.multiply(BigDecimal.valueOf(2)).add(extra));

        mockMvc.perform(post("/admin/bookings/{id}/check-out", b.getId()).param("paymentMethod", "CASH").with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(paymentService.findByBookingId(b.getId()).orElseThrow().getAmount())
                .isEqualByComparingTo(PRICE.multiply(BigDecimal.valueOf(2)).add(extra));
    }

    @Test
    void overdueGuest_waiveNeedsReason() throws Exception {
        Booking b = booking(room(), TODAY.minusDays(3), TODAY.minusDays(1), BookingStatus.CHECKED_IN, "Khach Mien Tien");

        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.toString()).param("waive", "true")
                        .param("reason", " ").with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("ghi lý do")));
        assertThat(extensions(b)).isEmpty();

        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.toString()).param("waive", "true")
                        .param("reason", "Chuyen bay bi huy").with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("successMessage", containsString("miễn tiền")));
        BookingCharge c = extensions(b).get(0);
        assertThat(c.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.getDescription()).contains("MIỄN").contains("Chuyen bay bi huy");
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getCheckOutDate()).isEqualTo(TODAY);
    }

    @Test
    void extendFuture_checksNextGuest_andCanUndo() throws Exception {
        Room r = room();
        Booking b = booking(r, TODAY.minusDays(1), TODAY.plusDays(1), BookingStatus.CHECKED_IN, "Khach O Them");
        Booking next = booking(r, TODAY.plusDays(2), TODAY.plusDays(4), BookingStatus.CONFIRMED, "Khach Ke Tiep");

        // Den ngay khach sau nhan phong -> khong duoc
        mockMvc.perform(get("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(3).toString()).with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng không còn trống")))
                .andExpect(content().string(containsString("Khach Ke Tiep")));
        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(3).toString()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("#" + next.getCode())));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getCheckOutDate()).isEqualTo(TODAY.plusDays(1));

        // Ngay truoc khi khach sau den -> duoc, cong dung gia dem do
        mockMvc.perform(get("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(2).toString()).with(user(staff)))
                .andExpect(content().string(containsString("Gia hạn đến")));
        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(2).toString()).with(csrf()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getCheckOutDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(extensions(b).get(0).getAmount())
                .isEqualByComparingTo(pricingService.roomAmount(PRICE, TODAY.plusDays(1), TODAY.plusDays(2)));

        // Tien o them khong them / xoa tay duoc
        mockMvc.perform(post("/admin/bookings/{id}/charges/{cid}/delete", b.getId(), extensions(b).get(0).getId()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("Hoàn tác gia hạn")));
        mockMvc.perform(post("/admin/bookings/{id}/charges", b.getId()).param("type", "EXTENDED_STAY").param("amount", "500000")
                        .with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("màn hình Gia hạn")));

        // Bam nham -> hoan tac
        mockMvc.perform(post("/admin/bookings/{id}/extend/undo", b.getId()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("successMessage", containsString("hoàn tác")));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getCheckOutDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(extensions(b)).isEmpty();
    }

    @Test
    void rejectsInvalidDates_andNotCheckedIn() throws Exception {
        Room r = room();
        Booking b = booking(r, TODAY.minusDays(1), TODAY.plusDays(1), BookingStatus.CHECKED_IN, "Khach Ngay Sai");
        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(1).toString()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("phải sau ngày trả hiện tại")));
        mockMvc.perform(post("/admin/bookings/{id}/extend", b.getId()).param("to", TODAY.plusDays(40).toString()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("tối đa 30 đêm")));

        Booking overdue = booking(room(), TODAY.minusDays(5), TODAY.minusDays(3), BookingStatus.CHECKED_IN, "Khach Qua Han Ba Dem");
        mockMvc.perform(post("/admin/bookings/{id}/extend", overdue.getId()).param("to", TODAY.minusDays(1).toString()).with(csrf()).with(user(staff)))
                .andExpect(flash().attribute("errorMessage", containsString("ít nhất là hôm nay")));

        Booking confirmed = booking(room(), TODAY.plusDays(3), TODAY.plusDays(4), BookingStatus.CONFIRMED, "Khach Chua Den");
        mockMvc.perform(get("/admin/bookings/{id}/extend", confirmed.getId()).with(user(staff)))
                .andExpect(redirectedUrl("/admin/bookings/" + confirmed.getId()));
    }
}
