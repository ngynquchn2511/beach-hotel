package com.hotel.controller;

import com.hotel.entity.ContactMessage;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ContactMessageService;
import com.hotel.util.PaginationUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

// Hop thu lien he: yeu cau tu van khach gui tu trang gioi thieu - le tan tra loi qua email / danh dau da xu ly
@Controller
@RequestMapping("/admin/contact-messages")
public class AdminContactMessageController {

    private final ContactMessageService contactMessageService;

    public AdminContactMessageController(ContactMessageService contactMessageService) {
        this.contactMessageService = contactMessageService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        List<ContactMessage> all = contactMessageService.findForAdmin(status, q);
        int totalPages = PaginationUtil.totalPages(all.size(), PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("contactMessages", PaginationUtil.slice(all, page, PaginationUtil.DEFAULT_PAGE_SIZE));
        model.addAttribute("totalElements", all.size());
        model.addAttribute("currentPage", Math.max(1, Math.min(page, totalPages)));
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PaginationUtil.pageNumbersToShow(page, totalPages));
        model.addAttribute("status", status);
        model.addAttribute("q", q);
        return "admin/contact-messages/list";
    }

    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id,
                        @RequestParam(required = false) String reply,
                        @RequestParam(required = false) String status,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "1") int page,
                        @AuthenticationPrincipal CustomUserDetails currentUser,
                        RedirectAttributes redirectAttributes) {
        try {
            ContactMessage m = contactMessageService.reply(id, reply, currentUser.getUser().getFullName());
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi trả lời tới " + m.getEmail());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + listUrl(status, q, page) + "#contact-" + id;
    }

    @PostMapping("/{id}/toggle-handled")
    public String toggleHandled(@PathVariable Long id,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "1") int page,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                RedirectAttributes redirectAttributes) {
        try {
            ContactMessage m = contactMessageService.toggleHandled(id, currentUser.getUser().getFullName());
            redirectAttributes.addFlashAttribute("successMessage", m.isHandled()
                    ? "Đã đánh dấu xử lý tin của " + m.getFullName()
                    : "Đã mở lại tin của " + m.getFullName());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + listUrl(status, q, page);
    }

    private static String listUrl(String status, String q, int page) {
        return UriComponentsBuilder.fromPath("/admin/contact-messages")
                .queryParamIfPresent("status", Optional.ofNullable(status).filter(s -> !s.isBlank()))
                .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                .queryParam("page", page)
                .encode().toUriString();
    }
}
