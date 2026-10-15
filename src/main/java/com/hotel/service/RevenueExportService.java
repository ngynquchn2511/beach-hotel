package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.Payment;
import com.hotel.entity.PaymentMethod;
import com.hotel.repository.PaymentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Xuat bao cao doanh thu theo khoang ngay dang loc tren dashboard ra file Excel (.xlsx)
@Service
public class RevenueExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] BOOKING_HEADERS = {"Mã đơn", "Ngày tạo", "Khách hàng", "Số điện thoại", "Phòng",
            "Loại phòng", "Nhận phòng", "Trả phòng", "Số đêm", "Combo", "Giảm giá", "Tiền đơn", "Phụ phí",
            "Tổng cộng", "Đã cọc", "Trạng thái đơn", "Thanh toán", "Phương thức", "Ngày thanh toán"};
    private static final int[] BOOKING_WIDTHS = {9, 17, 24, 14, 8, 18, 12, 12, 8, 20, 13, 14, 13, 14, 13, 16, 17, 22, 17};

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ReportService reportService;

    public RevenueExportService(BookingService bookingService, PaymentService paymentService,
                                PaymentRepository paymentRepository, ReportService reportService) {
        this.reportService = reportService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
    }

    // Can transaction de doc cac quan he LAZY (khach, phong, combo) khi ghi tung dong
    @Transactional(readOnly = true)
    public byte[] exportRevenue(LocalDate fromDate, LocalDate toDate) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            writeSummarySheet(wb, styles, fromDate, toDate);
            writeReportSheet(wb, styles, fromDate, toDate);
            writeBookingSheet(wb, styles, fromDate, toDate);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được file Excel", ex);
        }
    }

    private void writeSummarySheet(Workbook wb, Styles styles, LocalDate fromDate, LocalDate toDate) {
        Sheet sheet = wb.createSheet("Tổng quan");
        sheet.setColumnWidth(0, 42 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        int r = 0;

        Cell title = sheet.createRow(r++).createCell(0);
        title.setCellValue("BÁO CÁO DOANH THU SÓNG XANH RESORT");
        title.setCellStyle(styles.title);
        sheet.createRow(r++).createCell(0)
                .setCellValue("Từ ngày " + fromDate.format(DATE) + " đến ngày " + toDate.format(DATE));
        r++;

        BookingService.HotelKpi kpi = bookingService.getHotelKpi(fromDate, toDate);
        r = sectionHeader(sheet, styles, r, "Chỉ số resort (theo ngày khách ở)");
        r = labelValue(sheet, r, "Công suất phòng (%)", kpi.occupancyRate(), styles.percent);
        r = labelValue(sheet, r, "Số đêm-phòng đã bán", kpi.soldRoomNights(), styles.integer);
        r = labelValue(sheet, r, "Số đêm-phòng có thể bán", kpi.availableRoomNights(), styles.integer);
        r = labelValue(sheet, r, "Giá trung bình mỗi đêm - ADR (VND)", kpi.adr(), styles.money);
        r = labelValue(sheet, r, "Doanh thu trên mỗi phòng - RevPAR (VND)", kpi.revPar(), styles.money);
        r++;

        Map<PaymentMethod, BigDecimal> paid = paymentService.getPaidRevenueByMethod(fromDate, toDate);
        BigDecimal paidTotal = paid.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        r = sectionHeader(sheet, styles, r, "Tiền đã thu (theo ngày thanh toán)");
        r = labelValue(sheet, r, "Tổng đã thu (VND)", paidTotal, styles.moneyBold);
        r = labelValue(sheet, r, "Tiền mặt (VND)", paid.get(PaymentMethod.CASH), styles.money);
        r = labelValue(sheet, r, "Chuyển khoản (VND)", paid.get(PaymentMethod.BANK_TRANSFER), styles.money);
        r = labelValue(sheet, r, "Cọc online qua VNPay (VND)", paid.get(PaymentMethod.ONLINE), styles.money);
        r++;

        Map<String, BigDecimal> byRoomType = bookingService.getRevenueByRoomType(fromDate, toDate);
        r = sectionHeader(sheet, styles, r, "Doanh thu theo loại phòng (theo ngày tạo đơn, gồm phụ phí)");
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : byRoomType.entrySet()) {
            r = labelValue(sheet, r, e.getKey(), e.getValue(), styles.money);
            total = total.add(e.getValue());
        }
        r = labelValue(sheet, r, "Tổng cộng", total, styles.moneyBold);
        r++;

        r = sectionHeader(sheet, styles, r, "Ghi chú");
        for (String note : List.of(
                "- Công suất phòng = số đêm-phòng đã bán / (số phòng x số ngày).",
                "- Đêm-phòng đã bán gồm đơn đã xác nhận, đang ở, đã trả phòng.",
                "- ADR = tiền đơn (phòng + combo - giảm giá, không gồm phụ phí) / số đêm-phòng đã bán.",
                "- RevPAR = tiền đơn / số đêm-phòng có thể bán (= ADR x công suất).",
                "- Doanh thu theo loại phòng bỏ qua đơn đã hủy.")) {
            sheet.createRow(r++).createCell(0).setCellValue(note);
        }
    }

    // Bao cao chi tiet: kenh dat, huy don, ma giam gia, dich vu them (cung so lieu voi dashboard)
    private void writeReportSheet(Workbook wb, Styles styles, LocalDate fromDate, LocalDate toDate) {
        ReportService.Report report = reportService.build(fromDate, toDate);
        Sheet sheet = wb.createSheet("Báo cáo chi tiết");
        sheet.setColumnWidth(0, 34 * 256);
        for (int col = 1; col <= 3; col++) {
            sheet.setColumnWidth(col, 18 * 256);
        }
        int r = 0;
        Cell title = sheet.createRow(r++).createCell(0);
        title.setCellValue("BÁO CÁO CHI TIẾT");
        title.setCellStyle(styles.title);
        sheet.createRow(r++).createCell(0).setCellValue("Đơn tạo từ " + fromDate.format(DATE) + " đến " + toDate.format(DATE)
                + " (không tính đơn hủy; doanh thu gồm phụ phí)");
        r++;

        r = sectionHeader(sheet, styles, r, "Doanh thu theo kênh đặt");
        r = tableHeader(sheet, styles, r, "Kênh", "Số đơn", "Doanh thu (VND)", "Tỷ trọng (%)");
        for (ReportService.ChannelRow c : report.channels()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(c.label());
            numberCell(row, 1, c.bookings(), styles.integer);
            numberCell(row, 2, c.revenue(), styles.money);
            numberCell(row, 3, c.share(), styles.percent);
        }
        r++;

        ReportService.CancellationStats cs = report.cancellations();
        r = sectionHeader(sheet, styles, r, "Hủy đơn");
        r = labelValue(sheet, r, "Số đơn tạo", cs.created(), styles.integer);
        r = labelValue(sheet, r, "Số đơn đã hủy", cs.cancelled(), styles.integer);
        r = labelValue(sheet, r, "Tỷ lệ hủy (%)", cs.cancelRate(), styles.percent);
        r = labelValue(sheet, r, "Đơn hủy đã cọc", cs.withDepositPaid(), styles.integer);
        r = labelValue(sheet, r, "Tiền cọc đã / cần hoàn (VND)", cs.refunded(), styles.money);
        r = labelValue(sheet, r, "Tiền cọc giữ lại (VND)", cs.forfeited(), styles.money);
        r++;

        r = sectionHeader(sheet, styles, r, "Mã giảm giá được dùng");
        r = tableHeader(sheet, styles, r, "Mã", "Lượt dùng", "Đã giảm (VND)", "Doanh thu đơn (VND)");
        for (ReportService.DiscountRow d : report.discounts()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(d.code());
            numberCell(row, 1, d.uses(), styles.integer);
            numberCell(row, 2, d.discountTotal(), styles.money);
            numberCell(row, 3, d.revenue(), styles.money);
        }
        r++;

        r = sectionHeader(sheet, styles, r, "Dịch vụ thêm & phụ phí");
        r = tableHeader(sheet, styles, r, "Loại", "Số lần", "Doanh thu (VND)");
        for (ReportService.ServiceRow sv : report.services()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(sv.label());
            numberCell(row, 1, sv.count(), styles.integer);
            numberCell(row, 2, sv.amount(), styles.money);
        }
        Row total = sheet.createRow(r);
        total.createCell(0).setCellValue("Tổng cộng");
        numberCell(total, 2, report.servicesTotal(), styles.moneyBold);
    }

    private int tableHeader(Sheet sheet, Styles styles, int r, String... titles) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(styles.header);
        }
        return r + 1;
    }

    private void writeBookingSheet(Workbook wb, Styles styles, LocalDate fromDate, LocalDate toDate) {
        Sheet sheet = wb.createSheet("Đơn đặt phòng");
        Row header = sheet.createRow(0);
        for (int i = 0; i < BOOKING_HEADERS.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(BOOKING_HEADERS[i]);
            c.setCellStyle(styles.header);
            sheet.setColumnWidth(i, BOOKING_WIDTHS[i] * 256);
        }
        sheet.createFreezePane(0, 1);

        List<Booking> bookings = bookingService.findBookingsCreatedBetween(fromDate, toDate);
        Map<Long, BigDecimal> charges = bookingService.chargeTotalsByBooking();
        Map<Long, Payment> payments = paymentRepository
                .findByBookingIdIn(bookings.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity(), (a, b) -> a));

        int r = 1;
        for (Booking b : bookings) {
            Row row = sheet.createRow(r++);
            BigDecimal charge = charges.getOrDefault(b.getId(), BigDecimal.ZERO);
            Payment payment = payments.get(b.getId());
            int c = 0;
            row.createCell(c++).setCellValue("#" + b.getId());
            row.createCell(c++).setCellValue(b.getCreatedAt().format(DATE_TIME));
            row.createCell(c++).setCellValue(b.getGuestName() != null ? b.getGuestName() : b.getCustomer().getFullName());
            row.createCell(c++).setCellValue(b.getGuestPhone() != null ? b.getGuestPhone() : b.getCustomer().getPhoneNumber());
            row.createCell(c++).setCellValue(b.getRoom().getRoomNumber());
            row.createCell(c++).setCellValue(b.getRoom().getRoomType().getName());
            row.createCell(c++).setCellValue(b.getCheckInDate().format(DATE));
            row.createCell(c++).setCellValue(b.getCheckOutDate().format(DATE));
            numberCell(row, c++, ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()), styles.integer);
            row.createCell(c++).setCellValue(b.getCombo() != null ? b.getCombo().getName() : "");
            numberCell(row, c++, b.getDiscountAmount(), styles.money);
            numberCell(row, c++, b.getTotalAmount(), styles.money);
            numberCell(row, c++, charge, styles.money);
            numberCell(row, c++, b.getTotalAmount().add(charge), styles.moneyBold);
            numberCell(row, c++, b.getDepositPaidAmount(), styles.money);
            row.createCell(c++).setCellValue(b.getStatus().getVietnameseLabel());
            row.createCell(c++).setCellValue(payment != null ? payment.getStatus().getVietnameseLabel() : "Chưa thanh toán");
            row.createCell(c++).setCellValue(payment != null && payment.getPaymentMethod() != null
                    ? payment.getPaymentMethod().getVietnameseLabel() : "");
            row.createCell(c).setCellValue(payment != null && payment.getPaymentDate() != null
                    ? payment.getPaymentDate().format(DATE_TIME) : "");
        }
        if (bookings.isEmpty()) {
            sheet.createRow(r).createCell(0).setCellValue("Không có đơn nào được tạo trong khoảng ngày này.");
        } else {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, r - 1, 0, BOOKING_HEADERS.length - 1));
        }
    }

    private int sectionHeader(Sheet sheet, Styles styles, int r, String text) {
        Cell c = sheet.createRow(r).createCell(0);
        c.setCellValue(text);
        c.setCellStyle(styles.section);
        return r + 1;
    }

    private int labelValue(Sheet sheet, int r, String label, Object value, CellStyle style) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        numberCell(row, 1, value, style);
        return r + 1;
    }

    private void numberCell(Row row, int col, Object value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0 : ((Number) value).doubleValue());
        c.setCellStyle(style);
    }

    private static class Styles {
        final CellStyle title, section, header, money, moneyBold, integer, percent;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(big);
            section = wb.createCellStyle();
            section.setFont(bold);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);
            money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0"));
            moneyBold = wb.createCellStyle();
            moneyBold.cloneStyleFrom(money);
            moneyBold.setFont(bold);
            integer = wb.createCellStyle();
            integer.setDataFormat(fmt.getFormat("0"));
            percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("0.0"));
        }
    }
}
