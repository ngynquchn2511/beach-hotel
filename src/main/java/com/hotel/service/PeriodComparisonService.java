package com.hotel.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// So sanh ky dang xem voi ky lien truoc (cung so ngay) va cung ky nam truoc: tien da thu, cong suat, ADR, RevPAR, so don
@Service
public class PeriodComparisonService {

    public record Snapshot(LocalDate from, LocalDate to, BigDecimal paidTotal, double occupancy, BigDecimal adr,
                           BigDecimal revPar, long bookings) {
    }

    // Chenh lech so voi ky hien tai: % thay doi (null neu ky so sanh = 0); cong suat tinh bang diem %
    public record Delta(Double paidPercent, double occupancyPoints, Double adrPercent, Double revParPercent, Double bookingsPercent) {
    }

    public record Row(String label, Snapshot snapshot, Delta delta) {
    }

    public record Comparison(Snapshot current, List<Row> rows) {
    }

    private final BookingService bookingService;
    private final PaymentService paymentService;

    public PeriodComparisonService(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public Comparison compare(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        Snapshot current = snapshot(from, to);
        Snapshot previous = snapshot(from.minusDays(days), from.minusDays(1));
        Snapshot lastYear = snapshot(from.minusYears(1), to.minusYears(1));
        return new Comparison(current, List.of(
                new Row("Kỳ trước", previous, delta(current, previous)),
                new Row("Cùng kỳ năm trước", lastYear, delta(current, lastYear))));
    }

    public Snapshot snapshot(LocalDate from, LocalDate to) {
        BigDecimal paid = paymentService.getPaidRevenueByMethod(from, to).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BookingService.HotelKpi kpi = bookingService.getHotelKpi(from, to);
        long bookings = bookingService.findBookingsCreatedBetween(from, to).size();
        return new Snapshot(from, to, paid, kpi.occupancyRate(), kpi.adr(), kpi.revPar(), bookings);
    }

    static Delta delta(Snapshot current, Snapshot other) {
        return new Delta(percent(current.paidTotal(), other.paidTotal()),
                Math.round((current.occupancy() - other.occupancy()) * 10) / 10.0,
                percent(current.adr(), other.adr()), percent(current.revPar(), other.revPar()),
                percent(BigDecimal.valueOf(current.bookings()), BigDecimal.valueOf(other.bookings())));
    }

    static Double percent(BigDecimal current, BigDecimal other) {
        if (other == null || other.signum() == 0) {
            return null;
        }
        return current.subtract(other).multiply(BigDecimal.valueOf(100)).divide(other.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
