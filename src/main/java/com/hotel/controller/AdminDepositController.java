package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.repository.BookingRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Danh sach khach coc theo ngay (le tan, ke toan, quan tri):
//  paid    - khach da coc trong ngay (VNPay hoac le tan ghi nhan chuyen khoan / tien mat)
//  arrival - khach nhan phong ngay do co yeu cau coc: da coc chua, bao nhieu
//  due     - don cho coc het han trong ngay (can goi nhac / se tu huy)
@Controller
public class AdminDepositController {

    private final BookingRepository bookingRepository;

    public AdminDepositController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/admin/deposits")
    @Transactional(readOnly = true)
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(defaultValue = "paid") String view, Model model) {
        LocalDate day = date != null ? date : LocalDate.now();
        String v = switch (view) {
            case "arrival", "due" -> view;
            default -> "paid";
        };
        List<Booking> list = switch (v) {
            case "arrival" -> bookingRepository.findArrivalsWithDeposit(day);
            case "due" -> bookingRepository.findDepositsDueBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            default -> bookingRepository.findDepositsPaidBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        };
        BigDecimal total = list.stream().map(Booking::getDepositAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = list.stream().filter(Booking::isDepositPaid).map(Booking::getDepositAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long online = list.stream().filter(Booking::isDepositPaidOnline).count();

        // 14 ngay gan nhat (tinh toi ngay dang xem): so khach coc + tien coc moi ngay
        LocalDate from = day.minusDays(13);
        Map<LocalDate, BigDecimal[]> daily = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(day); d = d.plusDays(1)) {
            daily.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Booking b : bookingRepository.findDepositsPaidBetween(from.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            BigDecimal[] cell = daily.get(b.getDepositPaidAt().toLocalDate());
            if (cell != null) {
                cell[0] = cell[0].add(BigDecimal.ONE);
                cell[1] = cell[1].add(b.getDepositAmount() != null ? b.getDepositAmount() : BigDecimal.ZERO);
            }
        }
        List<DayTotal> days = new ArrayList<>();
        daily.forEach((d, c) -> days.add(new DayTotal(d, c[0].intValue(), c[1])));

        model.addAttribute("day", day);
        model.addAttribute("view", v);
        model.addAttribute("list", list);
        model.addAttribute("total", total);
        model.addAttribute("paidTotal", paidTotal);
        model.addAttribute("onlineCount", online);
        model.addAttribute("days", days);
        model.addAttribute("isToday", day.equals(LocalDate.now()));
        return "admin/deposits/index";
    }

    public record DayTotal(LocalDate date, int count, BigDecimal amount) {
    }
}
