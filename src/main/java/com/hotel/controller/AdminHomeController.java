package com.hotel.controller;

import com.hotel.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Trang chu cong quan tri theo vai tro (le tan / admin -> tong quan, buong phong -> don phong, ke toan -> hoa don)
@Controller
public class AdminHomeController {

    @GetMapping("/admin/home")
    public String home(@AuthenticationPrincipal CustomUserDetails user) {
        return "redirect:" + (user != null ? user.getUser().getRole().adminHome() : "/admin");
    }
}
