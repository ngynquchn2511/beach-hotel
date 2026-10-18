package com.hotel.controller;

import com.hotel.service.CancellationReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;

// Bao cao huy / khong den (ADMIN, STAFF, ACCOUNTANT). Mac dinh 6 thang gan nhat
@Controller
@RequestMapping("/admin/reports/cancellations")
public class AdminCancellationReportController {

    private static final int MAX_MONTHS = 24;

    private final CancellationReportService reportService;

    public AdminCancellationReportController(CancellationReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to, Model model) {
        YearMonth[] range = range(from, to);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("report", reportService.build(range[0], range[1]));
        model.addAttribute("lateHours", CancellationReportService.LATE_CANCEL_HOURS);
        return "admin/reports/cancellations";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
        YearMonth[] range = range(from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"huy-khong-den-" + range[0] + "-" + range[1] + ".xlsx\"")
                .body(reportService.excel(range[0], range[1]));
    }

    // Dao lai neu nhap nguoc, gioi han toi da 24 thang
    static YearMonth[] range(YearMonth from, YearMonth to) {
        YearMonth t = to != null ? to : YearMonth.now();
        YearMonth f = from != null ? from : t.minusMonths(5);
        if (f.isAfter(t)) {
            YearMonth x = f;
            f = t;
            t = x;
        }
        if (f.plusMonths(MAX_MONTHS - 1).isBefore(t)) {
            f = t.minusMonths(MAX_MONTHS - 1);
        }
        return new YearMonth[]{f, t};
    }
}
