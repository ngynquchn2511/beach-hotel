package com.hotel.controller;

import com.hotel.service.StayRuleService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

// Ngay nhan phong muon nhat con nhan dat (toi da 6 thang) - cac o chon ngay dat phong dung lam gioi han max
@ControllerAdvice
public class BookingWindowModelAdvice {

    private final StayRuleService stayRuleService;

    public BookingWindowModelAdvice(StayRuleService stayRuleService) {
        this.stayRuleService = stayRuleService;
    }

    @ModelAttribute("bookingMaxDate")
    public LocalDate bookingMaxDate() {
        return stayRuleService.latestCheckIn();
    }
}
