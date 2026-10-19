package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.QuickReplyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Mau tra loi nhanh cho le tan (chi ADMIN quan ly - SecurityConfig); le tan dung o trang chi tiet don
@Controller
@RequestMapping("/admin/quick-replies")
public class AdminQuickReplyController {

    private final QuickReplyService service;

    public AdminQuickReplyController(QuickReplyService service) {
        this.service = service;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("replies", service.all());
        model.addAttribute("variables", QuickReplyService.VARIABLES);
        model.addAttribute("bodyMax", QuickReplyService.BODY_MAX);
        return "admin/quick-replies/index";
    }

    @PostMapping
    public String create(@RequestParam(required = false) String title, @RequestParam(required = false) String body,
                         @RequestParam(required = false) Integer sortOrder, RedirectAttributes redirectAttributes) {
        return run(() -> service.create(title, body, sortOrder), "Đã thêm mẫu trả lời", redirectAttributes);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam(required = false) String title, @RequestParam(required = false) String body,
                         @RequestParam(required = false) Integer sortOrder, RedirectAttributes redirectAttributes) {
        return run(() -> service.update(id, title, body, sortOrder), "Đã lưu mẫu trả lời", redirectAttributes);
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> service.toggle(id), "Đã cập nhật trạng thái mẫu", redirectAttributes);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return run(() -> service.delete(id), "Đã xóa mẫu trả lời", redirectAttributes);
    }

    private static String run(Runnable action, String success, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/quick-replies";
    }
}
