package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.LoyaltyService;
import com.hotel.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Thuong diem khi viet danh gia: chi cham sao 5, co nhan xet >= 20 ky tu 20, moi anh +10 (toi da 3), co video +30;
// quan tri an danh gia -> thu hoi, hien lai -> cong lai; tai khoan tam khach vang lai khong duoc thuong
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewRewardIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PointTransactionRepository transactionRepository;
    @Autowired private ReviewService reviewService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private User customer;

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Reward Suite").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder().roomNumber("RW-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        customer = userRepository.save(User.builder().fullName("Khach Thuong Diem").email("thuong.diem@mail.com")
                .phoneNumber("0977000111").password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER)
                .customerType(CustomerType.NEW).loyaltyPoints(100).build());
    }

    @AfterEach
    void cleanFiles() throws Exception {
        Path dir = Path.of("target/test-uploads/reviews");
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                files.forEach(f -> f.toFile().delete());
            }
        }
    }

    private Booking checkedOut(User who) {
        LocalDate in = LocalDate.now().minusDays(4);
        return bookingRepository.save(Booking.builder().customer(who).room(room).checkInDate(in).checkOutDate(in.plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).guestName(who.getFullName())
                .guestPhone(who.getPhoneNumber()).guestEmail(who.getEmail()).totalAmount(BigDecimal.valueOf(1000000))
                .discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("media", name, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }

    private static MockMultipartFile video(String name) {
        return new MockMultipartFile("media", name, "video/mp4", new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'});
    }

    private int balance() {
        return userRepository.findById(customer.getId()).orElseThrow().getPointsBalance();
    }

    @Test
    void pointsRule() {
        assertThat(LoyaltyService.reviewPoints(null, 0, 0)).isEqualTo(5);
        assertThat(LoyaltyService.reviewPoints("Tốt", 0, 0)).isEqualTo(5);
        assertThat(LoyaltyService.reviewPoints("Phòng sạch, view biển rất đẹp", 0, 0)).isEqualTo(20);
        assertThat(LoyaltyService.reviewPoints("Phòng sạch, view biển rất đẹp", 2, 0)).isEqualTo(40);
        assertThat(LoyaltyService.reviewPoints("Phòng sạch, view biển rất đẹp", 5, 2)).isEqualTo(20 + 30 + 30);
        assertThat(LoyaltyService.reviewPoints("", 0, 1)).isEqualTo(35);
    }

    @Test
    void reviewWithPhotosAndVideo_earnsMore_flashSaysPoints_thenHideRevokesAndUnhideRestores() throws Exception {
        Booking b = checkedOut(customer);
        mockMvc.perform(multipart("/customer/bookings/{id}/review", b.getId()).file(image("a.jpg")).file(image("b.jpg")).file(video("c.mp4"))
                        .with(csrf()).with(user(new CustomUserDetails(customer)))
                        .param("rating", "5").param("comment", "Phòng sạch, view biển rất đẹp, nhân viên dễ thương"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("reviewSuccess", containsString("70 điểm thưởng")));
        Review r = reviewRepository.findByBookingId(b.getId()).orElseThrow();
        assertThat(r.getRewardPoints()).isEqualTo(70);
        assertThat(balance()).isEqualTo(170);
        assertThat(transactionRepository.findRecent(customer.getId(), org.springframework.data.domain.Pageable.unpaged()))
                .anyMatch(t -> t.getType() == PointTransaction.Type.REVIEW && t.getPoints() == 70);

        // Trang don cua khach hien da nhan diem
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(user(new CustomUserDetails(customer))))
                .andExpect(content().string(containsString("Bạn đã nhận 70 điểm thưởng")));

        reviewService.toggleHidden(r.getId());
        assertThat(balance()).isEqualTo(100);
        assertThat(reviewRepository.findById(r.getId()).orElseThrow().getRewardPoints()).isZero();
        reviewService.toggleHidden(r.getId());
        assertThat(balance()).isEqualTo(170);
    }

    @Test
    void starsOnly_earnsBasePoints_formShowsRewardTable() throws Exception {
        Booking b = checkedOut(customer);
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(user(new CustomUserDetails(customer))))
                .andExpect(content().string(containsString("Viết đánh giá nhận điểm thưởng")))
                .andExpect(content().string(containsString("reviewRewardPoints")));
        mockMvc.perform(multipart("/customer/bookings/{id}/review", b.getId()).with(csrf()).with(user(new CustomUserDetails(customer)))
                .param("rating", "4"));
        assertThat(balance()).isEqualTo(105);
    }

    @Test
    void walkInPlaceholder_getsNoPoints() {
        User walkIn = userRepository.save(User.builder().fullName("Khach Quay").email("abc123@khachvanglai.local")
                .phoneNumber("0977000222").password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).build());
        Booking b = checkedOut(walkIn);
        Review r = reviewService.createReview(b.getId(), walkIn.getId(), 5, "Phòng sạch, view biển rất đẹp, sẽ quay lại");
        assertThat(r.getRewardPoints()).isNull();
        assertThat(userRepository.findById(walkIn.getId()).orElseThrow().getPointsBalance()).isZero();
    }
}
