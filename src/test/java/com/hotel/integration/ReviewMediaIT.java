package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Danh gia kem anh / video: toi da 5 tep, anh <= 5MB, video <= 30MB, dung dinh dang; hien o trang don va trang phong
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewMediaIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private Booking booking;
    private CustomUserDetails me;

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Media Suite").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder().roomNumber("MD-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        User customer = userRepository.save(User.builder().fullName("Media Guest").email("media.guest@mail.com")
                .phoneNumber("0944444444").password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER)
                .customerType(CustomerType.NEW).build());
        me = new CustomUserDetails(customer);
        LocalDate in = LocalDate.now().minusDays(5);
        booking = bookingRepository.save(Booking.builder().customer(customer).room(room).checkInDate(in).checkOutDate(in.plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).guestName("Media Guest")
                .guestPhone("0944444444").guestEmail("media.guest@mail.com").totalAmount(BigDecimal.valueOf(1000000))
                .discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("media", name, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }

    private static MockMultipartFile video(String name) {
        return new MockMultipartFile("media", name, "video/mp4", new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'});
    }

    private MockMultipartHttpServletRequestBuilder reviewRequest() {
        MockMultipartHttpServletRequestBuilder req = multipart("/customer/bookings/{id}/review", booking.getId());
        req.with(csrf()).with(user(me)).param("rating", "5").param("comment", "Phòng đẹp");
        return req;
    }

    @Test
    void reviewWithImagesAndVideo_savedInOrder_filesOnDisk() throws Exception {
        mockMvc.perform(reviewRequest().file(image("a.jpg")).file(video("clip.mp4")).file(image("b.png")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("reviewSuccess"));

        Review review = reviewRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(review.getMedia()).extracting(ReviewMedia::getMediaType)
                .containsExactly(MediaType.IMAGE, MediaType.VIDEO, MediaType.IMAGE);
        for (ReviewMedia m : review.getMedia()) {
            assertThat(m.getUrl()).startsWith("/uploads/reviews/");
            Path file = Path.of("target/test-uploads", m.getUrl().substring("/uploads/".length()));
            assertThat(Files.exists(file)).as(file.toString()).isTrue();
            Files.deleteIfExists(file);
        }
    }

    @Test
    void sixFiles_rejected_noReviewSaved() throws Exception {
        MockMultipartHttpServletRequestBuilder req = reviewRequest();
        for (int i = 0; i < 6; i++) req.file(image("p" + i + ".jpg"));
        mockMvc.perform(req)
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Mỗi đánh giá chỉ được đính kèm tối đa 5 ảnh hoặc video"));
        assertThat(reviewRepository.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    void unsupportedType_rejected() throws Exception {
        mockMvc.perform(reviewRequest().file(new MockMultipartFile("media", "virus.exe", "application/x-msdownload", new byte[]{1})))
                .andExpect(flash().attribute("errorMessage", "Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc video MP4, WEBM, MOV"));
        // Doi duoi nhung noi dung khong phai video cung bi chan
        mockMvc.perform(reviewRequest().file(new MockMultipartFile("media", "fake.mp4", "text/html", new byte[]{1})))
                .andExpect(flash().attribute("errorMessage", "Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc video MP4, WEBM, MOV"));
        assertThat(reviewRepository.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    void imageOver5MB_rejected_andEarlierFilesCleanedUp() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        long before = countUploaded();
        mockMvc.perform(reviewRequest().file(image("ok.jpg")).file(new MockMultipartFile("media", "big.jpg", "image/jpeg", big)))
                .andExpect(flash().attribute("errorMessage", "Mỗi ảnh tối đa 5MB"));
        assertThat(reviewRepository.findByBookingId(booking.getId())).isEmpty();
        assertThat(countUploaded()).as("anh hop le luu truoc do phai bi xoa").isEqualTo(before);
    }

    @Test
    void errorsAreTranslated_inEnglish() throws Exception {
        MockMultipartHttpServletRequestBuilder req = reviewRequest();
        for (int i = 0; i < 6; i++) req.file(image("p" + i + ".jpg"));
        mockMvc.perform(req.param("lang", "en"))
                .andExpect(flash().attribute("errorMessage", "Each review can include at most 5 photos or videos"));
    }

    @Test
    void mediaShownOnBookingAndRoomPages_formRendersInBothLanguages() throws Exception {
        Review review = Review.builder().booking(booking).customer(booking.getCustomer()).rating(4).comment("Tốt").build();
        review.getMedia().add(ReviewMedia.builder().review(review).url("/uploads/reviews/x.jpg").mediaType(MediaType.IMAGE).sortOrder(0).build());
        review.getMedia().add(ReviewMedia.builder().review(review).url("/uploads/reviews/y.mp4").mediaType(MediaType.VIDEO).sortOrder(1).build());
        reviewRepository.save(review);

        String bookingPage = mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(bookingPage).contains("src=\"/uploads/reviews/x.jpg\"").contains("<video src=\"/uploads/reviews/y.mp4\"");

        String roomPage = mockMvc.perform(get("/customer/rooms/{id}", room.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(roomPage).contains("/uploads/reviews/x.jpg").contains("/uploads/reviews/y.mp4");
    }

    @Test
    void reviewForm_rendersUploadControls() throws Exception {
        for (String lang : new String[]{"vi", "en"}) {
            String page = mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).with(user(me)).param("lang", lang))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(page).contains("enctype=\"multipart/form-data\"").contains("id=\"reviewMedia\"")
                    .contains("var MAX_FILES = 5;").contains("var MAX_VIDEO_MB = 30;");
            assertThat(page).contains(lang.equals("en") ? "Add photos or videos (optional, up to 5 files)" : "Thêm ảnh hoặc video (không bắt buộc, tối đa 5 tệp)");
        }
    }

    private long countUploaded() throws Exception {
        Path dir = Path.of("target/test-uploads/reviews");
        if (!Files.exists(dir)) return 0;
        try (var s = Files.list(dir)) {
            return s.count();
        }
    }
}
