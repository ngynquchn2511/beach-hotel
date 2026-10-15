package com.hotel.controller;

import com.hotel.service.ForecastService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

// Du bao cong suat phong 14 / 30 / 60 ngay toi (ADMIN + STAFF)
@Controller
public class AdminForecastController {

    static final List<Integer> ALLOWED_DAYS = List.of(14, 30, 60);

    private final ForecastService forecastService;

    public AdminForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/admin/forecast")
    public String forecast(@RequestParam(defaultValue = "30") int days, Model model) {
        int range = ALLOWED_DAYS.contains(days) ? days : 30;
        model.addAttribute("forecast", forecastService.forecast(LocalDate.now(), range));
        model.addAttribute("days", range);
        model.addAttribute("allowedDays", ALLOWED_DAYS);
        model.addAttribute("lowPercent", ForecastService.LOW_OCCUPANCY_PERCENT);
        return "admin/forecast/index";
    }
}
