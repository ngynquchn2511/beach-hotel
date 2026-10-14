package com.hotel.controller;

import com.hotel.entity.BookingGuest;
import com.hotel.exception.BusinessException;
import com.hotel.service.GuestRegistrationExportService;
import com.hotel.service.GuestRegistrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

// Tong hop khach luu tru theo ngay de le tan khai bao voi cong an (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/guest-registrations")
public class AdminGuestRegistrationController {

    // Gioi han khoang ngay de trang khong qua nang
    private static final int MAX_RANGE_DAYS = 92;

    private final GuestRegistrationService guestRegistrationService;
    private final GuestRegistrationExportService exportService;

    public AdminGuestRegistrationController(GuestRegistrationService guestRegistrationService,
                                            GuestRegistrationExportService exportService) {
        this.guestRegistrationService = guestRegistrationService;
        this.exportService = exportService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        LocalDate[] range = normalizeRange(from, to);
        List<BookingGuest> guests = guestRegistrationService.findStaying(range[0], range[1]);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
        model.addAttribute("guests", guests);
        return "admin/guest-registrations/list";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        byte[] file = exportService.export(range[0], range[1]);
        String fileName = "khai-bao-luu-tru_" + range[0] + "_" + range[1] + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    // Mac dinh hom nay; chon nguoc thi dao lai; khong qua MAX_RANGE_DAYS ngay
    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        if (from == null && to == null) {
            return new LocalDate[]{today, today};
        }
        if (from == null) {
            from = to;
        }
        if (to == null) {
            to = from;
        }
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException("Chỉ xem tối đa " + MAX_RANGE_DAYS + " ngày mỗi lần");
        }
        return new LocalDate[]{from, to};
    }
}
