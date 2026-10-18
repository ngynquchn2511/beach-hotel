package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.service.AccountingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

// Bao cao ke toan (ADMIN + ACCOUNTANT): so quy thang + xuat Excel, doi soat VNPay theo ngay voi sao ke
@Controller
@RequestMapping("/admin/accounting")
public class AdminAccountingController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountingService accountingService;

    public AdminAccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping({"", "/ledger"})
    public String ledger(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        model.addAttribute("month", m);
        model.addAttribute("report", accountingService.month(m));
        return "admin/accounting/ledger";
    }

    @GetMapping("/ledger.xlsx")
    public ResponseEntity<byte[]> ledgerExcel(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.now();
        return excel(accountingService.monthExcel(m), "so-quy-" + m + ".xlsx");
    }

    @GetMapping("/vnpay")
    public String vnpay(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day, Model model) {
        LocalDate d = day != null ? day : LocalDate.now().minusDays(1);
        fillVnpay(model, d);
        return "admin/accounting/vnpay";
    }

    @GetMapping("/vnpay.xlsx")
    public ResponseEntity<byte[]> vnpayExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return excel(accountingService.vnpayExcel(day, null), "vnpay-" + day + ".xlsx");
    }

    // Tai sao ke VNPay len -> so khop voi giao dich he thong trong ngay
    @PostMapping("/vnpay")
    public String reconcile(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                            @RequestParam(required = false) MultipartFile statement, Model model) {
        fillVnpay(model, day);
        try {
            model.addAttribute("reconciliation", reconcile(day, statement));
            model.addAttribute("statementName", statement.getOriginalFilename());
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "admin/accounting/vnpay";
    }

    // Cung file sao ke -> tai ve ket qua doi soat dang Excel
    @PostMapping("/vnpay/export")
    public Object reconcileExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                 @RequestParam(required = false) MultipartFile statement, Model model) {
        try {
            return excel(accountingService.vnpayExcel(day, reconcile(day, statement)), "doi-soat-vnpay-" + day + ".xlsx");
        } catch (BusinessException ex) {
            fillVnpay(model, day);
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/accounting/vnpay";
        }
    }

    private AccountingService.Reconciliation reconcile(LocalDate day, MultipartFile statement) {
        byte[] content;
        try {
            content = statement == null ? null : statement.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("Không đọc được file sao kê");
        }
        return AccountingService.reconcile(accountingService.vnpayDay(day),
                AccountingService.parseStatement(statement != null ? statement.getOriginalFilename() : null, content));
    }

    private void fillVnpay(Model model, LocalDate day) {
        var txns = accountingService.vnpayDay(day);
        model.addAttribute("day", day);
        model.addAttribute("dayLabel", day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("txns", txns);
        model.addAttribute("totalIn", txns.stream().filter(t -> t.kind() != AccountingService.VnPayKind.REFUND)
                .map(AccountingService.VnPayTxn::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        model.addAttribute("totalOut", txns.stream().filter(t -> t.kind() == AccountingService.VnPayKind.REFUND)
                .map(AccountingService.VnPayTxn::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }

    private static ResponseEntity<byte[]> excel(byte[] body, String filename) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"").body(body);
    }
}
