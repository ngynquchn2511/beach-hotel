package com.hotel.controller;

import com.hotel.dto.ChangePasswordRequest;
import com.hotel.dto.ProfileUpdateRequest;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.BookingService;
import com.hotel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hotel.service.UserMessages;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Trang "Tai khoan cua toi" cho khach hang da dang nhap (thuoc /customer/** nen bat buoc role CUSTOMER)
@Controller
@RequestMapping("/customer/account")
public class CustomerAccountController {

    private final UserService userService;
    private final BookingService bookingService;
    private final UserMessages userMessages;

    public CustomerAccountController(UserService userService, BookingService bookingService, UserMessages userMessages) {
        this.userMessages = userMessages;
        this.userService = userService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public String account(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User user = userService.findById(userDetails.getUser().getId());
        if (!model.containsAttribute("profileRequest")) {
            ProfileUpdateRequest profile = new ProfileUpdateRequest();
            profile.setFullName(user.getFullName());
            profile.setPhoneNumber(user.getPhoneNumber());
            model.addAttribute("profileRequest", profile);
        }
        if (!model.containsAttribute("passwordRequest")) {
            model.addAttribute("passwordRequest", new ChangePasswordRequest());
        }
        model.addAttribute("account", user);
        model.addAttribute("bookingCount", bookingService.findAllForCustomer(user.getId()).size());
        return "customer/account";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileRequest") ProfileUpdateRequest request,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return keepErrors("profileRequest", request, bindingResult, redirectAttributes);
        }
        try {
            User updated = userService.updateProfile(userDetails.getUser().getId(), request);
            // Dong bo lai thong tin trong phien dang nhap hien tai
            userDetails.getUser().setFullName(updated.getFullName());
            userDetails.getUser().setPhoneNumber(updated.getPhoneNumber());
            redirectAttributes.addFlashAttribute("profileSuccess", userMessages.get("msg.profileSaved"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("profileError", userMessages.of(ex));
            redirectAttributes.addFlashAttribute("profileRequest", request);
        }
        return "redirect:/customer/account";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordRequest") ChangePasswordRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        // Khong gui lai mat khau ve trinh duyet
        ChangePasswordRequest blank = new ChangePasswordRequest();
        if (bindingResult.hasErrors()) {
            return keepErrors("passwordRequest", blank, bindingResult, redirectAttributes);
        }
        try {
            User updated = userService.changePassword(userDetails.getUser().getId(), request);
            userDetails.getUser().setPassword(updated.getPassword());
            redirectAttributes.addFlashAttribute("passwordSuccess", userMessages.get("msg.passwordChanged"));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("passwordError", userMessages.of(ex));
        }
        return "redirect:/customer/account#doi-mat-khau";
    }

    // Giu lai du lieu + loi validate qua redirect (PRG) de form hien loi dung cho
    private String keepErrors(String name, Object request, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult." + name, bindingResult);
        redirectAttributes.addFlashAttribute(name, request);
        return "redirect:/customer/account" + ("passwordRequest".equals(name) ? "#doi-mat-khau" : "");
    }
}
