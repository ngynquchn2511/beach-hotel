package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.ContactMessageService;
import com.hotel.service.UserMessages;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

// Form "Gui yeu cau tu van" o trang gioi thieu - bat buoc dang nhap (SecurityConfig)
@Controller
public class ContactController {

    private final ContactMessageService contactMessageService;
    private final UserMessages messages;

    public ContactController(ContactMessageService contactMessageService, UserMessages messages) {
        this.contactMessageService = contactMessageService;
        this.messages = messages;
    }

    // Khach chua dang nhap bam "Dang nhap de gui yeu cau" -> /login -> dang nhap xong ve lai form lien he
    @GetMapping("/contact")
    public String loginThenBack() {
        return "redirect:/about#lien-he";
    }

    @PostMapping("/contact")
    public String submit(@RequestParam(required = false) String fullName,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) String phone,
                         @RequestParam(required = false) String message,
                         // O an chong bot: nguoi that khong nhin thay nen khong dien. Co gia tri -> bo qua, van bao thanh cong
                         @RequestParam(required = false) String website,
                         RedirectAttributes redirectAttributes) {
        if (website != null && !website.isBlank()) {
            redirectAttributes.addFlashAttribute("contactSuccess", messages.get("contact.success"));
            return "redirect:/about#lien-he";
        }
        try {
            contactMessageService.submit(fullName, email, phone, message);
            redirectAttributes.addFlashAttribute("contactSuccess", messages.get("contact.success"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("contactError", messages.of(ex));
            // Giu lai noi dung khach da go de khong phai nhap lai
            redirectAttributes.addFlashAttribute("contactForm", Map.of(
                    "fullName", fullName == null ? "" : fullName,
                    "email", email == null ? "" : email,
                    "phone", phone == null ? "" : phone,
                    "message", message == null ? "" : message));
        }
        return "redirect:/about#lien-he";
    }
}
