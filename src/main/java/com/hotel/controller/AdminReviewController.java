package com.hotel.controller;

import com.hotel.entity.Review;
import com.hotel.exception.BusinessException;
import com.hotel.service.ReviewService;
import com.hotel.util.PaginationUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

// Quan ly danh gia cua khach: xem, loc, an/hien danh gia khong phu hop
@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    private com.hotel.service.ReviewAutoReplyService autoReplyService;

    @org.springframework.beans.factory.annotation.Autowired
    void setAutoReplyService(com.hotel.service.ReviewAutoReplyService autoReplyService) {
        this.autoReplyService = autoReplyService;
    }

    // Tuong tac cua khach khac voi danh gia (tim / binh luan / chia se)
    private com.hotel.service.ReviewSocialService socialService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSocialService(com.hotel.service.ReviewSocialService socialService) {
        this.socialService = socialService;
    }

    // Quan tri an / hien binh luan khong phu hop
    @PostMapping("/comments/{commentId}/toggle-hidden")
    public String toggleComment(@PathVariable Long commentId, @RequestParam(required = false) String back,
                                RedirectAttributes ra) {
        var c = socialService.toggleCommentHidden(commentId);
        ra.addFlashAttribute("successMessage", c.isHidden() ? "Đã ẩn bình luận" : "Đã hiện lại bình luận");
        String to = back != null && back.startsWith("/admin/reviews") ? back : "/admin/reviews";
        return "redirect:" + to + "#tuong-tac";
    }

    @GetMapping
    public String list(@RequestParam(required = false) Integer rating,
                       @RequestParam(required = false) Boolean hidden,
                       @RequestParam(required = false) Boolean replied,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        List<Review> all = reviewService.findAllForAdmin(rating, hidden, replied, q);
        model.addAttribute("replied", replied);
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        List<Review> pageItems = PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("reviews", pageItems);
        // Goi y tra loi theo quy tac tu khoa cho danh gia chua tra loi - dien san vao o tra loi
        if (autoReplyService != null) {
            java.util.Map<Long, com.hotel.service.ReviewAutoReplyService.Match> suggestions = new java.util.HashMap<>();
            pageItems.stream().filter(r -> !r.isReplied())
                    .forEach(r -> autoReplyService.match(r).ifPresent(m -> suggestions.put(r.getId(), m)));
            model.addAttribute("suggestions", suggestions);
        }
        if (socialService != null) {
            model.addAttribute("reviewSocial", socialService.stats(pageItems, null));
            model.addAttribute("reviewComments", socialService.allComments(pageItems.stream().map(Review::getId).toList()));
            model.addAttribute("engagement", socialService.engagement(30, java.time.LocalDate.now()));
        }
        model.addAttribute("totalElements", all.size());
        model.addAttribute("averageRating", all.isEmpty() ? 0
                : Math.round(all.stream().mapToInt(Review::getRating).average().orElse(0) * 10) / 10.0);
        // Tong quan (khong theo bo loc): phan bo so sao, ty le da tra loi, diem TB 12 thang
        List<Review> everything = rating == null && hidden == null && replied == null && (q == null || q.isBlank())
                ? all : reviewService.findAllForAdmin(null, null, null, null);
        long[] stars = new long[6];
        everything.forEach(r -> { if (r.getRating() >= 1 && r.getRating() <= 5) stars[r.getRating()]++; });
        model.addAttribute("starCounts", List.of(stars[5], stars[4], stars[3], stars[2], stars[1]));
        model.addAttribute("overviewTotal", everything.size());
        model.addAttribute("overviewAverage", everything.isEmpty() ? 0
                : Math.round(everything.stream().mapToInt(Review::getRating).average().orElse(0) * 10) / 10.0);
        model.addAttribute("repliedCount", everything.stream().filter(Review::isReplied).count());
        java.time.YearMonth now = java.time.YearMonth.now();
        List<String> trendMonths = new java.util.ArrayList<>();
        List<Double> trendAvg = new java.util.ArrayList<>();
        List<Long> trendCount = new java.util.ArrayList<>();
        for (int k = 11; k >= 0; k--) {
            java.time.YearMonth ym = now.minusMonths(k);
            List<Review> ofMonth = everything.stream().filter(r -> r.getCreatedAt() != null
                    && java.time.YearMonth.from(r.getCreatedAt()).equals(ym)).toList();
            trendMonths.add(ym.getMonthValue() + "/" + (ym.getYear() % 100));
            trendCount.add((long) ofMonth.size());
            trendAvg.add(ofMonth.isEmpty() ? null : Math.round(ofMonth.stream().mapToInt(Review::getRating).average().orElse(0) * 100) / 100.0);
        }
        model.addAttribute("trendMonths", trendMonths);
        model.addAttribute("trendAvg", trendAvg);
        model.addAttribute("trendCount", trendCount);
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        model.addAttribute("rating", rating);
        model.addAttribute("hidden", hidden);
        model.addAttribute("q", q);
        return "admin/reviews/list";
    }

    // Tra loi cong khai (de trong = go cau tra loi). Giu nguyen bo loc + trang hien tai
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id,
                        @RequestParam(required = false) String reply,
                        @RequestParam(required = false) Integer rating,
                        @RequestParam(required = false) Boolean hidden,
                        @RequestParam(required = false) Boolean replied,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "1") int page,
                        RedirectAttributes redirectAttributes) {
        try {
            Review review = reviewService.reply(id, reply);
            redirectAttributes.addFlashAttribute("successMessage", review.isReplied()
                    ? "Đã lưu câu trả lời cho đánh giá của đơn #" + review.getBooking().getCode()
                    : "Đã gỡ câu trả lời của đánh giá đơn #" + review.getBooking().getCode());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + listUrl(rating, hidden, replied, q, page) + "#review-" + id;
    }

    private static String listUrl(Integer rating, Boolean hidden, Boolean replied, String q, int page) {
        return UriComponentsBuilder.fromPath("/admin/reviews")
                .queryParamIfPresent("rating", Optional.ofNullable(rating))
                .queryParamIfPresent("hidden", Optional.ofNullable(hidden))
                .queryParamIfPresent("replied", Optional.ofNullable(replied))
                .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .queryParam("page", page)
                .encode().toUriString();
    }

    // Giu nguyen bo loc + trang hien tai sau khi an/hien
    @PostMapping("/{id}/toggle-hidden")
    public String toggleHidden(@PathVariable Long id,
                               @RequestParam(required = false) Integer rating,
                               @RequestParam(required = false) Boolean hidden,
                               @RequestParam(required = false) Boolean replied,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "1") int page,
                               RedirectAttributes redirectAttributes) {
        try {
            Review review = reviewService.toggleHidden(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    review.isHidden() ? "Đã ẩn đánh giá của đơn #" + review.getBooking().getCode()
                            : "Đã hiện lại đánh giá của đơn #" + review.getBooking().getCode());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + listUrl(rating, hidden, replied, q, page);
    }
}
