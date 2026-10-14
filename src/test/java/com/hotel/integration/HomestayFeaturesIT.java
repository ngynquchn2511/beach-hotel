package com.hotel.integration;

import com.hotel.config.BookingExpirationScheduler;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gia cuoi tuan / ngay le, dat coc va khai bao luu tru - chay qua controller that tren H2.
// @Transactional: moi test tu rollback, cai dat gia khong anh huong cac test khac.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomestayFeaturesIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookingExpirationScheduler expirationScheduler;

    private Room room;
    private User admin;
    private User staff;

    // Dem thu 6 + dem thu 7 cua tuan sau
    private static final LocalDate FRIDAY = LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder()
                .name("Phong HomestayIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(4).build());
        room = roomRepository.save(Room.builder().roomNumber("HS-" + System.nanoTime() % 100000)
                .roomType(rt).price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        admin = userRepository.findByEmail("admin@hotel.com").orElseThrow();
        staff = userRepository.findByEmail("staff@hotel.com").orElseThrow();
    }

    private void adminSetsPricing(int weekend, int deposit, int hours) throws Exception {
        mockMvc.perform(post("/admin/pricing/settings").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("weekendSurchargePercent", String.valueOf(weekend))
                        .param("depositPercent", String.valueOf(deposit))
                        .param("depositDeadlineHours", String.valueOf(hours)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    private Booking guestBooksOnline(LocalDate in, LocalDate out, String email) throws Exception {
        mockMvc.perform(post("/customer/bookings/new").with(csrf())
                        .param("roomId", room.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", out.toString())
                        .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                        .param("guestName", "Trần Thị Mai").param("guestPhone", "0909001122").param("guestEmail", email))
                .andExpect(status().is3xxRedirection());
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoom().getId().equals(room.getId()) && email.equals(b.getGuestEmail()))
                .findFirst().orElseThrow();
    }

    @Test
    void pageImages_servedFromProjectWithoutExternalFolder() throws Exception {
        // Moi truong test tro app.page-images.dir toi thu muc khong ton tai -> phai lay anh di kem project
        mockMvc.perform(get("/page-images/beach-thap-ba.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
        mockMvc.perform(get("/page-images/trangchu.jpg"))
                .andExpect(status().isOk());
    }

    @Test
    void roomDetail_pendingBooking_explainsAndSuggestsFreeDates() throws Exception {
        guestBooksOnline(FRIDAY, FRIDAY.plusDays(2), "hold.it@mail.com");

        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", FRIDAY.plusDays(1).toString()).param("checkOut", FRIDAY.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("available", false))
                .andExpect(model().attribute("pendingDates", org.hamcrest.Matchers.hasItems(FRIDAY.toString(), FRIDAY.plusDays(1).toString())))
                .andExpect(content().string(containsString("đơn chờ xác nhận")))
                .andExpect(content().string(containsString("Phòng trống trở lại từ 12:00")))
                // Goi y dat tu ngay phong trong, giu nguyen 2 dem
                .andExpect(model().attribute("suggestCheckIn", FRIDAY.plusDays(2)))
                .andExpect(model().attribute("suggestCheckOut", FRIDAY.plusDays(4)));

        // Ngay doi khach: nhan phong dung ngay khach truoc tra thi duoc, kem gio nhan som nhat
        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", FRIDAY.plusDays(2).toString()).param("checkOut", FRIDAY.plusDays(3).toString()))
                .andExpect(model().attribute("available", true))
                .andExpect(content().string(containsString("bạn nhận phòng được từ giờ này")));
    }

    @Test
    void roomDetail_maintenanceRoom_saysMaintenanceNotBooked() throws Exception {
        room.setStatus(RoomStatus.MAINTENANCE);
        roomRepository.save(room);
        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", FRIDAY.toString()).param("checkOut", FRIDAY.plusDays(1).toString()))
                .andExpect(model().attribute("available", false))
                .andExpect(content().string(containsString("Phòng đang bảo trì")));
    }

    @Test
    void zaloButton_shownOnCustomerPages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://zalo.me/0337196258")))
                .andExpect(content().string(containsString("Nhắn Zalo")));
        mockMvc.perform(get("/about"))
                .andExpect(content().string(containsString("0337 196 258")));
    }

    @Test
    void pricingPage_adminOnly() throws Exception {
        mockMvc.perform(get("/admin/pricing").with(user(new CustomUserDetails(staff))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/pricing/settings").with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("weekendSurchargePercent", "90").param("depositPercent", "90").param("depositDeadlineHours", "1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/pricing").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/pricing/index"));
    }

    @Test
    void weekendAndHolidaySurcharge_appliedPerNight() throws Exception {
        adminSetsPricing(40, 0, 24);
        // Dem thu 7 la ngay le +100% (uu tien hon cuoi tuan)
        mockMvc.perform(post("/admin/pricing/special-rates").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("name", "Lễ IT").param("startDate", FRIDAY.plusDays(1).toString())
                        .param("endDate", FRIDAY.plusDays(1).toString()).param("surchargePercent", "100"))
                .andExpect(flash().attributeExists("successMessage"));

        // Thu 5 (thuong 500k) + thu 6 (+40% = 700k) + thu 7 (le +100% = 1.000k) = 2.200.000
        mockMvc.perform(get("/customer/bookings/new").param("roomId", room.getId().toString())
                        .param("checkIn", FRIDAY.minusDays(1).toString()).param("checkOut", FRIDAY.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cuối tuần (+40%)")))
                .andExpect(content().string(containsString("Lễ IT (+100%)")));

        Booking b = guestBooksOnline(FRIDAY.minusDays(1), FRIDAY.plusDays(2), "weekend.it@mail.com");
        assertThat(b.getRoomAmount()).isEqualByComparingTo("2200000");
        assertThat(b.getTotalAmount()).isEqualByComparingTo("2200000");
        assertThat(b.isDepositRequired()).isFalse();
    }

    @Test
    void depositFlow_bookConfirmDepositDeclareCheckInCheckOut() throws Exception {
        adminSetsPricing(0, 30, 24);
        Booking b = guestBooksOnline(FRIDAY, FRIDAY.plusDays(2), "deposit.it@mail.com");

        // 2 dem x 500k = 1.000.000 -> coc 30% = 300.000, han ~24h
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.getDepositAmount()).isEqualByComparingTo("300000");
        assertThat(b.getDepositDeadline()).isBetween(LocalDateTime.now().plusHours(23), LocalDateTime.now().plusHours(25));

        // Khach thay QR chuyen coc voi noi dung "HB<id> COC"
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).param("token", b.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chuyển cọc để giữ phòng")))
                .andExpect(content().string(containsString("HB" + b.getId() + " COC")))
                .andExpect(content().string(containsString("img.vietqr.io")));

        // Nhan vien xac nhan da nhan coc -> don CONFIRMED
        mockMvc.perform(post("/admin/bookings/{id}/confirm-deposit", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attributeExists("successMessage"));
        Booking confirmed = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.getDepositPaidAt()).isNotNull();

        // Chua khai bao luu tru -> khong cho check-in
        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attribute("errorMessage", containsString("khai báo lưu trú")));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // CCCD sai dinh dang bi tu choi
        mockMvc.perform(post("/admin/bookings/{id}/guests", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("fullName", "Trần Thị Mai").param("dateOfBirth", "1992-03-08").param("gender", "FEMALE")
                        .param("idType", "CCCD").param("idNumber", "12345").param("nationality", "Việt Nam"))
                .andExpect(flash().attribute("errorMessage", containsString("12 chữ số")));

        mockMvc.perform(post("/admin/bookings/{id}/guests", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("fullName", "Trần Thị Mai").param("dateOfBirth", "1992-03-08").param("gender", "FEMALE")
                        .param("idType", "CCCD").param("idNumber", "001192008888").param("nationality", "Việt Nam")
                        .param("address", "Hà Nội"))
                .andExpect(flash().attributeExists("successMessage"));

        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.nullValue()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHECKED_IN);

        // Trang tra phong: con phai thu = 1.000.000 - 300.000
        mockMvc.perform(get("/admin/bookings/{id}/check-out", b.getId()).with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("amountDue", org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("700000"))))
                .andExpect(content().string(containsString("Đã đặt cọc")));

        mockMvc.perform(post("/admin/bookings/{id}/check-out", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("paymentMethod", "CASH"))
                .andExpect(flash().attributeExists("successMessage"));
        Payment payment = paymentRepository.findByBookingId(b.getId()).orElseThrow();
        assertThat(payment.getAmount()).isEqualByComparingTo("700000");

        // Hoa don the hien tong gia tri, tien coc, so thu khi tra phong
        mockMvc.perform(get("/admin/bookings/{id}/invoice", b.getId()).with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thanh toán khi trả phòng")))
                .andExpect(content().string(containsString("-300.000")));
    }

    @Test
    void unpaidDeposit_pastDeadline_autoCancelled() throws Exception {
        adminSetsPricing(0, 50, 24);
        Booking b = guestBooksOnline(FRIDAY, FRIDAY.plusDays(1), "late.it@mail.com");
        b.setDepositDeadline(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(b);

        expirationScheduler.expireUnconfirmedBookings();

        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void walkInBooking_neverRequiresDeposit() throws Exception {
        adminSetsPricing(0, 30, 24);
        mockMvc.perform(post("/admin/bookings/walk-in/new").with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("guestName", "Khách Tại Quầy").param("guestEmail", "walkin.hs@mail.com").param("guestPhone", "0900777888")
                        .param("roomId", room.getId().toString())
                        .param("checkIn", FRIDAY.toString()).param("checkOut", FRIDAY.plusDays(1).toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00"))
                .andExpect(status().is3xxRedirection());
        Booking b = bookingRepository.findAll().stream().filter(x -> "walkin.hs@mail.com".equals(x.getGuestEmail())).findFirst().orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.isDepositRequired()).isFalse();
    }

    @Test
    void guestRegistrationList_showsCheckedInGuests_andExportsExcel() throws Exception {
        User customer = userRepository.save(User.builder().fullName("Khách IT").email("reg.it@mail.com").phoneNumber("0900111222")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        Booking b = bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(LocalDate.now()).checkOutDate(LocalDate.now().plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khách IT").guestPhone("0900111222").guestEmail("reg.it@mail.com")
                .totalAmount(BigDecimal.valueOf(1000000)).discountAmount(BigDecimal.ZERO).status(BookingStatus.CONFIRMED).build());
        mockMvc.perform(post("/admin/bookings/{id}/guests", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("fullName", "John Smith").param("dateOfBirth", "1988-11-02").param("gender", "MALE")
                        .param("idType", "PASSPORT").param("idNumber", "x9876543").param("nationality", "Anh"))
                .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(post("/admin/bookings/{id}/check-in", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))));

        mockMvc.perform(get("/admin/guest-registrations").with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("John Smith")))
                .andExpect(content().string(containsString("X9876543")));

        byte[] xlsx = mockMvc.perform(get("/admin/guest-registrations/export").with(user(new CustomUserDetails(staff))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("khai-bao-luu-tru_")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx.length).isGreaterThan(1000);
        // File .xlsx la file zip, bat dau bang "PK"
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
    }

    @Test
    void refundFlow_cancelAfterDeposit_staffConfirmsRefund() throws Exception {
        // Chinh sach mac dinh: huy truoc >= 72h hoan 100% coc; FRIDAY cach >= 7 ngay
        adminSetsPricing(0, 30, 24);
        Booking b = guestBooksOnline(FRIDAY, FRIDAY.plusDays(2), "refund.it@mail.com");
        mockMvc.perform(post("/admin/bookings/{id}/confirm-deposit", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attributeExists("successMessage"));

        // Truoc khi huy: chu don (da dang nhap) va nhan vien deu thay tien hoan du kien
        User owner = userRepository.findById(b.getCustomer().getId()).orElseThrow();
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(user(new CustomUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nếu hủy bây giờ, bạn được hoàn <b>300.000 VND</b> (100% tiền cọc)")));
        mockMvc.perform(get("/admin/bookings/{id}", b.getId()).with(user(new CustomUserDetails(staff))))
                .andExpect(content().string(containsString("Hủy bây giờ theo chính sách")));

        mockMvc.perform(post("/admin/bookings/{id}/cancel", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attribute("successMessage", containsString("100% cọc")));
        Booking cancelled = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getRefundPercent()).isEqualTo(100);
        assertThat(cancelled.getRefundAmount()).isEqualByComparingTo("300000");
        assertThat(cancelled.isRefundPending()).isTrue();

        // Hien o danh sach loc "cho hoan coc", dashboard va trang don cua khach
        mockMvc.perform(get("/admin/bookings").param("refund", "pending").with(user(new CustomUserDetails(staff))))
                .andExpect(content().string(containsString("#" + b.getId())))
                .andExpect(content().string(containsString("Chờ hoàn 300.000")));
        mockMvc.perform(get("/admin/dashboard").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đơn hủy chờ hoàn cọc")));
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).param("token", b.getAccessToken()))
                .andExpect(content().string(containsString("Bạn được hoàn")));

        // Nhan vien da chuyen tien -> ghi nhan, het cho
        mockMvc.perform(post("/admin/bookings/{id}/confirm-refund", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("refundNote", "CK VCB FT999"))
                .andExpect(flash().attributeExists("successMessage"));
        Booking refunded = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(refunded.getRefundedAt()).isNotNull();
        assertThat(refunded.getRefundNote()).isEqualTo("CK VCB FT999");
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).param("token", b.getAccessToken()))
                .andExpect(content().string(containsString("Resort đã hoàn")));

        // Bam lan nua -> bao loi, khong ghi de
        mockMvc.perform(post("/admin/bookings/{id}/confirm-refund", b.getId()).with(csrf()).with(user(new CustomUserDetails(staff))))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void pricingSettings_saveRefundPolicy_andRejectInvalid() throws Exception {
        mockMvc.perform(post("/admin/pricing/settings").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("weekendSurchargePercent", "0").param("depositPercent", "30").param("depositDeadlineHours", "24")
                        .param("refundFullHours", "24").param("refundPartialHours", "48").param("refundPartialPercent", "50"))
                .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(post("/admin/pricing/settings").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("weekendSurchargePercent", "0").param("depositPercent", "30").param("depositDeadlineHours", "24")
                        .param("refundFullHours", "96").param("refundPartialHours", "48").param("refundPartialPercent", "30"))
                .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(get("/admin/pricing").with(user(new CustomUserDetails(admin))))
                .andExpect(content().string(containsString("Chính sách hủy &amp; hoàn cọc")))
                .andExpect(content().string(containsString("hoàn <b>30</b>%")));
    }
}
