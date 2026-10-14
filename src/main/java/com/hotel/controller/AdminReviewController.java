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

    @GetMapping
    public String list(@RequestParam(required = false) Integer rating,
                       @RequestParam(required = false) Boolean hidden,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        List<Review> all = reviewService.findAllForAdmin(rating, hidden, q);
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("reviews", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("totalElements", all.size());
        model.addAttribute("averageRating", all.isEmpty() ? 0
                : Math.round(all.stream().mapToInt(Review::getRating).average().orElse(0) * 10) / 10.0);
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        model.addAttribute("rating", rating);
        model.addAttribute("hidden", hidden);
        model.addAttribute("q", q);
        return "admin/reviews/list";
    }

    // Giu nguyen bo loc + trang hien tai sau khi an/hien
    @PostMapping("/{id}/toggle-hidden")
    public String toggleHidden(@PathVariable Long id,
                               @RequestParam(required = false) Integer rating,
                               @RequestParam(required = false) Boolean hidden,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "1") int page,
                               RedirectAttributes redirectAttributes) {
        try {
            Review review = reviewService.toggleHidden(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    review.isHidden() ? "Đã ẩn đánh giá của đơn #" + review.getBooking().getId()
                            : "Đã hiện lại đánh giá của đơn #" + review.getBooking().getId());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        String url = UriComponentsBuilder.fromPath("/admin/reviews")
                .queryParamIfPresent("rating", Optional.ofNullable(rating))
                .queryParamIfPresent("hidden", Optional.ofNullable(hidden))
                .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .queryParam("page", page)
                .encode().toUriString();
        return "redirect:" + url;
    }
}
