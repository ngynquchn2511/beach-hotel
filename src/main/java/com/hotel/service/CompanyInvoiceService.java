package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

// Hoa don cong ty: khach / le tan nhap ten don vi, ma so thue, dia chi, email nhan HDDT;
// ke toan xem danh sach, danh dau da xuat hoa don dien tu (kem so HD), xuat Excel
@Service
public class CompanyInvoiceService {

    // MST: 10 so, 10 so + "-" + 3 so (chi nhanh), hoac 12 so (ca nhan kinh doanh dung so CCCD)
    private static final Pattern TAX_CODE = Pattern.compile("^\\d{10}(-\\d{3})?$|^\\d{12}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public record Info(String companyName, String taxCode, String address, String email) {
    }

    private final BookingRepository bookingRepository;

    public CompanyInvoiceService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // Kiem tra + chuan hoa thong tin (dung truoc khi tao don de khong tao don roi moi bao loi)
    public static Info validate(String companyName, String taxCode, String address, String email) {
        String name = clean(companyName, 200);
        String tax = taxCode == null ? "" : taxCode.replaceAll("\\s", "");
        String addr = clean(address, 255);
        String mail = clean(email, 100);
        if (name == null) {
            throw BusinessException.of("ci.error.name");
        }
        if (!TAX_CODE.matcher(tax).matches()) {
            throw BusinessException.of("ci.error.taxCode");
        }
        if (addr == null) {
            throw BusinessException.of("ci.error.address");
        }
        if (mail != null && !EMAIL.matcher(mail).matches()) {
            throw BusinessException.of("ci.error.email");
        }
        return new Info(name, tax, addr, mail);
    }

    // Da xuat HDDT thi khong sua nua (phai lap hoa don dieu chinh ngoai he thong)
    public boolean canEdit(Booking booking) {
        return booking.getEInvoiceIssuedAt() == null && booking.getStatus() != BookingStatus.CANCELLED;
    }

    @Transactional
    public Booking save(Booking booking, Info info) {
        if (!canEdit(booking)) {
            throw BusinessException.of("ci.error.locked");
        }
        booking.setInvoiceCompanyName(info.companyName());
        booking.setInvoiceTaxCode(info.taxCode());
        booking.setInvoiceAddress(info.address());
        booking.setInvoiceEmail(info.email());
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking remove(Booking booking) {
        if (!canEdit(booking)) {
            throw BusinessException.of("ci.error.locked");
        }
        booking.setInvoiceCompanyName(null);
        booking.setInvoiceTaxCode(null);
        booking.setInvoiceAddress(null);
        booking.setInvoiceEmail(null);
        return bookingRepository.save(booking);
    }

    public List<Booking> list() {
        return bookingRepository.findCompanyInvoices();
    }

    // Ke toan da phat hanh HDDT tren he thong hoa don dien tu -> ghi so hoa don
    @Transactional
    public Booking markIssued(Long bookingId, String invoiceNumber) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BusinessException("Không tìm thấy đơn"));
        if (!booking.isCompanyInvoice()) {
            throw new BusinessException("Đơn này không yêu cầu hóa đơn công ty");
        }
        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw new BusinessException("Chỉ xuất hóa đơn khi khách đã trả phòng và thanh toán");
        }
        String number = invoiceNumber == null ? "" : invoiceNumber.trim();
        if (number.isEmpty() || number.length() > 30) {
            throw new BusinessException("Vui lòng nhập số hóa đơn điện tử (tối đa 30 ký tự)");
        }
        booking.setEInvoiceNumber(number);
        booking.setEInvoiceIssuedAt(LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    // Danh sach cho ke toan nhap len he thong HDDT
    @Transactional(readOnly = true)
    public byte[] exportExcel() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Hoa don cong ty");
            String[] headers = {"Mã đơn", "Trả phòng", "Trạng thái", "Tên đơn vị", "Mã số thuế", "Địa chỉ", "Email nhận HĐ",
                    "Tổng tiền (VND)", "Số HĐĐT", "Ngày xuất"};
            Row head = sheet.createRow(0);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(bold);
            }
            DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            int r = 1;
            for (Booking b : list()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue("#" + b.getId());
                row.createCell(1).setCellValue(b.getCheckOutDate().format(date));
                row.createCell(2).setCellValue(b.getStatus().getVietnameseLabel());
                row.createCell(3).setCellValue(b.getInvoiceCompanyName());
                row.createCell(4).setCellValue(b.getInvoiceTaxCode());
                row.createCell(5).setCellValue(b.getInvoiceAddress());
                row.createCell(6).setCellValue(b.getInvoiceEmail() != null ? b.getInvoiceEmail() : "");
                row.createCell(7).setCellValue(b.getTotalAmount().doubleValue());
                row.createCell(8).setCellValue(b.getEInvoiceNumber() != null ? b.getEInvoiceNumber() : "");
                row.createCell(9).setCellValue(b.getEInvoiceIssuedAt() != null ? b.getEInvoiceIssuedAt().format(date) : "");
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
