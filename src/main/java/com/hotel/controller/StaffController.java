package com.hotel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffController {

    // Se hoan thien day du o buoc "Staff: xac nhan & check-in/out"
    @GetMapping("/staff/bookings")
    public String bookings() {
        return "staff/bookings";
    }
}
