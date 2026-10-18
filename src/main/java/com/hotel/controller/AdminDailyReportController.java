package com.hotel.controller;

import com.hotel.service.DailyReportService;
import com.hotel.service.EmailService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// Bao cao cuoi ngay (night audit): xem / in (ADMIN, STAFF, ACCOUNTANT), gui email ngay (ADMIN)
@Controller
@RequestMapping("/admin/reports/daily")
public class AdminDailyReportController {

    private final DailyReportService reportService;
    private final EmailService emailService;

    public AdminDailyReportController(DailyReportService reportService, EmailService emailService) {
        this.reportService = reportService;
        this.emailService = emailService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day, Model model) {
        LocalDate d = day != null ? day : LocalDate.now();
        model.addAttribute("day", d);
        model.addAttribute("report", reportService.build(d));
        model.addAttribute("recipients", reportService.recipients());
        model.addAttribute("mailConfigured", emailService.isConfigured());
        return "admin/reports/daily";
    }

    @PostMapping("/send")
    public String send(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                       RedirectAttributes redirectAttributes) {
        LocalDate d = day != null ? day : LocalDate.now();
        var to = reportService.recipients();
        boolean sent = emailService.sendDailyReport(to, subject(d), DailyReportService.text(reportService.build(d)));
        redirectAttributes.addFlashAttribute(sent ? "successMessage" : "errorMessage", sent
                ? "Đã gửi báo cáo ngày " + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " tới " + String.join(", ", to)
                : "Chưa gửi được: cần cấu hình MAIL_USERNAME / MAIL_PASSWORD và có ít nhất 1 người nhận");
        return "redirect:/admin/reports/daily?day=" + d;
    }

    public static String subject(LocalDate day) {
        return "Sóng Xanh Resort - Báo cáo cuối ngày " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
