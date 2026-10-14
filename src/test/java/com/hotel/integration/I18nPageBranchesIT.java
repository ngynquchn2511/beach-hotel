package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Cac nhanh giao dien chi hien khi co du lieu (da thanh toan, da danh gia, phu thu ngay le, cho coc, anh phong...)
// phai render duoc o ca 2 ngon ngu - loi cu phap #{...} chi lo ra khi nhanh do thuc su chay
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class I18nPageBranchesIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ComboRepository comboRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private SpecialRateRepository specialRateRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;
    @Autowired private PricingSettingsRepository pricingSettingsRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private Combo combo;
    private CustomUserDetails me;
    private Booking paidReviewed;
    private Booking awaitingDeposit;
    private Booking checkedOutNoReview;
    private Booking depositPaid;
    private LocalDate friday;

    @BeforeEach
    void seed() {
        PricingSettings settings = pricingSettingsRepository.findById(PricingSettings.SINGLETON_ID).orElseGet(PricingSettings::defaults);
        settings.setWeekendSurchargePercent(20);
        settings.setDepositPercent(30);
        settings.setDepositDeadlineHours(24);
        pricingSettingsRepository.save(settings);

        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Branch Suite").basePrice(BigDecimal.valueOf(500000))
                .maxGuests(2).area(BigDecimal.valueOf(30)).amenities("Wifi, TV").description("Mo ta").build());
        room = Room.builder().roomNumber("BR-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).description("Phong dep").build();
        room.getImages().add(RoomImage.builder().room(room).imageUrl("/uploads/x.jpg").build());
        room = roomRepository.save(room);
        combo = comboRepository.save(Combo.builder().name("BBQ").description("Do nuong").price(BigDecimal.valueOf(200000))
                .active(true).build());
        DiscountCode dc = discountCodeRepository.save(DiscountCode.builder().code("BR" + System.nanoTime() % 100000)
                .discountType(DiscountType.FIXED_AMOUNT).discountValue(BigDecimal.valueOf(50000)).active(true).build());

        User customer = userRepository.save(User.builder().fullName("Branch Guest").email("branch.guest@mail.com")
                .phoneNumber("0922222222").password(passwordEncoder.encode("secret1")).role(UserRole.CUSTOMER)
                .customerType(CustomerType.VIP).build());
        me = new CustomUserDetails(customer);

        friday = LocalDate.now().plusDays(30).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        specialRateRepository.save(SpecialRate.builder().name("Le").startDate(friday.plusDays(1)).endDate(friday.plusDays(1))
                .surchargePercent(50).build());

        LocalDate past = LocalDate.now().minusDays(10);
        paidReviewed = booking(customer, past, past.plusDays(2), BookingStatus.CHECKED_OUT, dc);
        paymentRepository.save(Payment.builder().booking(paidReviewed).amount(BigDecimal.valueOf(1000000))
                .paymentMethod(PaymentMethod.CASH).status(PaymentStatus.PAID).paymentDate(LocalDateTime.now()).build());
        reviewRepository.save(Review.builder().booking(paidReviewed).customer(customer).rating(5).comment("Tot")
                .hidden(true).createdAt(LocalDateTime.now()).build());
        Booking visible = booking(customer, past.minusDays(5), past.minusDays(3), BookingStatus.CHECKED_OUT, null);
        reviewRepository.save(Review.builder().booking(visible).customer(customer).rating(4).comment("On")
                .hidden(false).createdAt(LocalDateTime.now()).build());
        checkedOutNoReview = booking(customer, past.minusDays(8), past.minusDays(7), BookingStatus.CHECKED_OUT, null);

        awaitingDeposit = booking(customer, friday.plusDays(10), friday.plusDays(12), BookingStatus.PENDING, null);
        awaitingDeposit.setDepositAmount(BigDecimal.valueOf(300000));
        awaitingDeposit.setDepositDeadline(LocalDateTime.now().plusHours(5));
        bookingRepository.save(awaitingDeposit);

        // Don sat ngay de trang chi tiet phong hien "khach truoc tra phong luc..." va goi y ngay khac
        depositPaid = booking(customer, friday.minusDays(2), friday, BookingStatus.CONFIRMED, null);
        depositPaid.setDepositAmount(BigDecimal.valueOf(300000));
        depositPaid.setDepositPaidAt(LocalDateTime.now());
        bookingRepository.save(depositPaid);
    }

    private Booking booking(User customer, LocalDate in, LocalDate out, BookingStatus status, DiscountCode dc) {
        return bookingRepository.save(Booking.builder().customer(customer).room(room).combo(combo)
                .checkInDate(in).checkOutDate(out).checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .numberOfGuests(2).guestName("Branch Guest").guestPhone("0922222222").guestEmail("branch.guest@mail.com")
                .discountCode(dc).discountAmount(dc != null ? BigDecimal.valueOf(50000) : BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(1000000)).status(status).build());
    }

    private void assertRenders(MockHttpServletRequestBuilder request, String lang) throws Exception {
        var resp = mockMvc.perform(request.param("lang", lang)).andReturn().getResponse();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentAsString(StandardCharsets.UTF_8)).doesNotContainPattern("\\?\\?[\\w.]+_(vi|en)\\w*\\?\\?");
    }

    @ParameterizedTest
    @ValueSource(strings = {"vi", "en"})
    void allDataDrivenBranches_render(String lang) throws Exception {
        String r = "/customer/rooms/" + room.getId();
        // Phong co anh + danh gia, ngay sat don khac (gio nhan/tra), ngay trung don (goi y), co chon combo
        assertRenders(get(r).param("checkIn", friday.toString()).param("checkOut", friday.plusDays(2).toString())
                .param("comboId", combo.getId().toString()), lang);
        assertRenders(get(r).param("checkIn", friday.minusDays(3).toString()).param("checkOut", friday.minusDays(1).toString()), lang);
        // Xac nhan dat: dem thuong + cuoi tuan + ngay le, uoc tinh tien coc, combo
        assertRenders(get("/customer/bookings/new").with(user(me)).param("roomId", room.getId().toString())
                .param("checkIn", friday.minusDays(1).toString()).param("checkOut", friday.plusDays(3).toString())
                .param("comboId", combo.getId().toString()), lang);
        // Chi tiet don: da thanh toan + ma giam gia + danh gia an / cho coc (QR) / duoc danh gia / da coc (sua gio, combo)
        for (Booking b : new Booking[]{paidReviewed, awaitingDeposit, checkedOutNoReview, depositPaid}) {
            assertRenders(get("/customer/bookings/" + b.getId()).with(user(me)), lang);
        }
        // Khach vang lai mo bang link co ma truy cap
        assertRenders(get("/customer/bookings/" + awaitingDeposit.getId()).param("token", awaitingDeposit.getAccessToken()), lang);
        assertRenders(get("/customer/bookings").with(user(me)), lang);
        assertRenders(get("/customer/account").with(user(me)), lang);
        assertRenders(get("/customer/rooms").param("checkIn", friday.plusDays(20).toString())
                .param("checkOut", friday.plusDays(22).toString()), lang);
    }
}
