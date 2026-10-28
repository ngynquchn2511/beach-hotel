package com.hotel.controller;

import com.hotel.service.BookingMessageService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Hop tin nhan cua le tan: moi don co tin nhan 1 dong, chua doc len dau (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/messages")
public class AdminMessageController {

    private final BookingMessageService messageService;

    public AdminMessageController(BookingMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public String inbox(@RequestParam(defaultValue = "false") boolean unread, @RequestParam(defaultValue = "1") int page, Model model) {
        var all = messageService.inbox().stream().filter(c -> !unread || c.unread() > 0)
                .sorted(java.util.Comparator.comparing((BookingMessageService.Conversation c) -> c.unread() == 0)).toList();
        Pager<BookingMessageService.Conversation> pager = Pager.of(all, page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("conversations", pager.items());
        model.addAttribute("onlyUnread", unread);
        return "admin/messages/index";
    }
}
