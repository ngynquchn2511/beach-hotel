package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.ReviewAutoReplyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Quy tac tu dong tra loi danh gia theo tu khoa (chi ADMIN - SecurityConfig): them / sua / tat / xoa,
// thu 1 noi dung danh gia xem khop quy tac nao, ap dung cho cac danh gia cu chua tra loi
@Controller
@RequestMapping("/admin/review-replies")
public class AdminReviewReplyController {

    private final ReviewAutoReplyService service;

    public AdminReviewReplyController(ReviewAutoReplyService service) {
        this.service = service;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Integer testRating,
                       @RequestParam(required = false) String testComment, Model model) {
        model.addAttribute("rules", service.all());
        model.addAttribute("variables", ReviewAutoReplyService.VARIABLES);
        model.addAttribute("bodyMax", ReviewAutoReplyService.BODY_MAX);
        model.addAttribute("enabled", service.isEnabled());
        model.addAttribute("unreplied", service.countUnreplied());
        if (testRating != null) {
            model.addAttribute("testRating", testRating);
            model.addAttribute("testComment", testComment);
            model.addAttribute("testMatch", service.match(testRating, testComment, "Nguyễn An", "Phòng Deluxe hướng biển").orElse(null));
            model.addAttribute("tested", true);
        }
        return "admin/review-replies/index";
    }

    @PostMapping
    public String create(@RequestParam(required = false) String name, @RequestParam(required = false) String keywords,
                         @RequestParam(required = false) Integer minRating, @RequestParam(required = false) Integer maxRating,
                         @RequestParam(required = false) String body, @RequestParam(required = false) Integer sortOrder,
                         RedirectAttributes ra) {
        return run(() -> service.create(name, keywords, minRating, maxRating, body, sortOrder), "Đã thêm quy tắc", ra);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam(required = false) String name,
                         @RequestParam(required = false) String keywords,
                         @RequestParam(required = false) Integer minRating, @RequestParam(required = false) Integer maxRating,
                         @RequestParam(required = false) String body, @RequestParam(required = false) Integer sortOrder,
                         RedirectAttributes ra) {
        return run(() -> service.update(id, name, keywords, minRating, maxRating, body, sortOrder), "Đã lưu quy tắc", ra);
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        return run(() -> service.toggle(id), "Đã cập nhật trạng thái quy tắc", ra);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        return run(() -> service.delete(id), "Đã xóa quy tắc", ra);
    }

    // Tra loi tu dong cac danh gia cu chua ai tra loi
    @PostMapping("/apply")
    public String apply(RedirectAttributes ra) {
        int n = service.applyToUnreplied();
        ra.addFlashAttribute("successMessage", n == 0 ? "Không có đánh giá nào khớp quy tắc"
                : "Đã tự động trả lời " + n + " đánh giá");
        return "redirect:/admin/review-replies";
    }

    private static String run(Runnable action, String success, RedirectAttributes ra) {
        try {
            action.run();
            ra.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/review-replies";
    }
}
