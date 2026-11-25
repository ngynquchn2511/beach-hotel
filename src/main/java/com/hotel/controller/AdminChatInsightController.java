package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ChatInsightService;
import com.hotel.util.Pager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

// Thong ke tro ly "Song" (le tan + quan tri): khach hoi gi nhieu nhat, cau Song chua hieu, ty le giai quyet,
// gop y khach chua hai long (danh dau da lien he / xu ly)
@Controller
@RequestMapping("/admin/chat-insights")
public class AdminChatInsightController {

    private static final List<Integer> PERIODS = List.of(7, 30, 90);

    private final ChatInsightService service;

    public AdminChatInsightController(ChatInsightService service) {
        this.service = service;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "open") String status,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        int d = PERIODS.contains(days) ? days : 30;
        String st = List.of("open", "handled", "all").contains(status) ? status : "open";
        model.addAttribute("report", service.report(d, LocalDate.now()));
        model.addAttribute("days", d);
        model.addAttribute("periods", PERIODS);
        model.addAttribute("status", st);
        model.addAttribute("feedbackPager", Pager.of(service.feedbacks(st), page, 10));
        model.addAttribute("handledCount", service.feedbacks("handled").size());
        model.addAttribute("noteMax", ChatInsightService.NOTE_MAX);
        return "admin/chat-insights/index";
    }

    @PostMapping("/{id}/handle")
    public String handle(@PathVariable Long id, @RequestParam(required = false) String note,
                         @RequestParam(defaultValue = "30") int days,
                         @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes ra) {
        try {
            service.handle(id, principal == null ? null : principal.getUser(), note);
            ra.addFlashAttribute("successMessage", "Đã đánh dấu góp ý là đã xử lý");
        } catch (BusinessException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/chat-insights?days=" + days + "#gop-y";
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, @RequestParam(defaultValue = "30") int days, RedirectAttributes ra) {
        service.reopen(id);
        ra.addFlashAttribute("successMessage", "Đã mở lại góp ý");
        return "redirect:/admin/chat-insights?days=" + days + "&status=handled#gop-y";
    }
}
