package com.hotel.controller;

import com.hotel.entity.Booking;
import com.hotel.exception.BusinessException;
import com.hotel.service.CompanyInvoiceService;
import com.hotel.util.PaginationUtil;
import com.hotel.util.Pager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Ke toan: don can xuat hoa don cong ty (VAT), danh dau da xuat HDDT, tai Excel (ADMIN + STAFF)
@Controller
@RequestMapping("/admin/company-invoices")
public class AdminCompanyInvoiceController {

    private final CompanyInvoiceService companyInvoiceService;

    public AdminCompanyInvoiceController(CompanyInvoiceService companyInvoiceService) {
        this.companyInvoiceService = companyInvoiceService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        Pager<Booking> pager = Pager.of(companyInvoiceService.list(), page, PaginationUtil.DEFAULT_PAGE_SIZE);
        model.addAttribute("pager", pager);
        model.addAttribute("bookings", pager.items());
        return "admin/company-invoices/list";
    }

    @PostMapping("/{id}/issued")
    public String issued(@PathVariable Long id, @RequestParam(required = false) String invoiceNumber, RedirectAttributes redirectAttributes) {
        try {
            companyInvoiceService.markIssued(id, invoiceNumber);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận số hóa đơn cho đơn #" + id);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/company-invoices";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hoa-don-cong-ty.xlsx\"")
                .body(companyInvoiceService.exportExcel());
    }
}
