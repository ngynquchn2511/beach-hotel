package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingGuest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Xuat danh sach khach luu tru ra Excel - cot theo thong tin can khai bao luu tru (ung dung ASM / cong dich vu cong)
@Service
public class GuestRegistrationExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] HEADERS = {"STT", "Họ và tên", "Ngày sinh", "Giới tính", "Quốc tịch",
            "Loại giấy tờ", "Số giấy tờ", "Nơi thường trú", "Phòng", "Mã đơn", "Ngày đến", "Ngày đi", "Lý do lưu trú"};
    private static final int[] WIDTHS = {6, 26, 12, 9, 14, 18, 16, 34, 8, 9, 12, 12, 14};

    private final GuestRegistrationService guestRegistrationService;

    public GuestRegistrationExportService(GuestRegistrationService guestRegistrationService) {
        this.guestRegistrationService = guestRegistrationService;
    }

    @Transactional(readOnly = true)
    public byte[] export(LocalDate from, LocalDate to) {
        List<BookingGuest> guests = guestRegistrationService.findStaying(from, to);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Khách lưu trú");

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle titleStyle = wb.createCellStyle();
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            titleStyle.setFont(big);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);
            // Giu so giay to dang chu (khong mat so 0 dau, khong hien dang 1.23E+11)
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            Cell title = sheet.createRow(0).createCell(0);
            title.setCellValue("DANH SÁCH KHÁCH LƯU TRÚ - SÓNG XANH RESORT");
            title.setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue("Từ ngày " + from.format(DATE) + " đến ngày " + to.format(DATE)
                    + " · Địa chỉ: Đường Hạ Long, phường Bãi Cháy, Hạ Long, Quảng Ninh");

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }
            sheet.createFreezePane(0, 4);

            int r = 4;
            for (BookingGuest g : guests) {
                Booking b = g.getBooking();
                Row row = sheet.createRow(r);
                int c = 0;
                row.createCell(c++).setCellValue(r - 3);
                row.createCell(c++).setCellValue(g.getFullName());
                row.createCell(c++).setCellValue(g.getDateOfBirth().format(DATE));
                row.createCell(c++).setCellValue(g.getGender().getVietnameseLabel());
                row.createCell(c++).setCellValue(g.getNationality());
                row.createCell(c++).setCellValue(g.getIdType().getVietnameseLabel());
                Cell id = row.createCell(c++);
                id.setCellValue(g.getIdNumber());
                id.setCellStyle(text);
                row.createCell(c++).setCellValue(g.getAddress() != null ? g.getAddress() : "");
                row.createCell(c++).setCellValue(b.getRoom().getRoomNumber());
                row.createCell(c++).setCellValue("#" + b.getId());
                row.createCell(c++).setCellValue(b.getCheckInDate().format(DATE));
                row.createCell(c++).setCellValue(b.getCheckOutDate().format(DATE));
                row.createCell(c).setCellValue("Du lịch");
                r++;
            }
            if (guests.isEmpty()) {
                sheet.createRow(r).createCell(0).setCellValue("Không có khách lưu trú nào trong khoảng ngày này.");
            } else {
                sheet.setAutoFilter(new CellRangeAddress(3, r - 1, 0, HEADERS.length - 1));
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Excel", ex);
        }
    }
}
