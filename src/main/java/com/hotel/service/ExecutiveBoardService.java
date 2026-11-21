package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.Review;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ReviewRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Bang dieu hanh cho quan ly: nhin 1 trang biet ngay viec can xu ly, tinh hinh hom nay, so do phong,
// 14 ngay toi, lai lo thang nay, danh gia khach, nhan su. Chi doc - gom so lieu tu cac service san co.
@Service
public class ExecutiveBoardService {

    public static final int FORECAST_DAYS = 14;
    public static final int REVIEW_DAYS = 30;
    // Danh gia tu muc nay tro xuong coi la "khach khong hai long"
    public static final int LOW_RATING = 3;

    // 1 viec can xu ly: muc do (danger / warning / info), icon bootstrap, noi dung, so luong, link toi trang xu ly
    public record Alert(String level, String icon, String label, long count, String link) {
    }

    // 1 o trong so do phong
    public record RoomTile(Room room, RoomStatus status, Booking guest, boolean arrivingToday, boolean leavingToday,
                           boolean overdue) {
        public String getCss() {
            if (overdue) {
                return "overdue";
            }
            return switch (status) {
                case OCCUPIED -> "occupied";
                case NOT_READY -> "dirty";
                case MAINTENANCE -> "maintenance";
                case BOOKED -> "booked";
                default -> "free";
            };
        }

        // Trang thai hien thi (moi phong dung 1 nhom): quá hạn > có khách > khách đến hôm nay > chờ dọn > bảo trì > trống
        public String getState() {
            if (overdue) {
                return "overdue";
            }
            if (status == RoomStatus.MAINTENANCE) {
                return "maintenance";
            }
            if (guest != null && !arrivingToday) {
                return "occupied";
            }
            if (status == RoomStatus.OCCUPIED) {
                return "occupied";
            }
            if (arrivingToday) {
                return "arriving";
            }
            if (status == RoomStatus.NOT_READY) {
                return "dirty";
            }
            return "free";
        }

        // So ngay qua han tra phong (0 neu khong qua han)
        public long getOverdueDays() {
            return overdue ? java.time.temporal.ChronoUnit.DAYS.between(guest.getCheckOutDate(), LocalDate.now()) : 0;
        }

        // Ten ngan cua khach: "Nguyễn Văn An" -> "Văn An" (vua o)
        public String getShortGuest() {
            if (guest == null || guest.getGuestName() == null || guest.getGuestName().isBlank()) {
                return null;
            }
            String[] p = guest.getGuestName().trim().split("\\s+");
            return p.length <= 2 ? String.join(" ", p) : p[p.length - 2] + " " + p[p.length - 1];
        }

        // Tang: "305" -> "3", "1201" -> "12", "B01" -> "B"
        public String getFloor() {
            String n = room.getRoomNumber() == null ? "" : room.getRoomNumber().trim();
            if (n.matches("\\d{3,}")) {
                return n.substring(0, n.length() - 2);
            }
            return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
        }
    }

    // Nhom o phong theo tang (giu thu tu)
    public static Map<String, List<RoomTile>> byFloor(List<RoomTile> rooms) {
        Map<String, List<RoomTile>> out = new java.util.LinkedHashMap<>();
        rooms.forEach(t -> out.computeIfAbsent(t.getFloor(), k -> new java.util.ArrayList<>()).add(t));
        return out;
    }

    public static String stateLabel(String state) {
        return switch (state) {
            case "free" -> "Trống";
            case "arriving" -> "Khách đến hôm nay";
            case "occupied" -> "Có khách";
            case "overdue" -> "Quá hạn trả";
            case "dirty" -> "Chờ dọn";
            case "maintenance" -> "Bảo trì";
            default -> state;
        };
    }

    public static String stateColor(String state) {
        return switch (state) {
            case "free" -> "#5cc283";
            case "arriving" -> "#3d9bd6";
            case "occupied" -> "#13395e";
            case "overdue" -> "#dc3545";
            case "dirty" -> "#f2c24d";
            default -> "#adb5bd";
        };
    }

    public static String stateIcon(String state) {
        return switch (state) {
            case "free" -> "bi-check-circle";
            case "arriving" -> "bi-box-arrow-in-right";
            case "occupied" -> "bi-person-fill";
            case "overdue" -> "bi-exclamation-triangle-fill";
            case "dirty" -> "bi-bucket";
            default -> "bi-tools";
        };
    }

    // Dem so phong theo trang thai hien thi (dung cho chu thich + thanh ti le)
    public static Map<String, Long> stateCounts(List<RoomTile> rooms) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (String s : List.of("free", "arriving", "occupied", "overdue", "dirty", "maintenance")) {
            out.put(s, rooms.stream().filter(t -> t.getState().equals(s)).count());
        }
        return out;
    }

    public record Today(double occupancy, long soldRooms, long sellableRooms, int arrivals, long arrivalsDone,
                        int departures, long departuresDone, int inHouse, BigDecimal collected, BigDecimal refunded,
                        int newBookings, BigDecimal newBookingsValue, int cancellations) {
    }

    public record Reviews(double average, long count, long unreplied, long low, List<Review> latestLow) {
    }

    // Lai lo thang nay so voi thang truoc + ke hoach (neu da dat) + luong thang truoc chua tra
    public record Finance(ProfitService.Report pnl, BudgetService.Variance budget, BigDecimal payrollDueLastMonth,
                          long payrollUnpaidStaff, YearMonth lastMonth) {
    }

    public record Board(LocalDateTime generatedAt, List<Alert> alerts, Today today, List<RoomTile> rooms,
                        Map<RoomStatus, Long> roomCounts, ForecastService.Forecast forecast, Reviews reviews,
                        Finance finance, List<String> shiftGaps) {
        public long getUrgentCount() {
            return alerts.stream().filter(a -> "danger".equals(a.level())).mapToLong(Alert::count).sum();
        }

        public long getAlertTotal() {
            return alerts.stream().mapToLong(Alert::count).sum();
        }
    }

    private final DailyReportService dailyReportService;
    private final FrontDeskService frontDeskService;
    private final ForecastService forecastService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final MaintenanceService maintenanceService;

    // Phu thuoc phu: thieu thi bo qua muc tuong ung
    private ServiceOrderService serviceOrderService;
    private ContactMessageService contactMessageService;
    private BookingMessageService messageService;
    private CallReminderService callReminderService;
    private ProfitService profitService;
    private BudgetService budgetService;
    private PayrollService payrollService;
    private StaffShiftService staffShiftService;

    public ExecutiveBoardService(DailyReportService dailyReportService, FrontDeskService frontDeskService,
                                 ForecastService forecastService, RoomRepository roomRepository,
                                 BookingRepository bookingRepository, ReviewRepository reviewRepository,
                                 MaintenanceService maintenanceService) {
        this.dailyReportService = dailyReportService;
        this.frontDeskService = frontDeskService;
        this.forecastService = forecastService;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.maintenanceService = maintenanceService;
    }

    @Autowired(required = false)
    void setServiceOrderService(ServiceOrderService s) { this.serviceOrderService = s; }

    @Autowired(required = false)
    void setContactMessageService(ContactMessageService s) { this.contactMessageService = s; }

    @Autowired(required = false)
    void setMessageService(BookingMessageService s) { this.messageService = s; }

    @Autowired(required = false)
    void setCallReminderService(CallReminderService s) { this.callReminderService = s; }

    @Autowired(required = false)
    void setProfitService(ProfitService s) { this.profitService = s; }

    @Autowired(required = false)
    void setBudgetService(BudgetService s) { this.budgetService = s; }

    @Autowired(required = false)
    void setPayrollService(PayrollService s) { this.payrollService = s; }

    @Autowired(required = false)
    void setStaffShiftService(StaffShiftService s) { this.staffShiftService = s; }

    // operations: hien viec van hanh (le tan / quan tri); finance: hien lai lo, luong (quan tri / ke toan)
    @Transactional(readOnly = true)
    public Board build(LocalDateTime now, boolean operations, boolean finance) {
        LocalDate today = now.toLocalDate();
        DailyReportService.DailyReport daily = dailyReportService.build(today);
        FrontDeskService.Day desk = frontDeskService.day(today, today);

        List<Alert> alerts = new ArrayList<>();
        if (operations) {
            operationAlerts(alerts, today, daily, desk);
        }
        Finance fin = finance ? finance(today) : null;
        if (fin != null && fin.payrollDueLastMonth().signum() > 0) {
            alerts.add(new Alert("warning", "bi-wallet2", "Nhân viên chưa nhận lương tháng " + fin.lastMonth().getMonthValue()
                    + " (" + com.hotel.util.Money.format(fin.payrollDueLastMonth()) + " VND)", fin.payrollUnpaidStaff(),
                    "/admin/payroll?month=" + fin.lastMonth()));
        }
        // Sap xep: do truoc, vang, roi xanh
        alerts.sort(Comparator.comparingInt(a -> switch (a.level()) {
            case "danger" -> 0;
            case "warning" -> 1;
            default -> 2;
        }));

        List<RoomTile> rooms = roomTiles(today, desk);
        Map<RoomStatus, Long> counts = new EnumMap<>(RoomStatus.class);
        for (RoomStatus s : RoomStatus.values()) {
            counts.put(s, 0L);
        }
        rooms.forEach(t -> counts.merge(t.status(), 1L, Long::sum));

        List<String> gaps = List.of();
        if (operations && staffShiftService != null) {
            gaps = upcomingShiftGaps(today);
        }

        return new Board(now, alerts, today(daily), rooms, counts, forecastService.forecast(today, FORECAST_DAYS),
                reviews(now), fin, gaps);
    }

    private void operationAlerts(List<Alert> alerts, LocalDate today, DailyReportService.DailyReport daily,
                                 FrontDeskService.Day desk) {
        DailyReportService.Pending p = daily.pending();
        add(alerts, "danger", "bi-door-open", "Khách quá hạn trả phòng chưa check-out", desk.overdueCheckOuts().size(), "/admin/today");
        add(alerts, "danger", "bi-person-x", "Khách quá ngày nhận phòng chưa đến (no-show?)", desk.noShows().size(), "/admin/today");
        add(alerts, "danger", "bi-tools", "Khách sắp đến nhưng phòng đang bảo trì",
                maintenanceService.arrivalsInBlockedRooms(today).size(), "/admin/maintenance");
        add(alerts, "danger", "bi-bucket", "Khách đến hôm nay mà phòng chưa sẵn sàng",
                desk.arrivals().stream().filter(FrontDeskService.Entry::isRoomNotReady).count(), "/admin/housekeeping");
        add(alerts, "warning", "bi-hourglass-split", "Đơn chờ xác nhận", p.bookingsToConfirm(), "/admin/bookings?status=PENDING");
        add(alerts, "warning", "bi-arrow-counterclockwise", "Đơn hủy chờ hoàn cọc", p.refunds(), "/admin/bookings?refund=pending");
        add(alerts, "warning", "bi-cash-coin", "Đơn hết hạn đặt cọc hôm nay, khách chưa cọc",
                bookingRepository.findDepositsDueBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()).size(),
                "/admin/deposits?view=due");
        add(alerts, "warning", "bi-clock-history", "Yêu cầu đổi giờ nhận / trả phòng", p.stayTimeRequests(), "/admin/today");
        add(alerts, "warning", "bi-wrench", "Phiếu báo hỏng chưa xử lý xong", p.maintenance(), "/admin/maintenance");
        long lowUnreplied = reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getRating() != null && r.getRating() <= LOW_RATING && !r.isReplied()).count();
        add(alerts, "warning", "bi-star-half", "Đánh giá thấp (≤ " + LOW_RATING + " sao) chưa trả lời", lowUnreplied,
                "/admin/reviews?replied=false");
        add(alerts, "info", "bi-cup-hot", "Gọi món lên phòng đang chờ giao", p.roomService(), "/admin/room-service");
        add(alerts, "info", "bi-calendar2-check", "Lịch spa / nhà hàng chờ xác nhận", p.facilities(), "/admin/facility-reservations");
        if (serviceOrderService != null) {
            add(alerts, "info", "bi-life-preserver", "Tour lặn / xe đưa đón chờ xác nhận", serviceOrderService.countPending(), "/admin/service-orders");
        }
        if (messageService != null) {
            add(alerts, "info", "bi-chat-dots", "Tin nhắn khách chưa đọc", messageService.unreadForStaff(), "/admin/messages");
        }
        if (contactMessageService != null) {
            add(alerts, "info", "bi-envelope", "Liên hệ mới chưa xử lý", contactMessageService.countNew(), "/admin/contact-messages");
        }
        if (callReminderService != null) {
            add(alerts, "info", "bi-telephone", "Khách cần gọi nhắc", callReminderService.countDue(), "/admin/call-reminders");
        }
    }

    private static void add(List<Alert> alerts, String level, String icon, String label, long count, String link) {
        if (count > 0) {
            alerts.add(new Alert(level, icon, label, count, link));
        }
    }

    private static Today today(DailyReportService.DailyReport d) {
        BigDecimal collected = d.income().values().stream().filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Today(d.kpi().occupancyRate(), d.kpi().soldRoomNights(), d.kpi().availableRoomNights(),
                d.arrivalsExpected(), d.arrivalsDone(), d.departuresExpected(), d.departuresDone(), d.inHouse(),
                collected, d.refunds(), d.newBookings(), d.newBookingsValue(), d.cancellations().size());
    }

    // So do phong: moi phong 1 o, kem khach dang o / khach den / di hom nay
    private List<RoomTile> roomTiles(LocalDate today, FrontDeskService.Day desk) {
        Map<Long, Booking> inHouse = new HashMap<>();
        desk.inHouse().forEach(b -> inHouse.put(b.getRoom().getId(), b));
        Map<Long, Booking> arriving = new HashMap<>();
        desk.arrivals().stream().filter(e -> !e.isDone()).forEach(e -> arriving.putIfAbsent(e.booking().getRoom().getId(), e.booking()));
        return roomRepository.findAll().stream()
                .sorted(Comparator.comparing(Room::getRoomNumber, ExecutiveBoardService::compareRoomNumber))
                .map(r -> {
                    Booking stay = inHouse.get(r.getId());
                    Booking arrival = arriving.get(r.getId());
                    boolean leaving = stay != null && stay.getCheckOutDate().equals(today);
                    boolean overdue = stay != null && stay.getCheckOutDate().isBefore(today);
                    return new RoomTile(r, r.getStatus(), stay != null ? stay : arrival, arrival != null, leaving, overdue);
                })
                .toList();
    }

    // "101" < "102" < "1001": so sanh theo so neu ca 2 la so, con lai theo chu
    public static int compareRoomNumber(String a, String b) {
        if (a != null && b != null && a.matches("\\d+") && b.matches("\\d+") && a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private Reviews reviews(LocalDateTime now) {
        LocalDateTime from = now.minusDays(REVIEW_DAYS);
        List<Review> all = reviewRepository.findAllByOrderByCreatedAtDesc();
        List<Review> recent = all.stream().filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(from)).toList();
        double avg = recent.stream().filter(r -> r.getRating() != null).mapToInt(Review::getRating).average().orElse(0);
        long unreplied = all.stream().filter(r -> !r.isReplied() && !r.isHidden()).count();
        List<Review> low = recent.stream().filter(r -> r.getRating() != null && r.getRating() <= LOW_RATING).toList();
        return new Reviews(Math.round(avg * 10) / 10.0, recent.size(), unreplied, low.size(), low.stream().limit(3).toList());
    }

    private Finance finance(LocalDate today) {
        if (profitService == null) {
            return null;
        }
        YearMonth month = YearMonth.from(today);
        ProfitService.Report report = profitService.report(month);
        BudgetService.Variance variance = budgetService == null ? null
                : budgetService.variance(report.current()).orElse(null);
        BigDecimal due = BigDecimal.ZERO;
        long unpaid = 0;
        if (payrollService != null) {
            PayrollService.Sheet sheet = payrollService.sheet(month.minusMonths(1));
            due = Optional.ofNullable(sheet.totalDue()).orElse(BigDecimal.ZERO);
            unpaid = sheet.lines().stream().filter(l -> l.payslip() == null && l.gross().signum() > 0).count();
        }
        return new Finance(report, variance, due, unpaid, month.minusMonths(1));
    }

    // Ca chua co ai truc trong 3 ngay toi (tinh ca hom nay)
    private List<String> upcomingShiftGaps(LocalDate today) {
        List<String> gaps = new ArrayList<>(staffShiftService.week(today).gaps());
        LocalDate limit = today.plusDays(2);
        if (StaffShiftService.weekStart(limit).isAfter(StaffShiftService.weekStart(today))) {
            gaps.addAll(staffShiftService.week(limit).gaps());
        }
        // Gap co dang "Ca sang 17/11": chi giu ngay trong [hom nay, hom nay + 2]
        List<String> keep = new ArrayList<>();
        for (String g : gaps) {
            for (int i = 0; i <= 2; i++) {
                LocalDate d = today.plusDays(i);
                if (g.endsWith(String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()))) {
                    keep.add(g);
                    break;
                }
            }
        }
        return keep;
    }
}
