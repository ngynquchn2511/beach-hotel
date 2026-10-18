package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.CashShift;
import com.hotel.entity.PaymentMethod;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.CashShiftRepository;
import com.hotel.repository.UserRepository;
import com.hotel.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Chot ngay (night audit): so lieu 1 ngay cho quan ly - cong suat, khach den / di, no-show, thu chi, ca truc, viec ton
@Service
public class DailyReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record ShiftLine(CashShift shift, BigDecimal difference) {
    }

    public record Pending(long bookingsToConfirm, long refunds, long stayTimeRequests, long facilities, long roomService,
                          long maintenance) {
        public long total() {
            return bookingsToConfirm + refunds + stayTimeRequests + facilities + roomService + maintenance;
        }
    }

    public record DailyReport(LocalDate day, BookingService.HotelKpi kpi, int arrivalsExpected, long arrivalsDone,
                              int departuresExpected, long departuresDone, int inHouse, List<Booking> noShowsMarked,
                              List<Booking> stillNotArrived, int newBookings, BigDecimal newBookingsValue, List<Booking> cancellations,
                              Map<PaymentMethod, BigDecimal> income, BigDecimal refunds, BigDecimal net,
                              List<ShiftLine> shifts, Pending pending) {
        public BigDecimal shiftDifferenceTotal() {
            return shifts.stream().map(ShiftLine::difference).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private final BookingService bookingService;
    private final FrontDeskService frontDeskService;
    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final CashShiftRepository cashShiftRepository;
    private final FacilityReservationService facilityService;
    private final RoomServiceService roomServiceService;
    private final MaintenanceService maintenanceService;
    private final UserRepository userRepository;

    @Value("${app.daily-report.recipients:}")
    private String recipients;

    public DailyReportService(BookingService bookingService, FrontDeskService frontDeskService, PaymentService paymentService,
                              BookingRepository bookingRepository, CashShiftRepository cashShiftRepository,
                              FacilityReservationService facilityService, RoomServiceService roomServiceService,
                              MaintenanceService maintenanceService, UserRepository userRepository) {
        this.bookingService = bookingService;
        this.frontDeskService = frontDeskService;
        this.paymentService = paymentService;
        this.bookingRepository = bookingRepository;
        this.cashShiftRepository = cashShiftRepository;
        this.facilityService = facilityService;
        this.roomServiceService = roomServiceService;
        this.maintenanceService = maintenanceService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DailyReport build(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay().minusNanos(1);
        FrontDeskService.Day desk = frontDeskService.day(day, LocalDate.now());
        List<Booking> cancelledToday = bookingRepository.findByCancelledAtBetween(from, to);
        List<Booking> created = bookingService.findBookingsCreatedBetween(day, day);
        List<PaymentService.LedgerEntry> ledger = paymentService.ledger(from, to);
        BigDecimal refunds = ledger.stream().filter(e -> e.amount().signum() < 0).map(e -> e.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<PaymentMethod, BigDecimal> income = PaymentService.totalsByMethod(ledger.stream().filter(e -> e.amount().signum() > 0).toList());
        BigDecimal net = ledger.stream().map(PaymentService.LedgerEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ShiftLine> shifts = cashShiftRepository.findAllForList().stream()
                .filter(s -> s.getClosedAt() != null && !s.getClosedAt().isBefore(from) && !s.getClosedAt().isAfter(to))
                .map(s -> new ShiftLine(s, s.getCashDifference())).toList();
        Pending pending = new Pending(bookingRepository.findByStatusOrderByCreatedAtDesc(BookingStatus.PENDING).size(),
                bookingService.countPendingRefunds(), bookingService.countPendingStayTimeRequests(), facilityService.countPending(),
                roomServiceService.countPending(), maintenanceService.countOpen());
        return new DailyReport(day, bookingService.getHotelKpi(day, day),
                desk.arrivals().size(), desk.arrivals().stream().filter(FrontDeskService.Entry::isDone).count(),
                desk.departures().size(), desk.departures().stream().filter(e -> e.booking().getStatus() == BookingStatus.CHECKED_OUT).count(),
                desk.inHouse().size(),
                cancelledToday.stream().filter(Booking::isNoShow).toList(),
                bookingService.overdueArrivals(to),
                created.size(), created.stream().map(Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                cancelledToday.stream().filter(b -> !b.isNoShow()).toList(),
                income, refunds, net, shifts, pending);
    }

    // Nguoi nhan: app.daily-report.recipients (cach nhau dau phay); trong -> email cac tai khoan ADMIN
    public List<String> recipients() {
        if (recipients != null && !recipients.isBlank()) {
            return Arrays.stream(recipients.split(",")).map(String::trim).filter(s -> s.contains("@")).distinct().toList();
        }
        return userRepository.findAll().stream().filter(u -> u.getRole() == UserRole.ADMIN).map(User::getEmail)
                .filter(e -> e != null && e.contains("@")).distinct().toList();
    }

    // Noi dung email dang van ban (doc tot tren dien thoai)
    public static String text(DailyReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("BÁO CÁO CUỐI NGÀY ").append(r.day().format(DAY)).append(" - Sóng Xanh Resort\n\n");
        sb.append("PHÒNG\n");
        sb.append("- Công suất: ").append(r.kpi().occupancyRate()).append("% (")
                .append(r.kpi().soldRoomNights()).append("/").append(r.kpi().availableRoomNights()).append(" phòng)\n");
        sb.append("- ADR: ").append(Money.format(r.kpi().adr())).append(" VND · RevPAR: ").append(Money.format(r.kpi().revPar())).append(" VND\n");
        sb.append("- Khách đến: ").append(r.arrivalsDone()).append("/").append(r.arrivalsExpected())
                .append(" · Khách đi: ").append(r.departuresDone()).append("/").append(r.departuresExpected())
                .append(" · Đang ở: ").append(r.inHouse()).append("\n");
        sb.append("- Không đến (đã đánh dấu): ").append(r.noShowsMarked().size())
                .append(" · Quá giờ nhận mà chưa đến: ").append(r.stillNotArrived().size()).append("\n\n");
        sb.append("ĐẶT PHÒNG\n");
        sb.append("- Đơn mới: ").append(r.newBookings()).append(" (").append(Money.format(r.newBookingsValue())).append(" VND)")
                .append(" · Hủy: ").append(r.cancellations().size()).append("\n\n");
        sb.append("THU CHI\n");
        sb.append("- Tiền mặt: ").append(Money.format(r.income().get(PaymentMethod.CASH))).append(" VND\n");
        sb.append("- Chuyển khoản: ").append(Money.format(r.income().get(PaymentMethod.BANK_TRANSFER))).append(" VND\n");
        sb.append("- Online (VNPay): ").append(Money.format(r.income().get(PaymentMethod.ONLINE))).append(" VND\n");
        sb.append("- Hoàn cọc: -").append(Money.format(r.refunds())).append(" VND\n");
        sb.append("- Thực thu: ").append(Money.format(r.net())).append(" VND\n");
        if (!r.shifts().isEmpty()) {
            sb.append("- Ca trực đóng trong ngày: ").append(r.shifts().size()).append(", chênh lệch tiền mặt ")
                    .append(Money.format(r.shiftDifferenceTotal())).append(" VND\n");
        }
        sb.append("\nVIỆC CÒN TỒN\n");
        Pending p = r.pending();
        sb.append("- Đơn chờ xác nhận: ").append(p.bookingsToConfirm()).append(" · Hoàn cọc chờ chuyển: ").append(p.refunds()).append("\n");
        sb.append("- Nhận sớm / trả muộn chờ duyệt: ").append(p.stayTimeRequests()).append(" · Spa / bàn chờ xác nhận: ").append(p.facilities()).append("\n");
        sb.append("- Gọi lên phòng chờ giao: ").append(p.roomService()).append(" · Phiếu bảo trì chưa xong: ").append(p.maintenance()).append("\n");
        return sb.toString();
    }
}
