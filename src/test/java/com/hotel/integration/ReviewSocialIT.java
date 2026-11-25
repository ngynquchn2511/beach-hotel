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
import java.nio.charset.StandardCharsets;
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

// Trang "Khach noi gi" + tha tim / binh luan / chia se danh gia + trinh xem anh / video
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewSocialIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewLikeRepository likeRepository;
    @Autowired private ReviewCommentRepository commentRepository;
    @Autowired private ReviewShareRepository shareRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room room;
    private Review photoReview;
    private Review videoReview;
    private User author;
    private User fan;
    private User admin;

    @BeforeEach
    void seed() {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Social Suite").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder().roomNumber("SO-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        author = customer("Tran Van Tac Gia", "tacgia.social@mail.com", "0955000001");
        fan = customer("Le Thi Ban Doc", "bandoc.social@mail.com", "0955000002");
        admin = userRepository.save(User.builder().fullName("Admin Social").email("admin.social@mail.com").phoneNumber("0955000003")
                .password(passwordEncoder.encode("x")).role(UserRole.ADMIN).build());
        photoReview = review(author, 5, "Bãi biển đẹp mê ly SOCIALTEST", MediaType.IMAGE, "/uploads/reviews/so-a.jpg", "/uploads/reviews/so-b.jpg");
        videoReview = review(author, 4, "Video hoàng hôn SOCIALVIDEO", MediaType.VIDEO, "/uploads/reviews/so-c.mp4");
    }

    private User customer(String name, String email, String phone) {
        return userRepository.save(User.builder().fullName(name).email(email).phoneNumber(phone)
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    private Review review(User who, int rating, String comment, MediaType type, String... urls) {
        LocalDate in = LocalDate.now().minusDays(6);
        Booking b = bookingRepository.save(Booking.builder().customer(who).room(room).checkInDate(in).checkOutDate(in.plusDays(1))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).guestName(who.getFullName())
                .guestPhone(who.getPhoneNumber()).guestEmail(who.getEmail()).totalAmount(BigDecimal.valueOf(500000))
                .discountAmount(BigDecimal.ZERO).status(BookingStatus.CHECKED_OUT).build());
        Review r = Review.builder().booking(b).customer(who).rating(rating).comment(comment).build();
        for (int i = 0; i < urls.length; i++) {
            r.getMedia().add(ReviewMedia.builder().review(r).url(urls[i]).mediaType(type).sortOrder(i).build());
        }
        return reviewRepository.save(r);
    }

    @Test
    void wall_isPublic_andFiltersVideo() throws Exception {
        mockMvc.perform(get("/reviews"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SOCIALTEST")))
                .andExpect(content().string(containsString("review-lightbox.js")))
                .andExpect(content().string(containsString("data-rv-type=\"video\"")));
        mockMvc.perform(get("/reviews").param("filter", "video"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SOCIALVIDEO")))
                .andExpect(content().string(not(containsString("SOCIALTEST"))));
    }

    @Test
    void detail_isPublic_withOpenGraph_hiddenIs404() throws Exception {
        mockMvc.perform(get("/reviews/" + photoReview.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("og:image")))
                .andExpect(content().string(containsString("/uploads/reviews/so-a.jpg")))
                .andExpect(content().string(containsString("binh-luan")));
        photoReview.setHidden(true);
        reviewRepository.save(photoReview);
        mockMvc.perform(get("/reviews/" + photoReview.getId())).andExpect(status().isNotFound());
    }

    @Test
    void like_togglesForCustomer_anonymousGoesToLogin() throws Exception {
        String body = mockMvc.perform(post("/reviews/" + photoReview.getId() + "/like").with(csrf()).with(user(new CustomUserDetails(fan))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"liked\":true").contains("\"likes\":1");
        assertThat(likeRepository.countByReviewId(photoReview.getId())).isEqualTo(1);

        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/like").with(csrf()).with(user(new CustomUserDetails(fan))))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likes").value(0));

        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/like").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(likeRepository.countByReviewId(photoReview.getId())).isZero();
    }

    @Test
    void comment_postShowDeleteOwn_validation() throws Exception {
        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/comments").with(csrf()).with(user(new CustomUserDetails(fan)))
                        .param("content", "Ảnh đẹp quá, tháng sau mình đi COMMENTX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
        mockMvc.perform(get("/reviews/" + photoReview.getId()))
                .andExpect(content().string(containsString("COMMENTX")))
                .andExpect(content().string(containsString("Ban Doc")));

        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/comments").with(csrf()).with(user(new CustomUserDetails(fan)))
                        .param("content", "   "))
                .andExpect(flash().attributeExists("errorMessage"));
        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/comments").with(csrf()).with(user(new CustomUserDetails(fan)))
                        .param("content", "x".repeat(501)))
                .andExpect(flash().attributeExists("errorMessage"));

        ReviewComment c = commentRepository.findByReviewIdOrderByCreatedAtAsc(photoReview.getId()).get(0);
        // Nguoi khac khong xoa duoc
        mockMvc.perform(post("/reviews/comments/" + c.getId() + "/delete").with(csrf()).with(user(new CustomUserDetails(author))));
        assertThat(commentRepository.findById(c.getId())).isPresent();
        mockMvc.perform(post("/reviews/comments/" + c.getId() + "/delete").with(csrf()).with(user(new CustomUserDetails(fan))))
                .andExpect(status().is3xxRedirection());
        assertThat(commentRepository.findById(c.getId())).isEmpty();
    }

    @Test
    void comment_requiresLogin() throws Exception {
        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/comments").with(csrf()).param("content", "hello"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(commentRepository.findByReviewIdOrderByCreatedAtAsc(photoReview.getId())).isEmpty();
    }

    @Test
    void share_countsEvenForAnonymous() throws Exception {
        mockMvc.perform(post("/reviews/" + videoReview.getId() + "/share").with(csrf()).param("channel", "facebook"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/reviews/" + videoReview.getId() + "/share").with(csrf()).param("channel", "bogus"))
                .andExpect(status().isOk());
        assertThat(shareRepository.countByReviewId(videoReview.getId())).isEqualTo(2);
        assertThat(shareRepository.findAll()).anyMatch(s -> "link".equals(s.getChannel()));
    }

    @Test
    void admin_seesEngagement_andHidesComment() throws Exception {
        mockMvc.perform(post("/reviews/" + photoReview.getId() + "/comments").with(csrf()).with(user(new CustomUserDetails(fan)))
                .param("content", "Spam quảng cáo HIDEME"));
        ReviewComment c = commentRepository.findByReviewIdOrderByCreatedAtAsc(photoReview.getId()).get(0);

        mockMvc.perform(get("/admin/reviews").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rvEngage")))
                .andExpect(content().string(containsString("HIDEME")));

        mockMvc.perform(post("/admin/reviews/comments/" + c.getId() + "/toggle-hidden").with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(status().is3xxRedirection());
        assertThat(commentRepository.findById(c.getId()).orElseThrow().isHidden()).isTrue();
        mockMvc.perform(get("/reviews/" + photoReview.getId()))
                .andExpect(content().string(not(containsString("HIDEME"))));
    }

    @Test
    void roomDetail_hasLightboxThumbsAndSocialBar() throws Exception {
        mockMvc.perform(get("/customer/rooms/" + room.getId()).with(user(new CustomUserDetails(fan))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("rv-thumb")))
                .andExpect(content().string(containsString("review-lightbox.js")))
                .andExpect(content().string(containsString("rv-like")))
                .andExpect(content().string(containsString("review-social.js")));
    }
}
