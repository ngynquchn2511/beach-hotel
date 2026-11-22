package com.hotel.controller;

import com.hotel.service.RoomCalendarService;
import com.hotel.util.Pager;
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

    // So phong (hang) moi trang lich
    static final int ROOMS_PER_PAGE = 10;

    private final RoomCalendarService roomCalendarService;
    private final com.hotel.service.StayRuleService stayRuleService;

    public AdminRoomCalendarController(RoomCalendarService roomCalendarService, com.hotel.service.StayRuleService stayRuleService) {
        this.roomCalendarService = roomCalendarService;
        this.stayRuleService = stayRuleService;
    }

    // month=yyyy-MM: xem ca thang (thang nay .. 6 thang toi). Lich xem toi da 6 thang toi (bang han nhan dat truoc)
    @GetMapping
    public String calendar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(defaultValue = "14") int days,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") java.time.YearMonth month,
                           @RequestParam(defaultValue = "1") int page, Model model) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusMonths(com.hotel.service.StayRuleService.MAX_ADVANCE_MONTHS);
        java.time.YearMonth thisMonth = java.time.YearMonth.from(today);
        java.time.YearMonth lastMonth = java.time.YearMonth.from(horizon);
        boolean monthMode = month != null;
        int range;
        LocalDate start;
        if (monthMode) {
            month = month.isBefore(thisMonth) ? thisMonth : (month.isAfter(lastMonth) ? lastMonth : month);
            start = month.atDay(1);
            range = month.lengthOfMonth();
        } else {
            range = RoomCalendarService.ALLOWED_DAYS.contains(days) ? days : 14;
            // Mac dinh lui 1 ngay de le tan van thay khach tra phong hom nay
            start = from != null ? from : today.minusDays(1);
            // Khong xem qua 6 thang toi: khung cuoi cung ket thuc dung ngay gioi han
            if (start.plusDays(range).isAfter(horizon.plusDays(1))) {
                start = horizon.plusDays(1).minusDays(range);
            }
        }
        LocalDate lastBookable = stayRuleService.latestCheckIn(today);
        RoomCalendarService.Calendar cal = roomCalendarService.build(start, range, today, lastBookable);
        model.addAttribute("monthMode", monthMode);
        model.addAttribute("month", month);
        model.addAttribute("prevMonth", monthMode && month.isAfter(thisMonth) ? month.minusMonths(1) : null);
        model.addAttribute("nextMonth", monthMode && month.isBefore(lastMonth) ? month.plusMonths(1) : null);
        model.addAttribute("canNext", cal.to().isBefore(horizon.plusDays(1)));
        model.addAttribute("horizon", horizon);
        model.addAttribute("lastBookable", lastBookable);
        java.util.List<java.time.YearMonth> months = new java.util.ArrayList<>();
        for (java.time.YearMonth m = thisMonth; !m.isAfter(lastMonth); m = m.plusMonths(1)) {
            months.add(m);
        }
        model.addAttribute("months", months);
        model.addAttribute("overview", roomCalendarService.overview(today, horizon, lastBookable));
        model.addAttribute("cal", cal);
        // Chia trang theo phong (hang); dong "con trong" cuoi bang van tinh tren tat ca phong
        model.addAttribute("roomPager", Pager.of(cal.rows(), page, ROOMS_PER_PAGE));
        model.addAttribute("allowedDays", RoomCalendarService.ALLOWED_DAYS);
        model.addAttribute("today", today);
        return "admin/room-calendar/index";
    }
}
