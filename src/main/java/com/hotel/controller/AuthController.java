package com.hotel.controller;

import com.hotel.dto.RegisterRequest;
import com.hotel.dto.ResetPasswordRequest;
import com.hotel.exception.BusinessException;
import com.hotel.service.EmailService;
import com.hotel.service.UserService;
import jakarta.validation.Valid;
import com.hotel.service.UserMessages;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final UserMessages userMessages;

    public AuthController(UserService userService, EmailService emailService, UserMessages userMessages) {
        this.userMessages = userMessages;
        this.userService = userService;
        this.emailService = emailService;
    }

    // ?locked=temp&minutes=N (tu failure handler khi khoa tam) - doc so phut o day (gioi han 1..LOCK_MINUTES)
    // vi template hien thong bao bang th:utext, khong duoc doc thang tham so request
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String minutes, Model model,
                            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        // Khach vang lai bam dat phong -> Spring Security luu lai trang dat phong roi chuyen ve day
        var saved = new org.springframework.security.web.savedrequest.HttpSessionRequestCache().getRequest(request, response);
        if (saved != null && com.hotel.security.CustomerPortalSuccessHandler.isBookingPath(
                java.net.URI.create(saved.getRedirectUrl()).getPath())) {
            model.addAttribute("bookingLoginRequired", true);
        }
        int lockMinutes = com.hotel.security.LoginAttemptService.LOCK_MINUTES;
        try {
            lockMinutes = Math.min(lockMinutes, Math.max(1, Integer.parseInt(minutes)));
        } catch (NumberFormatException ignored) {
            // thieu / sai -> dung so phut mac dinh
        }
        model.addAttribute("lockMinutes", lockMinutes);
        model.addAttribute("maxFailedAttempts", com.hotel.security.LoginAttemptService.MAX_FAILED_ATTEMPTS);
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                            BindingResult bindingResult,
                            Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        // Email da co tai khoan chua kich hoat (VD tu tao khi dat phong khong dang nhap): KHONG nhan mat khau tu form,
        // gui link toi chinh email do (dat mat khau / kich hoat) - tranh nguoi khac chiem tai khoan co don cua khach
        var pending = userService.renewPendingLink(request.getEmail());
        if (pending.isPresent()) {
            sendPendingLink(pending.get());
            return "redirect:/login?pending=true";
        }

        try {
            var user = userService.registerCustomer(request);
            emailService.sendEmailVerification(user, link("/verify-email", user.getEmailVerifyToken()));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            // Email da dang ky va dang dung -> goi y Quen mat khau
            model.addAttribute("emailTaken", true);
            return "auth/register";
        }

        return "redirect:/login?registered=true";
    }

    // Link kich hoat trong email dang ky
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(value = "token", required = false) String token) {
        return userService.verifyEmail(token) ? "redirect:/login?verified=true" : "redirect:/login?verifyInvalid=true";
    }

    // Gui lai link kich hoat / dat mat khau. Luon tra cung 1 thong bao (khong lo email co tai khoan hay khong)
    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam(value = "email", required = false) String email) {
        userService.renewPendingLink(email).ifPresent(this::sendPendingLink);
        return "redirect:/login?resent=true";
    }

    private void sendPendingLink(UserService.PendingLink pending) {
        if (pending.setupPassword()) {
            emailService.sendAccountSetup(pending.user(), link("/reset-password", pending.user().getResetToken()));
        } else {
            emailService.sendEmailVerification(pending.user(), link("/verify-email", pending.user().getEmailVerifyToken()));
        }
    }

    private static String link(String path, String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).queryParam("token", token).toUriString();
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam(value = "email", required = false) String email, Model model) {
        if (email == null || email.isBlank()) {
            model.addAttribute("errorMessage", userMessages.get("err.enterEmail"));
            return "auth/forgot-password";
        }

        var user = userService.createPasswordResetToken(email).orElse(null);
        if (user == null) {
            model.addAttribute("errorMessage", userMessages.get("err.emailNotRegistered"));
            model.addAttribute("email", email.trim());
            return "auth/forgot-password";
        }

        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/reset-password")
                .queryParam("token", user.getResetToken())
                .toUriString();
        emailService.sendPasswordReset(user, link);

        model.addAttribute("sent", true);
        model.addAttribute("email", email.trim());
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(value = "token", required = false) String token, Model model) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        model.addAttribute("resetPasswordRequest", request);
        model.addAttribute("tokenValid", userService.isResetTokenValid(token));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest request,
                                BindingResult bindingResult,
                                Model model) {
        boolean tokenValid = userService.isResetTokenValid(request.getToken());
        model.addAttribute("tokenValid", tokenValid);
        if (!tokenValid || bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        try {
            userService.resetPassword(request.getToken(), request.getPassword(), request.getConfirmPassword());
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            return "auth/reset-password";
        }

        return "redirect:/login?reset=true";
    }
}
