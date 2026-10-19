package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.Expense;
import com.hotel.entity.PaymentMethod;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Lai lo theo thang (don tich): doanh thu phong chia theo dem, dich vu theo ngay phat sinh, coc giu lai theo ngay huy;
// chi phi = gia von dich vu (%) + chi phi bien doi moi phong-dem + phi cong thanh toan + chi phi van hanh ke toan nhap
@Service
public class ProfitService {

    private static final Set<BookingStatus> SOLD = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);
    public static final int TREND_MONTHS = 12;

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository chargeRepository;
    private final RoomRepository roomRepository;
    private final ExpenseService expenseService;
    private final PaymentService paymentService;

    public ProfitService(BookingRepository bookingRepository, BookingChargeRepository chargeRepository, RoomRepository roomRepository,
                         ExpenseService expenseService, PaymentService paymentService) {
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
        this.roomRepository = roomRepository;
        this.expenseService = expenseService;
        this.paymentService = paymentService;
    }

    public record MonthPnl(YearMonth month,
                           BigDecimal roomRevenue, Map<ChargeType, BigDecimal> serviceRevenue, BigDecimal forfeitedDeposits,
                           Map<ChargeType, BigDecimal> serviceCost, BigDecimal variableRoomCost, BigDecimal paymentFees,
                           Map<Expense.Category, BigDecimal> expenses,
                           long soldRoomNights, long availableRoomNights) {

        public BigDecimal serviceRevenueTotal() {
            return sum(serviceRevenue.values());
        }

        public BigDecimal totalRevenue() {
            return roomRevenue.add(serviceRevenueTotal()).add(forfeitedDeposits);
        }

        public BigDecimal directCost() {
            return sum(serviceCost.values()).add(variableRoomCost).add(paymentFees);
        }

        // Lai gop = doanh thu - chi phi truc tiep (gia von, bien doi, phi thanh toan)
        public BigDecimal grossProfit() {
            return totalRevenue().subtract(directCost());
        }

        public BigDecimal expensesTotal() {
            return sum(expenses.values());
        }

        public BigDecimal totalCost() {
            return directCost().add(expensesTotal());
        }

        // Lai van hanh (GOP) = lai gop - chi phi van hanh
        public BigDecimal operatingProfit() {
            return totalRevenue().subtract(totalCost());
        }

        public double margin() {
            return totalRevenue().signum() == 0 ? 0
                    : operatingProfit().multiply(BigDecimal.valueOf(1000)).divide(totalRevenue(), 0, RoundingMode.HALF_UP).doubleValue() / 10;
        }

        public double occupancy() {
            return availableRoomNights == 0 ? 0 : Math.round(soldRoomNights * 1000.0 / availableRoomNights) / 10.0;
        }

        public BigDecimal adr() {
            return per(roomRevenue, soldRoomNights);
        }

        public BigDecimal revPar() {
            return per(roomRevenue, availableRoomNights);
        }

        // Lai van hanh tren moi phong-dem san co
        public BigDecimal goppar() {
            return per(operatingProfit(), availableRoomNights);
        }

        // Chi phi tren moi phong-dem co khach
        public BigDecimal cpor() {
            return per(totalCost(), soldRoomNights);
        }

        private static BigDecimal per(BigDecimal amount, long n) {
            return n == 0 ? BigDecimal.ZERO : amount.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
        }

        private static BigDecimal sum(Collection<BigDecimal> values) {
            return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // % thay doi so voi ky khac (null = ky khac bang 0)
    public record Delta(Double revenue, Double cost, Double profit, double marginPoints, double occupancyPoints) {
    }

    public record Report(MonthPnl current, MonthPnl previous, MonthPnl lastYear, Delta vsPrevious, Delta vsLastYear, List<MonthPnl> trend) {
    }

    @Transactional(readOnly = true)
    public Report report(YearMonth month) {
        // Chi nap du lieu cua cac thang can tinh: xu huong 12 thang + cung ky nam truoc
        YearMonth first = month.minusMonths(Math.max(TREND_MONTHS - 1, 12));
        List<Booking> bookings = periodBookings(first, month);
        List<BookingCharge> charges = periodCharges(first, month);
        long rooms = roomRepository.count();
        MonthPnl current = month(month, bookings, charges, rooms);
        MonthPnl previous = month(month.minusMonths(1), bookings, charges, rooms);
        MonthPnl lastYear = month(month.minusYears(1), bookings, charges, rooms);
        List<MonthPnl> trend = new ArrayList<>();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth m = month.minusMonths(i);
            trend.add(m.equals(month) ? current : m.equals(month.minusMonths(1)) ? previous : month(m, bookings, charges, rooms));
        }
        return new Report(current, previous, lastYear, delta(current, previous), delta(current, lastYear), trend);
    }

    @Transactional(readOnly = true)
    public MonthPnl month(YearMonth month) {
        return month(month, periodBookings(month, month), periodCharges(month, month), roomRepository.count());
    }

    private List<Booking> periodBookings(YearMonth first, YearMonth last) {
        LocalDate from = first.atDay(1);
        LocalDate to = last.plusMonths(1).atDay(1);
        return bookingRepository.findForPeriod(from, to, from.atStartOfDay(), to.atStartOfDay());
    }

    private List<BookingCharge> periodCharges(YearMonth first, YearMonth last) {
        return chargeRepository.findCreatedBetween(first.atDay(1).atStartOfDay(), last.plusMonths(1).atDay(1).atStartOfDay());
    }

    private MonthPnl month(YearMonth month, List<Booking> bookings, List<BookingCharge> charges, long rooms) {
        LocalDate from = month.atDay(1);
        LocalDate toExclusive = month.plusMonths(1).atDay(1);
        long sold = 0;
        BigDecimal roomRevenue = BigDecimal.ZERO;
        BigDecimal forfeited = BigDecimal.ZERO;
        for (Booking b : bookings) {
            if (SOLD.contains(b.getStatus())) {
                long nights = ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate());
                LocalDate start = b.getCheckInDate().isAfter(from) ? b.getCheckInDate() : from;
                LocalDate end = b.getCheckOutDate().isBefore(toExclusive) ? b.getCheckOutDate() : toExclusive;
                long inMonth = ChronoUnit.DAYS.between(start, end);
                if (nights > 0 && inMonth > 0) {
                    sold += inMonth;
                    roomRevenue = roomRevenue.add(b.getTotalAmount().multiply(BigDecimal.valueOf(inMonth))
                            .divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP));
                }
            } else if (b.getStatus() == BookingStatus.CANCELLED && b.getCancelledAt() != null
                    && YearMonth.from(b.getCancelledAt()).equals(month)) {
                forfeited = forfeited.add(b.getForfeitedDepositAmount());
            }
        }
        Map<ChargeType, BigDecimal> serviceRevenue = new EnumMap<>(ChargeType.class);
        for (BookingCharge c : charges) {
            if (c.getCreatedAt() != null && YearMonth.from(c.getCreatedAt()).equals(month)
                    && c.getBooking().getStatus() != BookingStatus.CANCELLED) {
                serviceRevenue.merge(c.getType(), c.getAmount(), BigDecimal::add);
            }
        }
        Map<ChargeType, Integer> rates = expenseService.costRates();
        Map<ChargeType, BigDecimal> serviceCost = new EnumMap<>(ChargeType.class);
        serviceRevenue.forEach((type, amount) -> {
            int pct = rates.getOrDefault(type, 0);
            if (pct > 0) {
                serviceCost.put(type, amount.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
            }
        });
        BigDecimal online = paymentService.ledger(from.atStartOfDay(), toExclusive.atStartOfDay().minusNanos(1)).stream()
                .filter(e -> e.method() == PaymentMethod.ONLINE && e.amount().signum() > 0)
                .map(PaymentService.LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fees = online.multiply(expenseService.paymentFeePercent()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal variable = expenseService.variableCostPerNight().multiply(BigDecimal.valueOf(sold));
        return new MonthPnl(month, roomRevenue, serviceRevenue, forfeited, serviceCost, variable, fees,
                expenseService.totalsByCategory(month), sold, rooms * month.lengthOfMonth());
    }

    static Delta delta(MonthPnl a, MonthPnl b) {
        return new Delta(pct(a.totalRevenue(), b.totalRevenue()), pct(a.totalCost(), b.totalCost()),
                pct(a.operatingProfit(), b.operatingProfit()),
                Math.round((a.margin() - b.margin()) * 10) / 10.0, Math.round((a.occupancy() - b.occupancy()) * 10) / 10.0);
    }

    static Double pct(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return null;
        }
        return a.subtract(b).multiply(BigDecimal.valueOf(100)).divide(b.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }

    // ===== Excel =====

    @Transactional(readOnly = true)
    public byte[] excel(YearMonth month) {
        Report r = report(month);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            Sheet s = wb.createSheet("Lai lo " + month);
            int[] row = {0};
            String[] head = {"Khoản mục", "Tháng " + month, "Tháng trước", "Cùng kỳ năm trước"};
            Row h = s.createRow(row[0]++);
            for (int i = 0; i < head.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(head[i]);
                c.setCellStyle(bold);
            }
            MonthPnl[] cols = {r.current(), r.previous(), r.lastYear()};
            line(s, row, "Doanh thu phòng", bold, money, false, Arrays.stream(cols).map(MonthPnl::roomRevenue).toArray(BigDecimal[]::new));
            for (ChargeType t : ChargeType.values()) {
                if (Arrays.stream(cols).anyMatch(p -> p.serviceRevenue().containsKey(t))) {
                    line(s, row, "Dịch vụ: " + t.getVietnameseLabel(), bold, money, false,
                            Arrays.stream(cols).map(p -> p.serviceRevenue().getOrDefault(t, BigDecimal.ZERO)).toArray(BigDecimal[]::new));
                }
            }
            line(s, row, "Cọc giữ lại (khách hủy / không đến)", bold, money, false, Arrays.stream(cols).map(MonthPnl::forfeitedDeposits).toArray(BigDecimal[]::new));
            line(s, row, "TỔNG DOANH THU", bold, money, true, Arrays.stream(cols).map(MonthPnl::totalRevenue).toArray(BigDecimal[]::new));
            line(s, row, "Giá vốn dịch vụ", bold, money, false, Arrays.stream(cols).map(p -> p.serviceCost().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).toArray(BigDecimal[]::new));
            line(s, row, "Chi phí biến đổi phòng", bold, money, false, Arrays.stream(cols).map(MonthPnl::variableRoomCost).toArray(BigDecimal[]::new));
            line(s, row, "Phí cổng thanh toán", bold, money, false, Arrays.stream(cols).map(MonthPnl::paymentFees).toArray(BigDecimal[]::new));
            line(s, row, "LÃI GỘP", bold, money, true, Arrays.stream(cols).map(MonthPnl::grossProfit).toArray(BigDecimal[]::new));
            for (Expense.Category c : Expense.Category.values()) {
                if (Arrays.stream(cols).anyMatch(p -> p.expenses().containsKey(c))) {
                    line(s, row, "Chi phí: " + c.getLabel(), bold, money, false,
                            Arrays.stream(cols).map(p -> p.expenses().getOrDefault(c, BigDecimal.ZERO)).toArray(BigDecimal[]::new));
                }
            }
            line(s, row, "LÃI VẬN HÀNH (GOP)", bold, money, true, Arrays.stream(cols).map(MonthPnl::operatingProfit).toArray(BigDecimal[]::new));
            row[0]++;
            line(s, row, "ADR", bold, money, false, Arrays.stream(cols).map(MonthPnl::adr).toArray(BigDecimal[]::new));
            line(s, row, "RevPAR", bold, money, false, Arrays.stream(cols).map(MonthPnl::revPar).toArray(BigDecimal[]::new));
            line(s, row, "GOPPAR", bold, money, false, Arrays.stream(cols).map(MonthPnl::goppar).toArray(BigDecimal[]::new));
            line(s, row, "Chi phí / phòng-đêm có khách (CPOR)", bold, money, false, Arrays.stream(cols).map(MonthPnl::cpor).toArray(BigDecimal[]::new));
            Row occ = s.createRow(row[0]++);
            occ.createCell(0).setCellValue("Công suất (%)");
            Row mar = s.createRow(row[0]++);
            mar.createCell(0).setCellValue("Biên lợi nhuận (%)");
            for (int i = 0; i < cols.length; i++) {
                occ.createCell(i + 1).setCellValue(cols[i].occupancy());
                mar.createCell(i + 1).setCellValue(cols[i].margin());
            }
            for (int i = 0; i < 4; i++) {
                s.autoSizeColumn(i);
            }

            Sheet t = wb.createSheet("Xu huong 12 thang");
            String[] th = {"Tháng", "Doanh thu", "Chi phí", "Lãi vận hành", "Biên (%)", "Công suất (%)", "ADR"};
            Row tr = t.createRow(0);
            for (int i = 0; i < th.length; i++) {
                Cell c = tr.createCell(i);
                c.setCellValue(th[i]);
                c.setCellStyle(bold);
            }
            int i = 1;
            for (MonthPnl p : r.trend()) {
                Row x = t.createRow(i++);
                x.createCell(0).setCellValue(p.month().toString());
                cell(x, 1, p.totalRevenue(), money);
                cell(x, 2, p.totalCost(), money);
                cell(x, 3, p.operatingProfit(), money);
                x.createCell(4).setCellValue(p.margin());
                x.createCell(5).setCellValue(p.occupancy());
                cell(x, 6, p.adr(), money);
            }
            for (int c = 0; c < th.length; c++) {
                t.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void line(Sheet s, int[] row, String label, CellStyle bold, CellStyle money, boolean strong, BigDecimal... values) {
        Row r = s.createRow(row[0]++);
        Cell l = r.createCell(0);
        l.setCellValue(label);
        if (strong) {
            l.setCellStyle(bold);
        }
        for (int i = 0; i < values.length; i++) {
            cell(r, i + 1, values[i], money);
        }
    }

    private static void cell(Row r, int col, BigDecimal v, CellStyle money) {
        Cell c = r.createCell(col);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(money);
    }
}
