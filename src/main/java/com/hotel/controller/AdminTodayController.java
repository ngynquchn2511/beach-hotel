package com.hotel.controller;

import com.hotel.service.BookingService;
import com.hotel.service.FrontDeskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// Man hinh "Hom nay" cua le tan (STAFF + ADMIN): khach den, khach di, can xu ly, phong cho don.
// ?date=yyyy-MM-dd de xem truoc ngay mai / xem lai ngay da qua
@Controller
public class AdminTodayController {

    private final FrontDeskService frontDeskService;
    private final BookingService bookingService;

    public AdminTodayController(FrontDeskService frontDeskService, BookingService bookingService) {
        this.bookingService = bookingService;
        this.frontDeskService = frontDeskService;
    }

    private com.hotel.service.StaffShiftService staffShiftService;
    private com.hotel.service.WeatherService weatherService;

    // Thoi tiet 3 ngay: le tan nhac khach tour bien / du thuyen khi gio manh, mua bao
    @org.springframework.beans.factory.annotation.Autowired
    void setWeatherService(com.hotel.service.WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setStaffShiftService(com.hotel.service.StaffShiftService staffShiftService) {
        this.staffShiftService = staffShiftService;
    }

    @GetMapping("/admin/today")
    public String today(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("day", frontDeskService.day(date != null ? date : today, today));
        // Yeu cau nhan som / tra muon cho le tan duyet (moi ngay)
        model.addAttribute("stayTimeRequests", bookingService.findPendingStayTimeRequests());
        if (weatherService != null) {
            model.addAttribute("weatherDays", weatherService.next(3));
        }
        if (staffShiftService != null && (date == null || date.equals(today))) {
            model.addAttribute("onDuty", staffShiftService.onDuty(java.time.LocalDateTime.now()));
        }
        return "admin/today";
    }

    // Trang tam truoc day cua nhan vien -> chuyen sang man hinh "Hom nay"
    @GetMapping("/staff/bookings")
    public String legacyStaffPage() {
        return "redirect:/admin/today";
    }
}
