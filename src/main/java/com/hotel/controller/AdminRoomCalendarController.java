package com.hotel.controller;

import com.hotel.service.RoomCalendarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// Lich phong timeline cho le tan - ?from=yyyy-MM-dd&days=7|14|30
@Controller
@RequestMapping("/admin/room-calendar")
public class AdminRoomCalendarController {

    private final RoomCalendarService roomCalendarService;

    public AdminRoomCalendarController(RoomCalendarService roomCalendarService) {
        this.roomCalendarService = roomCalendarService;
    }

    @GetMapping
    public String calendar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(defaultValue = "14") int days, Model model) {
        LocalDate today = LocalDate.now();
        int range = RoomCalendarService.ALLOWED_DAYS.contains(days) ? days : 14;
        // Mac dinh lui 1 ngay de le tan van thay khach tra phong hom nay
        LocalDate start = from != null ? from : today.minusDays(1);
        model.addAttribute("cal", roomCalendarService.build(start, range, today));
        model.addAttribute("allowedDays", RoomCalendarService.ALLOWED_DAYS);
        model.addAttribute("today", today);
        return "admin/room-calendar/index";
    }
}
