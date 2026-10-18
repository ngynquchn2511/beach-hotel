package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingChannel;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.User;
import com.hotel.repository.BookingRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

// Bao cao huy / khong den: theo thang dat (cohort), theo kenh dat, tien coc giu lai, huy sat ngay, khach huy nhieu lan
@Service
public class CancellationReportService {

    // Huy trong vong 48h truoc gio nhan phong = huy sat ngay
    public static final int LATE_CANCEL_HOURS = 48;
    public static final int REPEAT_THRESHOLD = 2;

    private final BookingRepository bookingRepository;

    public CancellationReportService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public record Line(String label, long created, long cancelled, long noShows, long lateCancels, BigDecimal forfeited) {
        public double cancelRate() {
            return rate(cancelled);
        }

        public double noShowRate() {
            return rate(noShows);
        }

        private double rate(long n) {
            return created == 0 ? 0 : Math.round(1000.0 * n / created) / 10.0;
        }
    }

    public record RepeatGuest(User customer, long cancelled, long noShows, Booking last) {
        public long total() {
            return cancelled + noShows;
        }
    }

    public record Report(YearMonth from, YearMonth to, List<Line> months, List<Line> channels, Line total, List<RepeatGuest> repeatGuests) {
    }

    @Transactional(readOnly = true)
    public Report build(YearMonth from, YearMonth to) {
        LocalDateTime start = from.atDay(1).atStartOfDay();
        LocalDateTime end = to.plusMonths(1).atDay(1).atStartOfDay();
        List<Booking> created = bookingRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> b.getCreatedAt() != null && !b.getCreatedAt().isBefore(start) && b.getCreatedAt().isBefore(end))
                .toList();

        List<Line> months = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            YearMonth month = m;
            months.add(row(month.getMonthValue() + "/" + month.getYear(),
                    created.stream().filter(b -> YearMonth.from(b.getCreatedAt()).equals(month)).toList()));
        }
        List<Line> channels = new ArrayList<>();
        for (BookingChannel c : BookingChannel.values()) {
            channels.add(row(c.getVietnameseLabel(), created.stream().filter(b -> b.getEffectiveChannel() == c).toList()));
        }

        Map<Long, long[]> perCustomer = new HashMap<>();
        Map<Long, Booking> lastOf = new HashMap<>();
        for (Booking b : created) {
            if (b.getStatus() != BookingStatus.CANCELLED || b.getCustomer() == null || isWalkInPlaceholder(b.getCustomer())) {
                continue;
            }
            long[] c = perCustomer.computeIfAbsent(b.getCustomer().getId(), k -> new long[2]);
            c[b.isNoShow() ? 1 : 0]++;
            lastOf.merge(b.getCustomer().getId(), b, (a, x) -> a.getCreatedAt().isAfter(x.getCreatedAt()) ? a : x);
        }
        List<RepeatGuest> repeat = perCustomer.entrySet().stream()
                .filter(e -> e.getValue()[0] + e.getValue()[1] >= REPEAT_THRESHOLD)
                .map(e -> new RepeatGuest(lastOf.get(e.getKey()).getCustomer(), e.getValue()[0], e.getValue()[1], lastOf.get(e.getKey())))
                .sorted(Comparator.comparing(RepeatGuest::total).reversed().thenComparing(g -> g.customer().getFullName()))
                .toList();
        return new Report(from, to, months, channels, row("Tổng", created), repeat);
    }

    private static Line row(String label, List<Booking> bookings) {
        List<Booking> closed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).toList();
        long noShows = closed.stream().filter(Booking::isNoShow).count();
        long late = closed.stream().filter(b -> !b.isNoShow() && isLateCancel(b)).count();
        BigDecimal forfeited = closed.stream().map(Booking::getForfeitedDepositAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Line(label, bookings.size(), closed.size() - noShows, noShows, late, forfeited);
    }

    static boolean isLateCancel(Booking b) {
        if (b.getCancelledAt() == null) {
            return false;
        }
        LocalDateTime checkIn = LocalDateTime.of(b.getCheckInDate(), b.getCheckInTime());
        return Duration.between(b.getCancelledAt(), checkIn).toHours() < LATE_CANCEL_HOURS;
    }

    private static boolean isWalkInPlaceholder(User u) {
        return u.getEmail() != null && u.getEmail().endsWith("@khachvanglai.local");
    }

    @Transactional(readOnly = true)
    public byte[] excel(YearMonth from, YearMonth to) {
        Report r = build(from, to);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            String[] head = {"", "Đơn đặt", "Hủy", "Tỷ lệ hủy (%)", "Không đến", "Tỷ lệ không đến (%)", "Hủy sát ngày (<48h)", "Cọc giữ lại (VND)"};
            sheet(wb.createSheet("Theo thang"), head, r.months(), r.total(), bold);
            sheet(wb.createSheet("Theo kenh"), head, r.channels(), r.total(), bold);
            Sheet rep = wb.createSheet("Khach huy nhieu lan");
            String[] h2 = {"Khách", "Email", "SĐT", "Hủy", "Không đến", "Đơn gần nhất"};
            Row hr = rep.createRow(0);
            for (int i = 0; i < h2.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(h2[i]);
                c.setCellStyle(bold);
            }
            int i = 1;
            for (RepeatGuest g : r.repeatGuests()) {
                Row row = rep.createRow(i++);
                row.createCell(0).setCellValue(g.customer().getFullName());
                row.createCell(1).setCellValue(g.customer().getEmail());
                row.createCell(2).setCellValue(g.customer().getPhoneNumber() != null ? g.customer().getPhoneNumber() : "");
                row.createCell(3).setCellValue(g.cancelled());
                row.createCell(4).setCellValue(g.noShows());
                row.createCell(5).setCellValue("#" + g.last().getId());
            }
            for (int c = 0; c < h2.length; c++) {
                rep.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void sheet(Sheet sheet, String[] head, List<CancellationReportService.Line> rows, CancellationReportService.Line total, CellStyle bold) {
        Row hr = sheet.createRow(0);
        for (int i = 0; i < head.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(head[i]);
            c.setCellStyle(bold);
        }
        int r = 1;
        for (CancellationReportService.Line row : rows) {
            fill(sheet.createRow(r++), row);
        }
        Row t = sheet.createRow(r);
        fill(t, total);
        t.getCell(0).setCellStyle(bold);
        for (int i = 0; i < head.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void fill(Row x, CancellationReportService.Line row) {
        x.createCell(0).setCellValue(row.label());
        x.createCell(1).setCellValue(row.created());
        x.createCell(2).setCellValue(row.cancelled());
        x.createCell(3).setCellValue(row.cancelRate());
        x.createCell(4).setCellValue(row.noShows());
        x.createCell(5).setCellValue(row.noShowRate());
        x.createCell(6).setCellValue(row.lateCancels());
        x.createCell(7).setCellValue(row.forfeited().doubleValue());
    }
}
