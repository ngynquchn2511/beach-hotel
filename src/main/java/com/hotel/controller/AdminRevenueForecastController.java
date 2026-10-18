package com.hotel.controller;

import com.hotel.service.RevenueForecastService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

// Du bao doanh thu 3 thang toi (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/revenue-forecast")
public class AdminRevenueForecastController {

    private final RevenueForecastService forecastService;

    public AdminRevenueForecastController(RevenueForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("forecasts", forecastService.forecast(LocalDate.now()));
        return "admin/accounting/revenue-forecast";
    }
}
