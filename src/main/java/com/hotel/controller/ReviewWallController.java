package com.hotel.controller;

import com.hotel.entity.Review;
import com.hotel.entity.ReviewMedia;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ReviewSocialService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Texts;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

// Trang cong khai "Khach noi gi": tuong danh gia (loc co anh / video, so sao, moi nhat / nhieu tim nhat),
// trang 1 danh gia (link chia se) voi binh luan. Tha tim / binh luan can dang nhap tai khoan khach (SecurityConfig)
@Controller
@RequestMapping("/reviews")
public class ReviewWallController {

    static final int PAGE_SIZE = 12;

    private final ReviewRepository reviewRepository;
    private final ReviewSocialService socialService;

    public ReviewWallController(ReviewRepository reviewRepository, ReviewSocialService socialService) {
        this.reviewRepository = reviewRepository;
        this.socialService = socialService;
    }

    @GetMapping
    public String wall(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "new") String sort,
                       @RequestParam(required = false) Integer rating, @RequestParam(defaultValue = "1") int page,
                       @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        int media = switch (filter) {
            case "media" -> 1;
            case "video" -> 2;
            default -> 0;
        };
        Integer star = rating != null && rating >= 1 && rating <= 5 ? rating : null;
        boolean top = "top".equals(sort);
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), PAGE_SIZE);
        Page<Review> result = top ? reviewRepository.wallTop(star, media, pr) : reviewRepository.wallNewest(star, media, pr);
        if (page > 1 && result.getContent().isEmpty() && result.getTotalPages() > 0) {
            result = top ? reviewRepository.wallTop(star, media, PageRequest.of(result.getTotalPages() - 1, PAGE_SIZE))
                    : reviewRepository.wallNewest(star, media, PageRequest.of(result.getTotalPages() - 1, PAGE_SIZE));
        }
        model.addAttribute("reviews", result.getContent());
        model.addAttribute("reviewSocial", socialService.stats(result.getContent(), user(principal)));
        model.addAttribute("filter", media == 1 ? "media" : media == 2 ? "video" : "all");
        model.addAttribute("sort", top ? "top" : "new");
        model.addAttribute("rating", star);
        model.addAttribute("currentPage", result.getNumber() + 1);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(result.getNumber() + 1, Math.max(1, result.getTotalPages())));
        model.addAttribute("countAll", reviewRepository.wallNewest(null, 0, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("countMedia", reviewRepository.wallNewest(null, 1, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("countVideo", reviewRepository.wallNewest(null, 2, PageRequest.of(0, 1)).getTotalElements());
        model.addAttribute("anonymous", principal == null);
        return "reviews/wall";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Review review;
        try {
            review = socialService.visibleReview(id);
        } catch (BusinessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        User viewer = user(principal);
        model.addAttribute("review", review);
        model.addAttribute("reviewSocial", Map.of(review.getId(), socialService.stats(review, viewer)));
        model.addAttribute("comments", socialService.visibleComments(id));
        model.addAttribute("viewerId", viewer == null ? null : viewer.getId());
        model.addAttribute("canComment", principal != null && principal.getUser().getRole() == com.hotel.entity.UserRole.CUSTOMER);
        model.addAttribute("anonymous", principal == null);
        model.addAttribute("commentMax", ReviewSocialService.COMMENT_MAX);
        ReviewMedia cover = review.getMedia().stream().filter(m -> !m.isVideo()).findFirst().orElse(null);
        model.addAttribute("ogImage", cover == null ? null : cover.getUrl());
        ReviewMedia video = review.getMedia().stream().filter(ReviewMedia::isVideo).findFirst().orElse(null);
        model.addAttribute("ogVideo", video == null ? null : video.getUrl());
        return "reviews/detail";
    }

    // Tha / bo tim (JSON cho nut tim o trang va trong trinh xem anh)
    @PostMapping("/{id}/like")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> like(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("login", "/login"));
        }
        try {
            ReviewSocialService.Stats s = socialService.toggleLike(id, principal.getUser());
            return ResponseEntity.ok(Map.of("likes", s.likes(), "comments", s.comments(), "liked", s.liked()));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found"));
        }
    }

    // Dem luot chia se (ai cung chia se duoc, ke ca chua dang nhap)
    @PostMapping("/{id}/share")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> share(@PathVariable Long id, @RequestParam(required = false) String channel) {
        try {
            socialService.share(id, channel);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("ok", false));
        }
    }

    @PostMapping("/{id}/comments")
    public String comment(@PathVariable Long id, @RequestParam(required = false) String content,
                          @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        try {
            socialService.comment(id, principal.getUser(), content);
            ra.addFlashAttribute("successMessage", Texts.get(LocaleContextHolder.getLocale(), "reviews.commentPosted"));
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.render((code, args) -> Texts.get(LocaleContextHolder.getLocale(), code, args)));
            ra.addFlashAttribute("draft", content);
        }
        return "redirect:/reviews/" + id + "#binh-luan";
    }

    @PostMapping("/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails principal,
                                RedirectAttributes ra) {
        Long reviewId = socialService.deleteOwnComment(commentId, principal.getUser());
        ra.addFlashAttribute("successMessage", Texts.get(LocaleContextHolder.getLocale(), "reviews.commentDeleted"));
        return "redirect:/reviews/" + reviewId + "#binh-luan";
    }

    private static User user(CustomUserDetails principal) {
        return principal == null ? null : principal.getUser();
    }
}
