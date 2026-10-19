package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// Du bao doanh thu phong cac thang toi theo phuong phap "pickup":
//   du bao = da co tren so (OTB) + pickup du kien, pickup = (ket qua cuoi thang nam ngoai - OTB nam ngoai cung thoi diem) x toc do ban
//   toc do ban = OTB nam nay / OTB nam ngoai cung thoi diem (gioi han 0.5 - 2 de tranh so ao khi du lieu it)
// Khong co du lieu nam ngoai -> du bao = OTB (muc san), do tin cay thap
@Service
public class RevenueForecastService {

    public static final int MONTHS_AHEAD = 3;
    private static final Set<BookingStatus> SOLD = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);
    static final double MIN_PACE = 0.5;
    static final double MAX_PACE = 2.0;

    public enum Confidence {
        HIGH("Cao"), MEDIUM("Trung bình"), LOW("Thấp - chưa có dữ liệu cùng kỳ");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Stay(BigDecimal revenue, long nights) {
        static final Stay ZERO = new Stay(BigDecimal.ZERO, 0);

        Stay plus(Stay o) {
            return new Stay(revenue.add(o.revenue), nights + o.nights);
        }
    }

    public record MonthForecast(YearMonth month, Stay onTheBooks, Stay lastYearAtSameLead, Stay lastYearFinal, double pace,
                                BigDecimal forecastRevenue, double forecastOccupancy, double otbOccupancy, long availableRoomNights,
                                Confidence confidence, BigDecimal revenueTarget) {
        public Double forecastVsTargetPercent() {
            return revenueTarget == null || revenueTarget.signum() == 0 ? null
                    : forecastRevenue.multiply(BigDecimal.valueOf(1000)).divide(revenueTarget, 0, RoundingMode.HALF_UP).doubleValue() / 10;
        }

        public BigDecimal getPickup() {
            return forecastRevenue.subtract(onTheBooks.revenue()).max(BigDecimal.ZERO);
        }
    }

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final BudgetService budgetService;

    public RevenueForecastService(BookingRepository bookingRepository, RoomRepository roomRepository, BudgetService budgetService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.budgetService = budgetService;
    }

    // Thang hien tai + MONTHS_AHEAD - 1 thang tiep theo
    @Transactional(readOnly = true)
    public List<MonthForecast> forecast(LocalDate today) {
        // Chi can cac thang du bao va cung ky nam truoc
        YearMonth first = YearMonth.from(today);
        YearMonth last = first.plusMonths(MONTHS_AHEAD - 1);
        List<Booking> all = new ArrayList<>(window(first, last));
        all.addAll(window(first.minusYears(1), last.minusYears(1)));
        long rooms = roomRepository.count();
        List<MonthForecast> list = new ArrayList<>();
        for (int i = 0; i < MONTHS_AHEAD; i++) {
            list.add(forecastMonth(YearMonth.from(today).plusMonths(i), today, all, rooms));
        }
        return list;
    }

    private List<Booking> window(YearMonth first, YearMonth last) {
        LocalDate from = first.atDay(1);
        LocalDate to = last.plusMonths(1).atDay(1);
        return bookingRepository.findForPeriod(from, to, from.atStartOfDay(), to.atStartOfDay());
    }

    MonthForecast forecastMonth(YearMonth m, LocalDate today, List<Booking> all, long rooms) {
        LocalDateTime now = today.atTime(23, 59, 59);
        LocalDateTime lyNow = now.minusYears(1);
        YearMonth ly = m.minusYears(1);
        Stay otb = stay(m, all, activeAt(now));
        Stay lyAtLead = stay(ly, all, activeAt(lyNow));
        Stay lyFinal = stay(ly, all, b -> SOLD.contains(b.getStatus()));
        long available = rooms * m.lengthOfMonth();

        double pace = 1;
        Confidence confidence;
        BigDecimal forecast;
        if (lyFinal.revenue().signum() == 0) {
            confidence = Confidence.LOW;
            forecast = otb.revenue();
        } else {
            if (lyAtLead.revenue().signum() > 0) {
                pace = otb.revenue().divide(lyAtLead.revenue(), 4, RoundingMode.HALF_UP).doubleValue();
                pace = Math.max(MIN_PACE, Math.min(MAX_PACE, pace));
            }
            BigDecimal pickup = lyFinal.revenue().subtract(lyAtLead.revenue()).max(BigDecimal.ZERO);
            forecast = otb.revenue().add(pickup.multiply(BigDecimal.valueOf(pace)).setScale(0, RoundingMode.HALF_UP));
            confidence = lyFinal.nights() >= 20 ? Confidence.HIGH : Confidence.MEDIUM;
        }
        // Cong suat du bao: dem phong ty le theo doanh thu (ADR nhu da co tren so, hoac nam ngoai neu chua co don)
        BigDecimal adr = otb.nights() > 0 ? otb.revenue().divide(BigDecimal.valueOf(otb.nights()), 0, RoundingMode.HALF_UP)
                : lyFinal.nights() > 0 ? lyFinal.revenue().divide(BigDecimal.valueOf(lyFinal.nights()), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        long forecastNights = adr.signum() == 0 ? otb.nights() : forecast.divide(adr, 0, RoundingMode.HALF_UP).longValue();
        forecastNights = Math.min(forecastNights, available);
        return new MonthForecast(m, otb, lyAtLead, lyFinal, Math.round(pace * 100) / 100.0, forecast,
                pct(forecastNights, available), pct(otb.nights(), available), available, confidence,
                budgetService.of(m).map(b -> b.getRevenueTarget()).orElse(null));
    }

    // Don con hieu luc tai thoi diem t: da tao truoc t, va (chua huy hoac huy sau t); khong tinh don dang cho xac nhan
    static Predicate<Booking> activeAt(LocalDateTime t) {
        return b -> b.getCreatedAt() != null && !b.getCreatedAt().isAfter(t)
                && (SOLD.contains(b.getStatus())
                || (b.getStatus() == BookingStatus.CANCELLED && b.getCancelledAt() != null && b.getCancelledAt().isAfter(t)));
    }

    // Doanh thu phong + so dem cua cac don thoa dieu kien, chia theo dem nam trong thang m
    static Stay stay(YearMonth m, List<Booking> all, Predicate<Booking> include) {
        LocalDate from = m.atDay(1);
        LocalDate to = m.plusMonths(1).atDay(1);
        Stay total = Stay.ZERO;
        for (Booking b : all) {
            if (!include.test(b)) {
                continue;
            }
            long nights = ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate());
            LocalDate s = b.getCheckInDate().isAfter(from) ? b.getCheckInDate() : from;
            LocalDate e = b.getCheckOutDate().isBefore(to) ? b.getCheckOutDate() : to;
            long in = ChronoUnit.DAYS.between(s, e);
            if (nights > 0 && in > 0) {
                total = total.plus(new Stay(b.getTotalAmount().multiply(BigDecimal.valueOf(in))
                        .divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP), in));
            }
        }
        return total;
    }

    private static double pct(long n, long d) {
        return d == 0 ? 0 : Math.round(n * 1000.0 / d) / 10.0;
    }
}
