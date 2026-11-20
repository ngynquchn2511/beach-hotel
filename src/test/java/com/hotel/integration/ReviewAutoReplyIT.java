package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.ReviewReplyRuleRepository;
import com.hotel.repository.ReviewRepository;
import com.hotel.service.ReviewAutoReplyService;
import com.hotel.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Tu dong tra loi danh gia theo tu khoa: khop tu khoa (co dau / khong dau), khoang sao, quy tac du phong,
// tat / bat, nhan vien sua tay, trang quan ly quy tac + thu quy tac + ap dung cho danh gia cu
class ReviewAutoReplyIT extends ItFixtures {

    @Autowired ReviewAutoReplyService autoReply;
    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewReplyRuleRepository ruleRepository;

    @AfterEach
    void off() {
        autoReply.setEnabledForTest(false);
    }

    private String rule(int rating, String comment) {
        return autoReply.match(rating, comment, "An", "Deluxe").map(m -> m.rule().getName()).orElse(null);
    }

    @Test
    void keywordMatching_followsRatingAndAccents() {
        assertThat(ruleRepository.count()).isGreaterThanOrEqualTo(10); // bo quy tac mac dinh

        assertThat(rule(2, "Phòng ồn quá, karaoke tới khuya!")).isEqualTo("Tiếng ồn");
        assertThat(rule(2, "phong on ao qua")).isEqualTo("Tiếng ồn");          // go khong dau, tu khoa dai
        assertThat(rule(2, "We stayed on the top floor")).isEqualTo("Xin lỗi chung (1-3 sao)"); // "on" khong khop "ồn"
        assertThat(rule(1, "Phòng BẨN, có mùi ẩm mốc")).isEqualTo("Vệ sinh, mùi phòng");
        assertThat(rule(3, "Điều hòa hỏng cả đêm")).isEqualTo("Hỏng hóc, tiện nghi");
        assertThat(rule(5, "View biển tuyệt đẹp, hoàng hôn rất chill")).isEqualTo("Khen view, bãi biển");
        assertThat(rule(5, "Nhân viên lễ tân rất thân thiện")).isEqualTo("Khen nhân viên");
        assertThat(rule(4, "Ổn")).isEqualTo("Cảm ơn chung (4-5 sao)");
        assertThat(rule(5, null)).isEqualTo("Cảm ơn chung (4-5 sao)");

        String text = autoReply.match(5, "view đẹp", "Minh Anh", "Suite").orElseThrow().text();
        assertThat(text).contains("Minh Anh").contains("5 sao").doesNotContain("{");
    }

    @Test
    void newReviewIsAutoReplied_manualEditClearsAutoFlag_andDisabledDoesNothing() {
        Room room = persistRoom(persistRoomType("Hang Tra Loi IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Booking b1 = persistBooking(guest, room, BookingStatus.CHECKED_OUT, -10, 2);
        Booking b2 = persistBooking(guest, room, BookingStatus.CHECKED_OUT, -20, 2);

        autoReply.setEnabledForTest(true);
        Review r = reviewService.createReview(b1.getId(), guest.getId(), 2, "Đêm qua rất ồn, không ngủ được");
        Review saved = reviewRepository.findById(r.getId()).orElseThrow();
        assertThat(saved.isReplied()).isTrue();
        assertThat(saved.isAutoReplied()).isTrue();
        assertThat(saved.getAutoReplyRule()).isEqualTo("Tiếng ồn");
        assertThat(saved.getReply()).contains("tiếng ồn");

        reviewService.reply(saved.getId(), "Quản lý đã gọi điện xin lỗi khách.");
        assertThat(reviewRepository.findById(r.getId()).orElseThrow().isAutoReplied()).isFalse();

        autoReply.setEnabledForTest(false);
        Review r2 = reviewService.createReview(b2.getId(), guest.getId(), 5, "View biển đẹp");
        assertThat(reviewRepository.findById(r2.getId()).orElseThrow().isReplied()).isFalse();
    }

    @Test
    void adminPage_testBox_ruleCrud_applyToOld_andRoles() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(get("/admin/review-replies").with(as(admin))
                        .param("testRating", "2").param("testComment", "Phòng có gián"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vệ sinh, mùi phòng")))
                .andExpect(content().string(containsString("gián")));

        mockMvc.perform(post("/admin/review-replies").with(csrf()).with(as(admin))
                        .param("name", "Wifi IT").param("keywords", "wifi, mạng yếu").param("minRating", "1").param("maxRating", "3")
                        .param("body", "Xin lỗi {ten_khach} về wifi.").param("sortOrder", "0"))
                .andExpect(flash().attribute("successMessage", "Đã thêm quy tắc"));
        // Quy tac moi thu tu 0 -> dung truoc "Hỏng hóc" (cung co tu khoa wifi)
        assertThat(rule(2, "Wifi chập chờn")).isEqualTo("Wifi IT");

        mockMvc.perform(post("/admin/review-replies").with(csrf()).with(as(admin))
                        .param("name", "Sai").param("minRating", "4").param("maxRating", "2").param("body", "x"))
                .andExpect(flash().attributeExists("errorMessage"));

        // Danh gia cu chua tra loi -> ap dung quy tac
        Room room = persistRoom(persistRoomType("Hang Cu IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Review old = reviewService.createReview(persistBooking(guest, room, BookingStatus.CHECKED_OUT, -30, 1).getId(),
                guest.getId(), 5, "Đồ ăn ngon");
        assertThat(reviewRepository.findById(old.getId()).orElseThrow().isReplied()).isFalse();

        // Trang Danh gia goi y san cau tra loi cho danh gia chua tra loi
        mockMvc.perform(get("/admin/reviews").with(as(admin)).param("replied", "false"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gợi ý theo quy tắc")));

        mockMvc.perform(post("/admin/review-replies/apply").with(csrf()).with(as(admin)))
                .andExpect(flash().attribute("successMessage", containsString("Đã tự động trả lời")));
        Review applied = reviewRepository.findById(old.getId()).orElseThrow();
        assertThat(applied.getAutoReplyRule()).isEqualTo("Khen đồ ăn");

        mockMvc.perform(get("/admin/reviews").with(as(admin)))
                .andExpect(content().string(containsString("Tự động · Khen đồ ăn")));

        mockMvc.perform(get("/admin/review-replies").with(as(persistUser(UserRole.STAFF, null))))
                .andExpect(status().isForbidden());
    }
}
