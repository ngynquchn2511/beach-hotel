package com.hotel.controller;

import com.hotel.security.AdminOtpService;
import com.hotel.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Nhap ma xac thuc 2 buoc sau khi dang nhap cong quan tri
@Controller
public class AdminOtpController {

    private final AdminOtpService otpService;

    public AdminOtpController(AdminOtpService otpService) {
        this.otpService = otpService;
    }

    @GetMapping("/admin/otp")
    public String page(HttpSession session, Model model) {
        if (!AdminOtpService.isPending(session)) {
            return "redirect:/admin/dashboard";
        }
        model.addAttribute("maskedEmail", AdminOtpService.maskedEmail(session));
        model.addAttribute("validMinutes", AdminOtpService.VALIDITY.toMinutes());
        return "admin/otp";
    }

    @PostMapping("/admin/otp")
    public String verify(@RequestParam(required = false) String code, HttpSession session, RedirectAttributes redirectAttributes) {
        AdminOtpService.Result result = otpService.verify(session, code);
        return switch (result) {
            case OK, NOT_PENDING -> "redirect:/admin/dashboard";
            case WRONG -> {
                redirectAttributes.addFlashAttribute("errorMessage", "Mã không đúng, vui lòng kiểm tra lại email");
                yield "redirect:/admin/otp";
            }
            case EXPIRED -> {
                redirectAttributes.addFlashAttribute("errorMessage", "Mã đã hết hạn - bấm \"Gửi lại mã\" để nhận mã mới");
                yield "redirect:/admin/otp";
            }
            case TOO_MANY -> {
                // Sai qua nhieu lan: huy phien, dang nhap lai tu dau
                session.invalidate();
                yield "redirect:/admin?otp=locked";
            }
        };
    }

    @PostMapping("/admin/otp/resend")
    public String resend(HttpSession session, @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        if (!AdminOtpService.isPending(session)) {
            return "redirect:/admin/dashboard";
        }
        boolean sent = otpService.resend(session, user != null ? user.getUser().getFullName() : null);
        redirectAttributes.addFlashAttribute(sent ? "successMessage" : "errorMessage",
                sent ? "Đã gửi mã mới tới email của bạn" : "Vui lòng đợi " + AdminOtpService.RESEND_COOLDOWN.toSeconds() + " giây rồi gửi lại");
        return "redirect:/admin/otp";
    }
}
