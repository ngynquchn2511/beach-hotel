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

    @GetMapping("/login")
    public String loginPage() {
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

        try {
            userService.registerCustomer(request);
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", userMessages.of(ex));
            return "auth/register";
        }

        return "redirect:/login?registered=true";
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
