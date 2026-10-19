package com.hotel.config.demo;

import com.hotel.config.demo.DemoPeople.Person;
import com.hotel.entity.*;
import com.hotel.repository.*;
import com.hotel.service.BudgetService;
import com.hotel.service.ExpenseService;
import com.hotel.service.FacilityReservationService;
import com.hotel.service.LoyaltyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Du lieu demo "nhu that" cho may dev: mo phong ~16 thang van hanh resort (tu 01/06/2025 den hom nay + don dat truoc 4 thang toi).
// Mua vu he / Tet / le 30-4, 2-9, cuoi tuan dong hon; khach dat truoc theo phan bo thuc te (don tuong lai chi co phan da dat toi hom nay),
// coc, huy / hoan coc theo chinh sach, khong den, khach doan cong ty, khach quay lai len hang, tich / doi diem, dich vu (dua don, tour, spa,
// nha hang, goi do, minibar, giat ui), khai bao luu tru, danh gia + phan hoi, tin nhan, don phong / kiem phong, su co, do that lac,
// lien he, the qua tang, chi phi van hanh hang thang, ke hoach nam. Hom nay con viec de thao tac: khach den / di, don cho xac nhan,
// goi do chua giao, tin nhan chua doc, phong chua don, phong dang sua.
// Chi chay khi app.seed-demo-data=true va chua seed (kiem tra tai khoan le tan demo). Email deu @example.* (khong toi nguoi that).
@Component
@Order(10)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String MARKER = "letan.thuhuong@example.com";
    static final String PASSWORD = "Songxanh@2026";
    static final LocalDate START = LocalDate.of(2025, 6, 1);
    static final int FUTURE_DAYS = 120;
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired private UserRepository users;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private BookingChargeRepository charges;
    @Autowired private ReviewRepository reviews;
    @Autowired private BookingGuestRepository bookingGuests;
    @Autowired private BookingMessageRepository messages;
    @Autowired private BookingGroupRepository groups;
    @Autowired private HousekeepingLogRepository hkLogs;
    @Autowired private MaintenanceTicketRepository tickets;
    @Autowired private LostItemRepository lostItems;
    @Autowired private ContactMessageRepository contacts;
    @Autowired private RoomServiceOrderRepository rsOrders;
    @Autowired private RoomServiceItemRepository rsItems;
    @Autowired private TourBookingRepository tourBookings;
    @Autowired private DivingTourRepository divingTours;
    @Autowired private AirportTransferRepository transfers;
    @Autowired private TransferRateRepository transferRates;
    @Autowired private FacilityReservationRepository facilities;
    @Autowired private SpaPackageRepository spaPackages;
    @Autowired private PointTransactionRepository pointTx;
    @Autowired private DiscountCodeRepository discountCodes;
    @Autowired private GiftVoucherRepository vouchers;
    @Autowired private FavoriteRoomRepository favorites;
    @Autowired private PricingSettingsRepository pricingRepo;
    @Autowired private ExpenseService expenseService;
    @Autowired private BudgetService budgetService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private final Random rnd = new Random(20250601L);
    private final DemoPeople people = new DemoPeople(rnd);
    private final Map<String, List<Object[]>> fixes = new LinkedHashMap<>();
    private LocalDate today;
    private LocalDateTime now;
    private String customerPassword;

    // Danh muc san co
    private List<Room> roomList;
    private List<DivingTour> tourList;
    private List<SpaPackage> spaList;
    private List<RoomServiceItem> menu;
    private List<TransferRate> rateList;
    private List<DiscountCode> codeList = new ArrayList<>();
    private List<User> frontDesk = new ArrayList<>();
    private List<User> housekeepers = new ArrayList<>();
    private User accountant;

    // Chinh sach (doc tu cau hinh gia)
    private int depositPercent = 10, depositHours = 6, weekendPercent = 10, refundFullHours = 24, refundPartialHours = 12,
            refundPartialPercent = 50, nonRefundablePercent = 10, memberNew = 5, memberRegular = 7, memberVip = 10;

    private final List<Booking> recentCheckouts = new ArrayList<>();
    private final Map<YearMonth, long[]> monthNights = new TreeMap<>(); // {phong-dem, khach-dem}
    private final Set<Long> maintenanceRoomIds = new HashSet<>();
    private int invoiceSeq = 1;

    static final class Guest {
        final Person p;
        User user;
        int stays;
        int points;
        Guest(Person p) {
            this.p = p;
        }
    }

    static final class Slot {
        Room room;
        LocalDate in, out;
        LocalDateTime created;
        boolean walkIn;
        int guests;
        int group = -1;
        Guest guest;
        String guestName;
    }

    // {cong ty, ngay den (offset hom nay neu null), so dem, so phong}
    private record GroupDef(String company, LocalDate start, int nights, int rooms) {
    }

    private final List<GroupDef> groupDefs = new ArrayList<>();
    private final Map<Integer, Guest> groupOrganizers = new HashMap<>();
    private final Map<Integer, BookingGroup> groupEntities = new HashMap<>();

    @Override
    public void run(String... args) {
        if (users.existsByEmail(MARKER)) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        roomList = tx.execute(s -> {
            List<Room> list = new ArrayList<>(rooms.findAll());
            list.sort(Comparator.comparing(Room::getId));
            list.forEach(r -> r.getRoomType().getName());
            return list;
        });
        if (roomList == null || roomList.isEmpty()) {
            log.warn("Bo qua du lieu demo: chua co phong nao");
            return;
        }
        long t0 = System.currentTimeMillis();
        today = LocalDate.now();
        now = LocalDateTime.now();
        customerPassword = encoder.encode(PASSWORD);
        tx.executeWithoutResult(s -> loadCatalog());
        tx.executeWithoutResult(s -> createStaff());
        tx.executeWithoutResult(s -> createDiscountCodes());

        List<Slot> slots = simulate();
        assignGuests(slots);
        log.info("Du lieu demo: {} don dat phong, dang ghi...", slots.size());
        for (int i = 0; i < slots.size(); i += 150) {
            List<Slot> part = slots.subList(i, Math.min(slots.size(), i + 150));
            tx.executeWithoutResult(s -> part.forEach(this::persist));
        }
        tx.executeWithoutResult(s -> {
            lostAndFound();
            maintenance();
            contactMessages();
            giftVouchers();
            favoriteRooms();
            roomStatuses();
        });
        tx.executeWithoutResult(s -> {
            costSettingsAndExpenses();
            budget2026();
        });
        applyFixes();
        log.info("Da tao du lieu demo trong {} giay. Dang nhap nhan vien: {} / {}", (System.currentTimeMillis() - t0) / 1000, MARKER, PASSWORD);
    }

    // ===================== Danh muc, nhan vien =====================

    private void loadCatalog() {
        tourList = divingTours.findAll().stream().filter(DivingTour::isActive).toList();
        spaList = spaPackages.findAll().stream().filter(p -> !Boolean.FALSE.equals(p.getActive())).toList();
        menu = rsItems.findAll().stream().filter(m -> !Boolean.FALSE.equals(m.getActive())).toList();
        rateList = transferRates.findAll();
        pricingRepo.findAll().stream().findFirst().ifPresent(p -> {
            depositPercent = or(p.getDepositPercent(), depositPercent);
            depositHours = or(p.getDepositDeadlineHours(), depositHours);
            weekendPercent = or(p.getWeekendSurchargePercent(), weekendPercent);
            refundFullHours = or(p.getRefundFullHours(), refundFullHours);
            refundPartialHours = or(p.getRefundPartialHours(), refundPartialHours);
            refundPartialPercent = or(p.getRefundPartialPercent(), refundPartialPercent);
            nonRefundablePercent = or(p.getNonRefundablePercent(), nonRefundablePercent);
            memberNew = or(p.getMemberDiscountNew(), memberNew);
            memberRegular = or(p.getMemberDiscountRegular(), memberRegular);
            memberVip = or(p.getMemberDiscountVip(), memberVip);
        });
    }

    private static int or(Integer v, int def) {
        return v == null ? def : v;
    }

    private User staff(String name, String email, UserRole role, LocalDateTime createdAt) {
        User u = users.save(User.builder().fullName(name).email(email).password(customerPassword).phoneNumber(people.phone())
                .role(role).emailVerified(true).locked(false).failedLoginAttempts(0).loyaltyPoints(0).build());
        fix("users", u.getId(), createdAt);
        return u;
    }

    private void createStaff() {
        LocalDateTime hired = START.minusMonths(2).atTime(9, 0);
        frontDesk.add(staff("Nguyễn Thu Hương", MARKER, UserRole.STAFF, hired));
        frontDesk.add(staff("Trần Minh Đức", "letan.minhduc@example.com", UserRole.STAFF, hired));
        frontDesk.add(staff("Phạm Ngọc Ánh", "letan.ngocanh@example.com", UserRole.STAFF, LocalDate.of(2025, 9, 15).atTime(9, 0)));
        housekeepers.add(staff("Lê Thị Hạnh", "buongphong.hanh@example.com", UserRole.HOUSEKEEPER, hired));
        housekeepers.add(staff("Đỗ Văn Toàn", "buongphong.toan@example.com", UserRole.HOUSEKEEPER, hired));
        housekeepers.add(staff("Hoàng Thị Mơ", "buongphong.mo@example.com", UserRole.HOUSEKEEPER, hired));
        housekeepers.add(staff("Vũ Thị Nụ", "buongphong.nu@example.com", UserRole.HOUSEKEEPER, LocalDate.of(2026, 5, 20).atTime(9, 0)));
        accountant = staff("Đặng Thị Bích Ngọc", "ketoan.bichngoc@example.com", UserRole.ACCOUNTANT, hired);
    }

    private void createDiscountCodes() {
        Object[][] defs = {
                {"HE2025", "Ưu đãi hè 2025 - giảm 10%", DiscountType.PERCENTAGE, 10, null, LocalDate.of(2025, 5, 15), LocalDate.of(2025, 8, 31), 300, 1_500_000, 800_000},
                {"TET2026", "Du xuân Bính Ngọ - giảm 15%", DiscountType.PERCENTAGE, 15, null, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 28), 120, 2_000_000, 1_200_000},
                {"HE2026", "Chào hè 2026 - giảm 10%", DiscountType.PERCENTAGE, 10, null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 31), 400, 1_500_000, 800_000},
                {"CHAOTHU", "Thu Hạ Long - giảm 300.000đ đơn từ 2 triệu", DiscountType.FIXED_AMOUNT, 300_000, null, LocalDate.of(2025, 9, 5), LocalDate.of(2026, 11, 30), null, 2_000_000, null},
                {"KHACHQUEN", "Cảm ơn khách quen - giảm 8%", DiscountType.PERCENTAGE, 8, CustomerType.REGULAR, START, null, null, null, 600_000},
                {"VIPSX", "Ưu đãi riêng khách VIP - giảm 12%", DiscountType.PERCENTAGE, 12, CustomerType.VIP, START, null, null, null, 1_500_000},
        };
        for (Object[] d : defs) {
            codeList.add(discountCodes.save(DiscountCode.builder().code((String) d[0]).description((String) d[1]).discountType((DiscountType) d[2])
                    .discountValue(BigDecimal.valueOf((Integer) d[3])).applicableCustomerType((CustomerType) d[4]).active(true)
                    .validFrom((LocalDate) d[5]).validTo((LocalDate) d[6]).maxUses((Integer) d[7])
                    .minOrderAmount(d[8] == null ? null : BigDecimal.valueOf((Integer) d[8]))
                    .maxDiscountAmount(d[9] == null ? null : BigDecimal.valueOf((Integer) d[9])).build()));
        }
    }

    // ===================== Mo phong lich phong =====================

    private static final double[] MONTH_OCC = {0.36, 0.50, 0.40, 0.56, 0.62, 0.82, 0.88, 0.78, 0.50, 0.42, 0.36, 0.46};

    private static double holiday(LocalDate d) {
        int y = d.getYear();
        if (!d.isBefore(LocalDate.of(2026, 2, 18)) && !d.isAfter(LocalDate.of(2026, 2, 22))) {
            return 0.93; // mung 2 - mung 6 Tet
        }
        if (!d.isBefore(LocalDate.of(2026, 2, 14)) && !d.isAfter(LocalDate.of(2026, 2, 17))) {
            return 0.25; // giap Tet, 30, mung 1: khach o nha
        }
        if (!d.isBefore(LocalDate.of(y, 4, 29)) && !d.isAfter(LocalDate.of(y, 5, 3))) {
            return 0.95;
        }
        if (!d.isBefore(LocalDate.of(y, 8, 29)) && !d.isAfter(LocalDate.of(y, 9, 2))) {
            return 0.93;
        }
        if ((d.getMonthValue() == 12 && d.getDayOfMonth() == 31) || (d.getMonthValue() == 1 && d.getDayOfMonth() == 1)) {
            return 0.82;
        }
        return -1;
    }

    private double occupancy(LocalDate d, Room room) {
        double o = MONTH_OCC[d.getMonthValue() - 1];
        if (d.getYear() >= 2026) {
            o *= 1.07;
        }
        DayOfWeek w = d.getDayOfWeek();
        o += (w == DayOfWeek.FRIDAY || w == DayOfWeek.SATURDAY) ? 0.14 : w == DayOfWeek.SUNDAY ? -0.03 : -0.06;
        double h = holiday(d);
        if (h >= 0) {
            o = h < 0.5 ? Math.min(o, h) : Math.max(o, h);
        }
        double price = room.getPrice().doubleValue();
        boolean summer = d.getMonthValue() >= 6 && d.getMonthValue() <= 8;
        if (price >= 5_000_000) {
            o *= 0.55;
        } else if (price >= 2_500_000) {
            o *= 0.75;
        } else if (room.getRoomType().getMaxGuests() != null && room.getRoomType().getMaxGuests() >= 4) {
            o *= summer ? 1.12 : 0.85;
        }
        return Math.max(0.03, Math.min(0.96, o));
    }

    private boolean isHoliday(LocalDate d) {
        return holiday(d) >= 0.5;
    }

    BigDecimal nightRate(LocalDate d, Room room) {
        double m = isHoliday(d) ? 1.3 : (d.getMonthValue() >= 6 && d.getMonthValue() <= 8) ? 1.2
                : (d.getMonthValue() >= 11 || d.getMonthValue() <= 3) ? 0.9 : 1.0;
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.FRIDAY || w == DayOfWeek.SATURDAY) {
            m *= 1 + weekendPercent / 100.0;
        }
        if (d.getYear() >= 2026) {
            m *= 1.05;
        }
        return round(room.getPrice().multiply(BigDecimal.valueOf(m)), 10_000);
    }

    private static BigDecimal round(BigDecimal v, long unit) {
        return v.divide(BigDecimal.valueOf(unit), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(unit));
    }

    private int nights() {
        int r = rnd.nextInt(100);
        return r < 22 ? 1 : r < 60 ? 2 : r < 85 ? 3 : r < 94 ? 4 : 5 + rnd.nextInt(3);
    }

    private int leadDays(LocalDate in) {
        int r = rnd.nextInt(100);
        int lead = r < 22 ? rnd.nextInt(4) : r < 55 ? 4 + rnd.nextInt(11) : r < 82 ? 15 + rnd.nextInt(21) : 36 + rnd.nextInt(65);
        if (in.getMonthValue() >= 6 && in.getMonthValue() <= 8) {
            lead += rnd.nextInt(10);
        }
        return lead;
    }

    private int guestsFor(Room room) {
        int max = room.getRoomType().getMaxGuests() == null ? 2 : room.getRoomType().getMaxGuests();
        if (max >= 4) {
            return 2 + rnd.nextInt(3);
        }
        if (max == 3) {
            return 2 + rnd.nextInt(2);
        }
        return rnd.nextInt(100) < 12 ? 1 : 2;
    }

    private List<Slot> simulate() {
        LocalDate end = today.plusDays(FUTURE_DAYS);
        int days = (int) ChronoUnit.DAYS.between(START, end);
        Map<Long, boolean[]> taken = new HashMap<>();
        roomList.forEach(r -> taken.put(r.getId(), new boolean[days]));
        List<Slot> slots = new ArrayList<>();

        // Don da co san trong DB (nhap tay truoc do) - khong xep trung
        for (Object[] row : jdbc.query("select room_id, check_in_date, check_out_date from bookings where status in ('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT')",
                (rs, i) -> new Object[]{rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate()})) {
            boolean[] t = taken.get((Long) row[0]);
            if (t != null) {
                int from = index((LocalDate) row[1]);
                mark(t, from, (int) ChronoUnit.DAYS.between((LocalDate) row[1], (LocalDate) row[2]), days);
            }
        }

        // 2 phong dang sua (khong ban tu hom qua den 4 ngay toi)
        for (Room r : List.of(roomList.get(4), roomList.get(roomList.size() / 2))) {
            maintenanceRoomIds.add(r.getId());
            mark(taken.get(r.getId()), index(today.minusDays(1)), 6, days);
        }

        // Khach doan cong ty
        groupDefs.add(new GroupDef(DemoTexts.COMPANIES[0], LocalDate.of(2025, 7, 18), 3, 8));
        groupDefs.add(new GroupDef(DemoTexts.COMPANIES[1], LocalDate.of(2025, 11, 14), 2, 10));
        groupDefs.add(new GroupDef(DemoTexts.COMPANIES[2], LocalDate.of(2026, 3, 20), 2, 6));
        groupDefs.add(new GroupDef(DemoTexts.COMPANIES[4], LocalDate.of(2026, 6, 26), 3, 9));
        groupDefs.add(new GroupDef(DemoTexts.COMPANIES[3], today.plusDays(32), 2, 8));
        List<Room> cheap = new ArrayList<>(roomList);
        cheap.sort(Comparator.comparing(Room::getPrice));
        for (int g = 0; g < groupDefs.size(); g++) {
            GroupDef def = groupDefs.get(g);
            int idx = index(def.start());
            int placed = 0;
            for (Room r : cheap) {
                if (placed == def.rooms()) {
                    break;
                }
                boolean[] t = taken.get(r.getId());
                if (free(t, idx, def.nights())) {
                    mark(t, idx, def.nights(), days);
                    Slot s = new Slot();
                    s.room = r;
                    s.in = def.start();
                    s.out = def.start().plusDays(def.nights());
                    LocalDate createdDay = def.start().minusDays(28);
                    s.created = (createdDay.isAfter(today) ? today.minusDays(3) : createdDay).atTime(10, 15).plusMinutes(placed);
                    s.guests = 2;
                    s.group = g;
                    slots.add(s);
                    placed++;
                }
            }
        }

        for (Room room : roomList) {
            boolean[] t = taken.get(room.getId());
            int i = 0;
            while (i < days) {
                if (t[i]) {
                    i++;
                    continue;
                }
                LocalDate d = START.plusDays(i);
                double occ = occupancy(d, room);
                double q = occ / (2.4 * (1 - occ) + occ);
                if (rnd.nextDouble() >= q) {
                    i++;
                    continue;
                }
                int n = nights();
                int k = 0;
                while (k < n && i + k < days && !t[i + k]) {
                    k++;
                }
                Slot s = new Slot();
                s.room = room;
                s.in = d;
                s.out = d.plusDays(k);
                s.guests = guestsFor(room);
                s.walkIn = !d.isAfter(today) && rnd.nextInt(100) < 17;
                s.created = s.walkIn ? d.atTime(13 + rnd.nextInt(8), rnd.nextInt(60))
                        : d.minusDays(leadDays(d)).atTime(7 + rnd.nextInt(16), rnd.nextInt(60));
                if (!s.created.isAfter(now)) {
                    mark(t, i, k, days);
                    slots.add(s);
                }
                i += k;
            }
        }
        slots.sort(Comparator.comparing(s -> s.created));
        return slots;
    }

    private int index(LocalDate d) {
        return (int) ChronoUnit.DAYS.between(START, d);
    }

    private static boolean free(boolean[] t, int from, int n) {
        for (int i = from; i < from + n; i++) {
            if (i < 0 || i >= t.length || t[i]) {
                return false;
            }
        }
        return true;
    }

    private static void mark(boolean[] t, int from, int n, int days) {
        for (int i = Math.max(0, from); i < Math.min(days, from + n); i++) {
            t[i] = true;
        }
    }

    // Khach moi / khach quay lai (~20%) / nguoi lien he cua doan
    private void assignGuests(List<Slot> slots) {
        List<Guest> pool = new ArrayList<>();
        for (Slot s : slots) {
            if (s.group >= 0) {
                s.guest = groupOrganizers.computeIfAbsent(s.group, g -> new Guest(people.person(today, false)));
                s.guestName = people.vietName(rnd.nextBoolean() ? Gender.MALE : Gender.FEMALE);
                continue;
            }
            if (!pool.isEmpty() && rnd.nextInt(100) < 20) {
                s.guest = pool.get(rnd.nextInt(pool.size()));
            } else {
                s.guest = new Guest(people.person(today, rnd.nextInt(100) < 12));
            }
            s.guestName = s.guest.p.fullName();
            pool.add(s.guest);
        }
    }

    // ===================== Ghi 1 don va moi thu xoay quanh no =====================

    private void persist(Slot s) {
        Guest g = s.guest;
        if (g.user == null) {
            g.user = users.save(User.builder().fullName(g.p.fullName()).email(g.p.email()).password(customerPassword)
                    .phoneNumber(g.p.phone()).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).loyaltyPoints(0)
                    .emailVerified(true).locked(false).failedLoginAttempts(0).build());
            fix("users", g.user.getId(), s.created.minusMinutes(3 + rnd.nextInt(40)));
        }
        CustomerType tier = g.stays >= 5 ? CustomerType.VIP : g.stays >= 2 ? CustomerType.REGULAR : CustomerType.NEW;
        boolean online = !s.walkIn;
        int nights = (int) ChronoUnit.DAYS.between(s.in, s.out);
        BigDecimal roomAmount = BigDecimal.ZERO;
        BigDecimal lastRate = BigDecimal.ZERO;
        for (LocalDate d = s.in; d.isBefore(s.out); d = d.plusDays(1)) {
            lastRate = nightRate(d, s.room);
            roomAmount = roomAmount.add(lastRate);
        }
        if (s.group >= 0) {
            roomAmount = round(roomAmount.multiply(BigDecimal.valueOf(0.85)), 10_000); // gia doan
        }

        Booking.BookingBuilder bb = Booking.builder().customer(g.user).room(s.room).checkInDate(s.in).checkOutDate(s.out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(s.guests)
                .guestName(s.guestName).guestPhone(g.p.phone()).guestEmail(g.p.email())
                .channel(online ? BookingChannel.ONLINE : BookingChannel.WALK_IN).roomAmount(roomAmount).newBooking(false);

        // Giam gia: thanh vien (dat online), ma giam gia, gia khong hoan tien, doi diem
        BigDecimal net = roomAmount;
        int memberPct = !online || s.group >= 0 ? 0 : tier == CustomerType.VIP ? memberVip : tier == CustomerType.REGULAR ? memberRegular : memberNew;
        if (memberPct > 0) {
            BigDecimal amt = round(roomAmount.multiply(BigDecimal.valueOf(memberPct)).divide(BigDecimal.valueOf(100)), 1000);
            bb.memberDiscountPercent(memberPct).memberDiscountAmount(amt);
            net = net.subtract(amt);
        }
        BigDecimal codeAmount = BigDecimal.ZERO;
        if (online && s.group < 0 && rnd.nextInt(100) < 14) {
            DiscountCode code = usableCode(s.created.toLocalDate(), tier, net);
            if (code != null) {
                codeAmount = code.getDiscountType() == DiscountType.PERCENTAGE
                        ? round(net.multiply(code.getDiscountValue()).divide(BigDecimal.valueOf(100)), 1000) : code.getDiscountValue();
                if (code.getMaxDiscountAmount() != null) {
                    codeAmount = codeAmount.min(code.getMaxDiscountAmount());
                }
                bb.discountCode(code);
                net = net.subtract(codeAmount);
            }
        }
        bb.discountAmount(codeAmount);
        boolean nonRefundable = online && s.group < 0 && rnd.nextInt(100) < 8;
        if (nonRefundable) {
            BigDecimal amt = round(net.multiply(BigDecimal.valueOf(nonRefundablePercent)).divide(BigDecimal.valueOf(100)), 1000);
            bb.nonRefundable(true).nonRefundablePercent(nonRefundablePercent).nonRefundableAmount(amt);
            net = net.subtract(amt);
        }
        int pointsUsed = 0;
        if (online && g.points >= 500 && rnd.nextInt(100) < 30) {
            pointsUsed = Math.min(g.points, 3000) / 100 * 100;
            BigDecimal amt = BigDecimal.valueOf((long) pointsUsed * LoyaltyService.VND_PER_REDEEMED_POINT);
            if (amt.compareTo(net.multiply(BigDecimal.valueOf(0.3))) > 0) {
                pointsUsed = net.multiply(BigDecimal.valueOf(0.3)).intValue() / LoyaltyService.VND_PER_REDEEMED_POINT / 100 * 100;
                amt = BigDecimal.valueOf((long) pointsUsed * LoyaltyService.VND_PER_REDEEMED_POINT);
            }
            if (pointsUsed > 0) {
                bb.pointsUsed(pointsUsed).pointsDiscount(amt);
                net = net.subtract(amt);
            }
        }
        BigDecimal total = net.max(BigDecimal.ZERO);
        bb.totalAmount(total);

        // Coc
        BigDecimal deposit = online && depositPercent > 0
                ? round(total.multiply(BigDecimal.valueOf(depositPercent)).divide(BigDecimal.valueOf(100)), 1000) : BigDecimal.ZERO;
        LocalDateTime deadline = s.created.plusHours(depositHours);
        LocalDateTime paidAt = null;
        boolean onlineDeposit = false;
        if (deposit.signum() > 0) {
            bb.depositAmount(deposit).depositDeadline(deadline);
            if (s.group >= 0 || rnd.nextInt(100) < 93) {
                paidAt = s.created.plusMinutes(5 + rnd.nextInt(150));
                if (paidAt.isAfter(now)) {
                    paidAt = null;
                }
            }
            if (paidAt != null) {
                bb.depositPaidAt(paidAt);
                if (rnd.nextInt(100) < 65) {
                    onlineDeposit = true;
                    bb.depositOnlineRef(String.valueOf(14_000_000 + rnd.nextInt(9_000_000)))
                            .depositOnlineTxnRef("SX" + paidAt.format(VNP_DATE) + (100 + rnd.nextInt(900)))
                            .depositOnlinePayDate(paidAt.format(VNP_DATE));
                }
            }
        }

        // Trang thai
        LocalDateTime checkInAt = s.in.atTime(14, 0);
        BookingStatus status;
        LocalDateTime cancelledAt = null;
        boolean noShow = false;
        if (deposit.signum() > 0 && paidAt == null) {
            if (deadline.isBefore(now)) {
                status = BookingStatus.CANCELLED;
                cancelledAt = deadline;
            } else {
                status = BookingStatus.PENDING;
            }
        } else {
            status = null;
            long lead = ChronoUnit.DAYS.between(s.created.toLocalDate(), s.in);
            int cancelPct = s.group >= 0 || !online ? 0 : nonRefundable ? 2 : 7 + (lead > 30 ? 5 : 0);
            if (rnd.nextInt(100) < cancelPct) {
                LocalDateTime from = paidAt != null ? paidAt : s.created;
                LocalDateTime at;
                if (rnd.nextInt(100) < 15) {
                    at = checkInAt.minusHours(1 + rnd.nextInt(11));
                } else {
                    long span = Math.max(60, ChronoUnit.MINUTES.between(from, checkInAt.minusHours(refundFullHours)));
                    at = from.plusMinutes((long) (rnd.nextDouble() * span));
                }
                if (!at.isAfter(from)) {
                    at = from.plusMinutes(30);
                }
                if (!at.isAfter(now)) {
                    status = BookingStatus.CANCELLED;
                    cancelledAt = at;
                }
            }
            if (status == null) {
                if (s.out.isBefore(today)) {
                    if (online && s.group < 0 && rnd.nextInt(1000) < 15) {
                        status = BookingStatus.CANCELLED;
                        noShow = true;
                        cancelledAt = s.in.atTime(23, 30);
                    } else {
                        status = BookingStatus.CHECKED_OUT;
                    }
                } else if (s.in.isBefore(today) || (!online && !s.in.isAfter(today))) {
                    status = BookingStatus.CHECKED_IN;
                } else {
                    status = BookingStatus.CONFIRMED;
                }
            }
        }
        bb.status(status).cancelledAt(cancelledAt);
        if (noShow) {
            bb.noShow(true).noShowAt(cancelledAt);
        }
        if (status == BookingStatus.CANCELLED && paidAt != null) {
            int pct;
            if (noShow || nonRefundable) {
                pct = 0;
            } else {
                long hours = ChronoUnit.HOURS.between(cancelledAt, checkInAt);
                pct = hours >= refundFullHours ? 100 : hours >= refundPartialHours ? refundPartialPercent : 0;
            }
            BigDecimal refund = round(deposit.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100)), 1000);
            bb.refundPercent(pct).refundAmount(refund);
            if (refund.signum() > 0) {
                LocalDateTime refundedAt = cancelledAt.plusHours(4 + rnd.nextInt(44));
                if (!refundedAt.isAfter(now)) {
                    bb.refundedAt(refundedAt).refundNote(onlineDeposit ? "Hoàn qua VNPay" : "Đã chuyển khoản hoàn cọc");
                    if (onlineDeposit) {
                        bb.refundOnlineRef(String.valueOf(15_000_000 + rnd.nextInt(9_000_000)));
                    }
                }
            }
        }

        // Yeu cau dac biet, hoa don cong ty, nhac lich (danh dau da gui de scheduler khong gui lai)
        if (rnd.nextInt(100) < 18) {
            List<GuestRequestTag> tags = new ArrayList<>(List.of(GuestRequestTag.values()));
            Collections.shuffle(tags, rnd);
            bb.requestTags(String.join(",", tags.subList(0, 1 + rnd.nextInt(2)).stream().map(Enum::name).toList()))
                    .specialRequests(people.pick(DemoTexts.SPECIAL_REQUESTS)).requestsUpdatedAt(s.created);
        }
        if (s.group >= 0 || (online && rnd.nextInt(100) < 5)) {
            String company = s.group >= 0 ? groupDefs.get(s.group).company() : people.pick(DemoTexts.COMPANIES);
            bb.invoiceCompanyName(company).invoiceTaxCode("0" + (100_000_000 + rnd.nextInt(899_999_999)))
                    .invoiceAddress("Tầng " + (2 + rnd.nextInt(15)) + ", tòa nhà " + people.pick(new String[]{"Sông Đà", "Viglacera", "HL Tower", "Hapro"}) + ", Hà Nội")
                    .invoiceEmail("ketoan." + DemoPeople.ascii(company.substring(company.lastIndexOf(' ') + 1)).toLowerCase() + "@example.com");
            if (status == BookingStatus.CHECKED_OUT) {
                bb.eInvoiceNumber(String.format("1C%02dTSX%07d", s.out.getYear() % 100, invoiceSeq++)).eInvoiceIssuedAt(s.out.plusDays(1).atTime(9, 30));
            }
        }
        LocalDateTime reminder = s.in.minusDays(2).atTime(9, 0);
        bb.reminderSentAt(reminder.isAfter(now) ? now : reminder);
        if (deposit.signum() > 0) {
            bb.depositReminderSentAt(deadline.minusHours(2).isAfter(now) ? now : deadline.minusHours(2));
        }
        if (status == BookingStatus.CHECKED_OUT) {
            LocalDateTime invite = s.out.plusDays(1).atTime(10, 0);
            bb.reviewInviteSentAt(invite.isAfter(now) ? now : invite);
            int earned = (int) Math.floor(total.divide(BigDecimal.valueOf(LoyaltyService.VND_PER_EARNED_POINT), 0, RoundingMode.DOWN).longValue()
                    * LoyaltyService.earnMultiplier(tier));
            bb.pointsEarned(earned);
        }
        if (status == BookingStatus.PENDING || s.created.isAfter(now.minusHours(20))) {
            bb.newBooking(true);
        }
        if (s.group >= 0) {
            bb.group(groupEntity(s.group, g, s.created));
        }

        Booking b = bookings.save(bb.build());
        fix("bookings", b.getId(), s.created);

        if (pointsUsed > 0) {
            g.points -= pointsUsed;
            point(g, b, PointTransaction.Type.REDEEM, -pointsUsed, s.created.plusMinutes(1));
            if (status == BookingStatus.CANCELLED) {
                g.points += pointsUsed;
                point(g, b, PointTransaction.Type.REFUND, pointsUsed, cancelledAt);
            }
        }
        boolean stayed = status == BookingStatus.CHECKED_OUT || status == BookingStatus.CHECKED_IN;
        if (stayed) {
            g.stays++;
            YearMonth ym = YearMonth.from(s.in);
            for (LocalDate d = s.in; d.isBefore(s.out); d = d.plusDays(1)) {
                long[] v = monthNights.computeIfAbsent(YearMonth.from(d), k -> new long[2]);
                v[0]++;
                v[1] += s.guests;
            }
        }

        BigDecimal extras = services(b, s, status, lastRate);
        if (status == BookingStatus.CHECKED_OUT) {
            BigDecimal due = total.add(extras).subtract(paidAt != null ? deposit : BigDecimal.ZERO).max(BigDecimal.ZERO);
            int r = rnd.nextInt(100);
            PaymentMethod method = r < 30 ? PaymentMethod.CASH : r < 72 ? PaymentMethod.CARD : PaymentMethod.BANK_TRANSFER;
            payments.save(Payment.builder().booking(b).amount(due).paymentMethod(method).status(PaymentStatus.PAID)
                    .paymentDate(s.out.atTime(10 + rnd.nextInt(2), rnd.nextInt(60))).build());
            g.points += b.getPointsEarned();
            point(g, b, PointTransaction.Type.EARN, b.getPointsEarned(), s.out.atTime(12, 5));
            if (!s.out.isBefore(today.minusDays(120))) {
                recentCheckouts.add(b);
            }
            if (!s.out.isBefore(today.minusDays(60))) {
                housekeeping(s.room, s.out);
            }
            review(b, g, s);
        }
        if (stayed) {
            registerGuests(b, g, s, online);
        }
        if (rnd.nextInt(100) < 10 && status != BookingStatus.CANCELLED) {
            chat(b, g, s);
        }
    }

    private DiscountCode usableCode(LocalDate day, CustomerType tier, BigDecimal amount) {
        List<DiscountCode> ok = codeList.stream()
                .filter(c -> (c.getValidFrom() == null || !day.isBefore(c.getValidFrom())) && (c.getValidTo() == null || !day.isAfter(c.getValidTo())))
                .filter(c -> c.getApplicableCustomerType() == null || c.getApplicableCustomerType() == tier)
                .filter(c -> c.getMinOrderAmount() == null || amount.compareTo(c.getMinOrderAmount()) >= 0).toList();
        return ok.isEmpty() ? null : ok.get(rnd.nextInt(ok.size()));
    }

    private BookingGroup groupEntity(int idx, Guest g, LocalDateTime created) {
        return groupEntities.computeIfAbsent(idx, k -> {
            BookingGroup bg = groups.save(BookingGroup.builder().customer(g.user).guestName(g.p.fullName())
                    .guestEmail(g.p.email()).guestPhone(g.p.phone()).build());
            fix("booking_groups", bg.getId(), created);
            return bg;
        });
    }

    private void point(Guest g, Booking b, PointTransaction.Type type, int points, LocalDateTime at) {
        if (points == 0) {
            return;
        }
        PointTransaction t = pointTx.save(PointTransaction.builder().user(g.user).booking(b).type(type).points(points).balanceAfter(g.points).build());
        fix("point_transactions", t.getId(), at);
    }

    private BookingCharge charge(Booking b, ChargeType type, String desc, BigDecimal amount, LocalDateTime at) {
        BookingCharge c = charges.save(BookingCharge.builder().booking(b).type(type).description(desc).amount(amount).build());
        fix("booking_charges", c.getId(), at);
        return c;
    }

    private String staffName() {
        return people.pick(frontDesk).getFullName();
    }

    // Dich vu trong thoi gian o; tra ve tong phu phi da cong vao don
    private BigDecimal services(Booking b, Slot s, BookingStatus status, BigDecimal lastRate) {
        boolean stayed = status == BookingStatus.CHECKED_OUT || status == BookingStatus.CHECKED_IN;
        boolean upcoming = status == BookingStatus.CONFIRMED;
        if (!stayed && !upcoming) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int nights = (int) ChronoUnit.DAYS.between(s.in, s.out);
        boolean summer = s.in.getMonthValue() >= 6 && s.in.getMonthValue() <= 9;

        // Don san bay
        if (rnd.nextInt(100) < 16 && !rateList.isEmpty()) {
            int r = rnd.nextInt(100);
            Airport airport = r < 70 ? Airport.VAN_DON : r < 90 ? Airport.NOI_BAI : Airport.CAT_BI;
            VehicleType v = s.guests <= 3 ? VehicleType.CAR_4 : s.guests <= 6 ? VehicleType.CAR_7 : VehicleType.VAN_16;
            BigDecimal price = rateList.stream().filter(x -> x.getAirport() == airport && x.getVehicleType() == v).findFirst()
                    .map(TransferRate::getPrice).orElse(BigDecimal.valueOf(650_000));
            LocalDateTime pickup = s.in.atTime(10 + rnd.nextInt(4), 15 * rnd.nextInt(4));
            LocalDateTime requested = s.created.plusHours(1 + rnd.nextInt(20));
            if (requested.isAfter(pickup.minusHours(3))) {
                requested = pickup.minusHours(5);
            }
            if (!requested.isAfter(now)) {
                LocalDateTime confirmed = requested.plusMinutes(20 + rnd.nextInt(120));
                boolean ok = !confirmed.isAfter(now);
                String flight = people.pick(airport == Airport.VAN_DON ? new String[]{"VN 1546", "VJ 471", "QH 1203", "VN 1529", "VJ 475"}
                        : airport == Airport.NOI_BAI ? new String[]{"VN 245", "VJ 135", "KE 479", "OZ 729", "CZ 3045", "QH 1525"}
                        : new String[]{"VN 1186", "VJ 735", "QH 1091"});
                BookingCharge c = ok ? charge(b, ChargeType.AIRPORT_TRANSFER, "Đón sân bay " + airportName(airport) + " - " + v.getVietnameseLabel(), price, confirmed) : null;
                AirportTransfer t = transfers.save(AirportTransfer.builder().booking(b).direction(TransferDirection.PICKUP).airport(airport)
                        .vehicleType(v).pickupAt(pickup).flightNumber(flight).passengers(s.guests).price(price)
                        .status(ok ? ServiceStatus.CONFIRMED : ServiceStatus.PENDING).charge(c).build());
                fix("airport_transfers", t.getId(), requested);
                if (ok) {
                    sum = sum.add(price);
                }
            }
        }
        if (!stayed) {
            // Don sap toi: thinh thoang da dat spa truoc (cho xac nhan)
            if (rnd.nextInt(100) < 6 && !spaList.isEmpty()) {
                SpaPackage p = people.pick(spaList);
                FacilityReservation fr = facilities.save(FacilityReservation.builder().booking(b).kind(FacilityReservation.Kind.SPA).spaPackage(p)
                        .reservedDate(s.in.plusDays(nights > 1 ? 1 : 0)).reservedTime(LocalTime.of(15 + rnd.nextInt(4), 0)).people(1)
                        .durationMinutes(p.getMinutes()).totalPrice(p.getPrice()).status(ServiceStatus.PENDING)
                        .note(rnd.nextBoolean() ? "Nhờ chọn kỹ thuật viên nữ" : null).build());
                fix("facility_reservations", fr.getId(), now.minusHours(1 + rnd.nextInt(30)));
            }
            return sum;
        }

        // Tien ich theo ngay o (chi nhung gi da xay ra toi luc nay)
        if (rnd.nextInt(100) < (summer ? 16 : 7) && !tourList.isEmpty()) {
            DivingTour tour = people.pick(tourList);
            LocalDate day = s.in.plusDays(nights >= 2 ? 1 : 0);
            LocalTime dep = tour.getDepartureTime() == null ? LocalTime.of(8, 0) : tour.getDepartureTime();
            LocalDateTime requested = s.in.atTime(16 + rnd.nextInt(4), rnd.nextInt(60));
            if (!day.isAfter(s.in)) {
                requested = s.created.plusHours(2);
            }
            LocalDateTime confirmed = requested.plusMinutes(30 + rnd.nextInt(60));
            if (!confirmed.isAfter(now)) {
                int participants = Math.min(s.guests, tour.getMaxParticipants() == null ? s.guests : tour.getMaxParticipants());
                BigDecimal totalPrice = tour.getPricePerPerson().multiply(BigDecimal.valueOf(participants));
                BookingCharge c = charge(b, ChargeType.DIVING_TOUR, "Tour: " + tour.getName() + " x " + participants + " người", totalPrice, confirmed);
                TourBooking tb = tourBookings.save(TourBooking.builder().booking(b).tour(tour).tourDate(day).participants(participants)
                        .unitPrice(tour.getPricePerPerson()).totalPrice(totalPrice).status(ServiceStatus.CONFIRMED).charge(c).build());
                fix("tour_bookings", tb.getId(), requested);
                sum = sum.add(totalPrice);
            }
        }
        if (rnd.nextInt(100) < 13 && !spaList.isEmpty()) {
            SpaPackage p = people.pick(spaList);
            LocalDate day = s.in.plusDays(rnd.nextInt(nights));
            LocalTime time = people.pick(FacilityReservationService.SPA_SLOTS);
            LocalDateTime requested = day.atTime(9, 30).plusMinutes(rnd.nextInt(120));
            if (!day.atTime(time).isAfter(now) && requested.isBefore(day.atTime(time))) {
                int ppl = s.guests >= 2 && rnd.nextBoolean() ? 2 : 1;
                BigDecimal price = p.getPrice().multiply(BigDecimal.valueOf(ppl));
                BookingCharge c = charge(b, ChargeType.SPA, "Spa: " + p.getName() + " x " + ppl + " người", price, requested.plusMinutes(15));
                FacilityReservation.FacilityReservationBuilder fb = FacilityReservation.builder().booking(b).kind(FacilityReservation.Kind.SPA)
                        .spaPackage(p).reservedDate(day).reservedTime(time).people(ppl).durationMinutes(p.getMinutes()).totalPrice(price)
                        .status(ServiceStatus.CONFIRMED).charge(c);
                LocalDateTime ratedAt = day.atTime(time).plusHours(3);
                if (rnd.nextInt(100) < 35 && !ratedAt.isAfter(now)) {
                    int rating = rnd.nextInt(100) < 80 ? 5 : rnd.nextInt(100) < 70 ? 4 : 3;
                    fb.rating(rating).ratedAt(ratedAt).feedback(people.pick(rating >= 4
                            ? new String[]{"Kỹ thuật viên tay nghề tốt, rất thư giãn.", "Không gian thơm, nhạc nhẹ dễ chịu.", "Sẽ quay lại!", "Massage rất đã, giá hợp lý."}
                            : new String[]{"Phòng spa hơi lạnh.", "Phải chờ 15 phút mới được vào."}));
                }
                FacilityReservation fr = facilities.save(fb.build());
                fix("facility_reservations", fr.getId(), requested);
                sum = sum.add(price);
            }
        }
        if (rnd.nextInt(100) < 12) {
            LocalDate day = s.in.plusDays(rnd.nextInt(nights));
            LocalTime time = people.pick(FacilityReservationService.RESTAURANT_SLOTS.stream().filter(t -> t.getHour() >= 18).toList());
            LocalDateTime requested = day.atTime(14, 0).plusMinutes(rnd.nextInt(180));
            if (!requested.isAfter(now)) {
                FacilityReservation fr = facilities.save(FacilityReservation.builder().booking(b).kind(FacilityReservation.Kind.RESTAURANT)
                        .reservedDate(day).reservedTime(time).people(s.guests).status(ServiceStatus.CONFIRMED)
                        .note(rnd.nextInt(3) == 0 ? people.pick(new String[]{"Bàn gần cửa sổ view biển", "Có 1 ghế trẻ em", "Sinh nhật, nhờ chuẩn bị nến"}) : null)
                        .build());
                fix("facility_reservations", fr.getId(), requested);
            }
        }
        // Goi do len phong / giat ui
        if (rnd.nextInt(100) < 25) {
            sum = sum.add(roomServiceOrder(b, s, nights, false));
        }
        if (rnd.nextInt(100) < 9) {
            sum = sum.add(roomServiceOrder(b, s, nights, true));
        }
        if (rnd.nextInt(100) < 3) {
            BigDecimal fee = round(lastRate.multiply(BigDecimal.valueOf(0.3)), 10_000);
            charge(b, ChargeType.EARLY_CHECKIN, "Nhận phòng sớm từ 10:00", fee, s.in.atTime(10, 5));
            sum = sum.add(fee);
        }
        if (status == BookingStatus.CHECKED_OUT) {
            if (rnd.nextInt(100) < 30) {
                List<RoomServiceItem> minibar = menu.stream().filter(m -> m.getCategory() == RoomServiceItem.Category.MINIBAR).toList();
                if (!minibar.isEmpty()) {
                    StringBuilder desc = new StringBuilder("Minibar: ");
                    BigDecimal amt = BigDecimal.ZERO;
                    List<RoomServiceItem> picked = new ArrayList<>(minibar);
                    Collections.shuffle(picked, rnd);
                    int kinds = 1 + rnd.nextInt(Math.min(3, picked.size()));
                    for (int i = 0; i < kinds; i++) {
                        int q = 1 + rnd.nextInt(3);
                        amt = amt.add(picked.get(i).getPrice().multiply(BigDecimal.valueOf(q)));
                        desc.append(i > 0 ? ", " : "").append(picked.get(i).getName()).append(" x").append(q);
                    }
                    charge(b, ChargeType.MINIBAR, desc.toString(), amt, s.out.atTime(10, 20 + rnd.nextInt(30)));
                    sum = sum.add(amt);
                }
            }
            if (rnd.nextInt(100) < 4) {
                BigDecimal fee = round(lastRate.multiply(BigDecimal.valueOf(0.5)), 10_000);
                charge(b, ChargeType.LATE_CHECKOUT, "Trả phòng muộn đến 16:00", fee, s.out.atTime(16, 0));
                sum = sum.add(fee);
            }
            if (rnd.nextInt(1000) < 6) {
                String[][] dmg = {{"Làm vỡ ly thủy tinh", "80000"}, {"Vết rượu vang trên ga giường, phải thay", "300000"}, {"Mất thẻ từ phòng", "100000"}};
                String[] d = people.pick(dmg);
                charge(b, ChargeType.DAMAGE, d[0], new BigDecimal(d[1]), s.out.atTime(10, 45));
                sum = sum.add(new BigDecimal(d[1]));
            }
        }
        return sum;
    }

    private static String airportName(Airport a) {
        return switch (a) {
            case VAN_DON -> "Vân Đồn";
            case NOI_BAI -> "Nội Bài";
            case CAT_BI -> "Cát Bi";
        };
    }

    private BigDecimal roomServiceOrder(Booking b, Slot s, int nights, boolean laundry) {
        LocalDate day = s.in.plusDays(rnd.nextInt(nights));
        LocalDateTime at = laundry ? day.plusDays(nights > 1 ? 0 : 0).atTime(8 + rnd.nextInt(3), rnd.nextInt(60))
                : day.atTime(rnd.nextBoolean() ? 21 : 19, rnd.nextInt(60));
        if (at.isBefore(s.in.atTime(14, 30)) || !at.isBefore(s.out.atTime(11, 0))) {
            at = s.in.atTime(20, 10 + rnd.nextInt(40));
        }
        if (at.isAfter(now)) {
            return BigDecimal.ZERO;
        }
        List<RoomServiceItem> options = menu.stream().filter(m -> laundry ? m.getCategory() == RoomServiceItem.Category.LAUNDRY
                : m.getCategory() == RoomServiceItem.Category.FOOD || m.getCategory() == RoomServiceItem.Category.DRINK).toList();
        if (options.isEmpty()) {
            return BigDecimal.ZERO;
        }
        RoomServiceOrder order = RoomServiceOrder.builder().booking(b).status(RoomServiceOrder.Status.PENDING)
                .note(laundry ? null : rnd.nextInt(4) == 0 ? people.pick(new String[]{"Ít cay giúp mình", "Không hành", "Cho thêm đá"}) : null).build();
        List<RoomServiceItem> picked = new ArrayList<>(options);
        Collections.shuffle(picked, rnd);
        BigDecimal total = BigDecimal.ZERO;
        int kinds = 1 + rnd.nextInt(Math.min(3, picked.size()));
        for (int i = 0; i < kinds; i++) {
            RoomServiceItem item = picked.get(i);
            int q = 1 + rnd.nextInt(laundry ? 3 : 2);
            order.getLines().add(RoomServiceOrderLine.builder().order(order).itemName(item.getName()).category(item.getCategory())
                    .unitPrice(item.getPrice()).quantity(q).build());
            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(q)));
        }
        order.setTotalAmount(total);
        LocalDateTime delivered = at.plusMinutes(laundry ? 240 : 20 + rnd.nextInt(25));
        boolean done = !delivered.isAfter(now);
        if (done) {
            order.setStatus(RoomServiceOrder.Status.DELIVERED);
            order.setDeliveredAt(delivered);
            order.setHandledBy(staffName());
        }
        order = rsOrders.save(order);
        fix("room_service_orders", order.getId(), at);
        if (!done) {
            return BigDecimal.ZERO;
        }
        Map<ChargeType, List<RoomServiceOrderLine>> byType = new LinkedHashMap<>();
        for (RoomServiceOrderLine l : order.getLines()) {
            byType.computeIfAbsent(l.getCategory().chargeType(), k -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<ChargeType, List<RoomServiceOrderLine>> e : byType.entrySet()) {
            String text = "Gọi lên phòng #" + order.getId() + ": " + String.join(", ", e.getValue().stream()
                    .map(l -> l.getItemName() + " x" + l.getQuantity()).toList());
            charge(b, e.getKey(), text.length() > 200 ? text.substring(0, 197) + "..." : text,
                    e.getValue().stream().map(RoomServiceOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add), delivered);
        }
        return total;
    }

    private void housekeeping(Room room, LocalDate day) {
        User cleaner = people.pick(housekeepers.subList(0, day.isBefore(LocalDate.of(2026, 5, 20)) ? 3 : 4));
        LocalDateTime start = day.atTime(12, 10).plusMinutes(rnd.nextInt(170));
        int minutes = 25 + rnd.nextInt(30) + (room.getPrice().doubleValue() >= 2_500_000 ? 15 : 0);
        LocalDateTime end = start.plusMinutes(minutes);
        int r = rnd.nextInt(100);
        String supervisor = housekeepers.get(0).getFullName();
        HousekeepingLog.Result result = r < 70 ? HousekeepingLog.Result.PASSED : r < 78 ? HousekeepingLog.Result.FAILED : HousekeepingLog.Result.DIRECT;
        hkLogs.save(HousekeepingLog.builder().room(room).cleaner(cleaner).cleanerName(cleaner.getFullName()).startedAt(start).finishedAt(end)
                .result(result).inspectedBy(result == HousekeepingLog.Result.DIRECT ? null : supervisor)
                .note(result == HousekeepingLog.Result.FAILED ? people.pick(new String[]{"Còn tóc trên sàn nhà tắm", "Thiếu 1 khăn tắm", "Gương còn vệt nước", "Chưa bổ sung nước suối"}) : null)
                .build());
        if (result == HousekeepingLog.Result.FAILED) {
            hkLogs.save(HousekeepingLog.builder().room(room).cleaner(cleaner).cleanerName(cleaner.getFullName()).startedAt(end.plusMinutes(10))
                    .finishedAt(end.plusMinutes(22)).result(HousekeepingLog.Result.PASSED).inspectedBy(supervisor).build());
        }
    }

    private void review(Booking b, Guest g, Slot s) {
        if (rnd.nextInt(100) >= 36) {
            return;
        }
        LocalDateTime at = s.out.plusDays(1 + rnd.nextInt(5)).atTime(8 + rnd.nextInt(14), rnd.nextInt(60));
        if (at.isAfter(now)) {
            return;
        }
        int rating;
        String comment;
        if (g.p.foreigner()) {
            String[] pool = DemoTexts.foreignReviews(g.p.nationality());
            comment = people.pick(pool);
            boolean complaint = comment.contains("slow") || comment.contains("noisy") || comment.contains("lent") || comment.contains("와이파이") || comment.contains("隔音");
            rating = complaint ? 4 : rnd.nextInt(100) < 75 ? 5 : 4;
        } else {
            int r = rnd.nextInt(100);
            rating = r < 55 ? 5 : r < 85 ? 4 : r < 95 ? 3 : 1 + rnd.nextInt(2);
            comment = people.pick(rating == 5 ? DemoTexts.REVIEW_5 : rating == 4 ? DemoTexts.REVIEW_4 : rating == 3 ? DemoTexts.REVIEW_3 : DemoTexts.REVIEW_LOW);
        }
        Review.ReviewBuilder rb = Review.builder().booking(b).customer(g.user).rating(rating).comment(comment).hidden(false);
        LocalDateTime replied = at.plusHours(6 + rnd.nextInt(24));
        if ((rating <= 3 ? rnd.nextInt(100) < 85 : rnd.nextInt(100) < 35) && !replied.isAfter(now)) {
            rb.reply(rating <= 3 ? people.pick(DemoTexts.REPLY_BAD) : g.p.foreigner() ? DemoTexts.REPLY_GOOD[2] : people.pick(Arrays.copyOf(DemoTexts.REPLY_GOOD, 2)))
                    .repliedAt(replied);
        }
        Review saved = reviews.save(rb.build());
        fix("reviews", saved.getId(), at);
    }

    private void registerGuests(Booking b, Guest g, Slot s, boolean online) {
        boolean preDeclared = online && rnd.nextInt(100) < 40;
        LocalDateTime at = preDeclared ? s.in.minusDays(1).atTime(20, rnd.nextInt(60)) : s.in.atTime(14, 0).plusMinutes(rnd.nextInt(240));
        if (at.isAfter(now)) {
            at = now.minusMinutes(30);
        }
        Person main = s.group >= 0 ? people.person(today, false) : g.p;
        String mainName = s.group >= 0 ? s.guestName : main.fullName();
        saveGuest(b, main, mainName, preDeclared, at);
        boolean family = s.guests >= 3;
        for (int i = 1; i < s.guests; i++) {
            boolean child = family && i >= 2;
            Person c = s.group >= 0 ? people.person(today, false) : people.companion(main, today, child);
            saveGuest(b, c, c.fullName(), preDeclared, at.plusMinutes(i));
        }
    }

    private void saveGuest(Booking b, Person p, String name, boolean online, LocalDateTime at) {
        boolean child = p.birthDate().isAfter(today.minusYears(14));
        IdDocumentType type = p.foreigner() ? IdDocumentType.PASSPORT : child ? IdDocumentType.BIRTH_CERTIFICATE : IdDocumentType.CCCD;
        BookingGuest bg = bookingGuests.save(BookingGuest.builder().booking(b).fullName(name).dateOfBirth(p.birthDate()).gender(p.gender())
                .idType(type).idNumber(p.idNumber()).nationality(p.nationality()).address(p.address()).declaredOnline(online).build());
        fix("booking_guests", bg.getId(), at);
    }

    private void chat(Booking b, Guest g, Slot s) {
        String[][] pool = DemoTexts.MESSAGES;
        String[] pair = g.p.foreigner() ? pool[pool.length - 1] : pool[rnd.nextInt(pool.length - 1)];
        LocalDateTime asked = s.created.plusHours(2 + rnd.nextInt(Math.max(1, (int) Math.min(240, ChronoUnit.HOURS.between(s.created, s.in.atTime(12, 0))))));
        if (asked.isAfter(now)) {
            asked = now.minusMinutes(10 + rnd.nextInt(120));
        }
        LocalDateTime answered = asked.plusMinutes(8 + rnd.nextInt(80));
        boolean replied = !answered.isAfter(now);
        BookingMessage q = messages.save(BookingMessage.builder().booking(b).fromGuest(true).body(pair[0]).readAt(replied ? answered.minusMinutes(2) : null).build());
        fix("booking_messages", q.getId(), asked);
        if (replied) {
            LocalDateTime read = answered.plusMinutes(5 + rnd.nextInt(120));
            BookingMessage a = messages.save(BookingMessage.builder().booking(b).fromGuest(false).body(pair[1]).staffName(staffName())
                    .readAt(read.isAfter(now) ? null : read).build());
            fix("booking_messages", a.getId(), answered);
        }
    }

    // ===================== Van hanh khac =====================

    private void lostAndFound() {
        List<Booking> list = new ArrayList<>(recentCheckouts);
        Collections.shuffle(list, rnd);
        for (int i = 0; i < Math.min(DemoTexts.LOST_ITEMS.length, list.size()); i++) {
            Booking b = list.get(i);
            String[] item = DemoTexts.LOST_ITEMS[i];
            LocalDateTime found = b.getCheckOutDate().atTime(12, 30).plusMinutes(rnd.nextInt(180));
            LostItem.LostItemBuilder lb = LostItem.builder().room(b.getRoom()).booking(b).description(item[0]).storageLocation(item[1])
                    .foundBy(people.pick(housekeepers).getFullName()).foundAt(found).guestNotifiedAt(found.plusHours(1 + rnd.nextInt(3)));
            long age = ChronoUnit.DAYS.between(found.toLocalDate(), today);
            if (age > 90 && rnd.nextBoolean()) {
                lb.status(LostItem.Status.DISPOSED).closedAt(found.plusDays(90)).closeNote("Quá 90 ngày khách không nhận, đã thanh lý theo quy định");
            } else if (age > 3 && rnd.nextInt(100) < 65) {
                lb.status(LostItem.Status.RETURNED).closedAt(found.plusDays(1 + rnd.nextInt(3)))
                        .closeNote(rnd.nextBoolean() ? "Đã gửi chuyển phát nhanh cho khách (khách trả phí)" : "Khách quay lại nhận tại lễ tân");
            } else {
                lb.status(LostItem.Status.STORED);
            }
            lostItems.save(lb.build());
        }
    }

    private void maintenance() {
        String[] techs = {"Trần Văn Bình (kỹ thuật)", "Nguyễn Đức Mạnh (kỹ thuật)"};
        long span = ChronoUnit.DAYS.between(START, today.minusDays(3));
        for (int i = 0; i < 28; i++) {
            String[] t = DemoTexts.TICKETS[rnd.nextInt(DemoTexts.TICKETS.length)];
            Room room = people.pick(roomList);
            LocalDateTime created = START.plusDays(rnd.nextLong(span)).atTime(8 + rnd.nextInt(12), rnd.nextInt(60));
            boolean urgent = rnd.nextInt(100) < 20;
            MaintenanceTicket mt = tickets.save(MaintenanceTicket.builder().room(room).title(t[0]).description(t[1])
                    .priority(urgent ? MaintenanceTicket.Priority.URGENT : MaintenanceTicket.Priority.NORMAL).status(MaintenanceTicket.Status.DONE)
                    .blocksRoom(false).reportedBy(rnd.nextBoolean() ? staffName() : people.pick(housekeepers).getFullName())
                    .assignee(people.pick(techs)).resolutionNote(t[2]).resolvedAt(created.plusHours(urgent ? 2 + rnd.nextInt(5) : 6 + rnd.nextInt(40))).build());
            fix("maintenance_tickets", mt.getId(), created);
        }
        String[][] current = {{"Thấm nước trần nhà tắm", "Trần nhà tắm bị thấm sau mưa lớn, đang chống thấm lại."},
                {"Điều hòa hỏng máy nén", "Máy nén điều hòa hỏng, chờ thay máy mới."}};
        int k = 0;
        for (Room room : roomList) {
            if (!maintenanceRoomIds.contains(room.getId())) {
                continue;
            }
            MaintenanceTicket mt = tickets.save(MaintenanceTicket.builder().room(room).title(current[k][0]).description(current[k][1])
                    .priority(MaintenanceTicket.Priority.URGENT).status(k == 0 ? MaintenanceTicket.Status.IN_PROGRESS : MaintenanceTicket.Status.OPEN)
                    .blocksRoom(true).expectedDoneDate(today.plusDays(4)).reportedBy(people.pick(housekeepers).getFullName())
                    .assignee(k == 0 ? techs[0] : null).build());
            fix("maintenance_tickets", mt.getId(), today.minusDays(1).atTime(9, 20 + k));
            k++;
        }
        for (String[] t : new String[][]{DemoTexts.TICKETS[5], DemoTexts.TICKETS[9]}) {
            MaintenanceTicket mt = tickets.save(MaintenanceTicket.builder().room(people.pick(roomList)).title(t[0]).description(t[1])
                    .priority(MaintenanceTicket.Priority.NORMAL).status(MaintenanceTicket.Status.OPEN).blocksRoom(false)
                    .reportedBy(staffName()).build());
            fix("maintenance_tickets", mt.getId(), now.minusHours(3 + rnd.nextInt(10)));
        }
    }

    private void contactMessages() {
        for (int i = 0; i < DemoTexts.CONTACTS.length * 2; i++) {
            String[] c = DemoTexts.CONTACTS[i % DemoTexts.CONTACTS.length];
            Person p = people.person(today, false);
            boolean recent = i >= DemoTexts.CONTACTS.length * 2 - 4;
            LocalDateTime at = recent ? now.minusHours(2 + rnd.nextInt(60)) : today.minusDays(5 + rnd.nextInt(150)).atTime(8 + rnd.nextInt(14), rnd.nextInt(60));
            boolean handled = !recent && !c[1].isEmpty();
            ContactMessage m = contacts.save(ContactMessage.builder().fullName(p.fullName()).email(p.email()).phone(p.phone()).message(c[0])
                    .reply(handled ? c[1] : null).repliedAt(handled ? at.plusHours(1 + rnd.nextInt(20)) : null)
                    .handledBy(handled ? staffName() : null).handled(handled || (!recent && rnd.nextBoolean())).build());
            fix("contact_messages", m.getId(), at);
        }
    }

    private void giftVouchers() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String[] notes = {"Chúc bố mẹ có kỳ nghỉ thật vui!", "Happy birthday em yêu!", "Quà cưới cho hai bạn, trăm năm hạnh phúc!",
                "Cảm ơn sếp đã luôn đồng hành cùng team.", "Chúc mừng 20/10!"};
        for (int i = 0; i < 18; i++) {
            StringBuilder code = new StringBuilder("QT-");
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    code.append('-');
                }
                code.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            Person buyer = people.person(today, false);
            Person to = people.person(today, false);
            BigDecimal amount = BigDecimal.valueOf(new long[]{1_000_000, 2_000_000, 3_000_000, 5_000_000}[rnd.nextInt(4)]);
            LocalDateTime created = today.minusDays(3 + rnd.nextInt(400)).atTime(9 + rnd.nextInt(12), rnd.nextInt(60));
            boolean pending = i >= 16;
            if (pending) {
                created = now.minusHours(1 + i);
            }
            BigDecimal balance = pending || rnd.nextBoolean() ? amount : amount.subtract(round(amount.multiply(BigDecimal.valueOf(rnd.nextDouble())), 100_000));
            GiftVoucher v = vouchers.save(GiftVoucher.builder().code(code.toString()).amount(amount).balance(balance)
                    .status(pending ? GiftVoucher.Status.PENDING_PAYMENT : GiftVoucher.Status.ACTIVE)
                    .purchaserName(buyer.fullName()).purchaserEmail(buyer.email()).recipientName(to.fullName()).recipientEmail(to.email())
                    .message(people.pick(notes)).onlineRef(pending ? null : String.valueOf(14_000_000 + rnd.nextInt(9_000_000)))
                    .paidAt(pending ? null : created.plusMinutes(4)).expiresAt(pending ? null : created.plusMonths(12)).build());
            fix("gift_vouchers", v.getId(), created);
        }
    }

    private void favoriteRooms() {
        List<Long> customers = jdbc.queryForList("select id from users where role = 'CUSTOMER' and email like '%@example.%' order by rand(42) limit 300", Long.class);
        Set<String> seen = new HashSet<>();
        for (Long uid : customers) {
            Room room = people.pick(roomList);
            if (seen.add(uid + ":" + room.getId())) {
                FavoriteRoom f = favorites.save(FavoriteRoom.builder().user(users.getReferenceById(uid)).room(room).build());
                fix("favorite_rooms", f.getId(), today.minusDays(rnd.nextInt(300)).atTime(20, rnd.nextInt(60)));
            }
        }
    }

    // Phong co khach dang o -> OCCUPIED, phong dang sua -> MAINTENANCE, vai phong vua tra hom qua chua don -> NOT_READY
    private void roomStatuses() {
        Set<Long> occupied = new HashSet<>(jdbc.queryForList("select room_id from bookings where status = 'CHECKED_IN'", Long.class));
        Set<Long> leftYesterday = new HashSet<>(jdbc.queryForList("select room_id from bookings where status = 'CHECKED_OUT' and check_out_date = ?",
                Long.class, today.minusDays(1)));
        int dirty = 0;
        for (Room r : roomList) {
            Room room = rooms.findById(r.getId()).orElseThrow();
            if (maintenanceRoomIds.contains(room.getId())) {
                room.setStatus(RoomStatus.MAINTENANCE);
            } else if (occupied.contains(room.getId())) {
                room.setStatus(RoomStatus.OCCUPIED);
            } else if (leftYesterday.contains(room.getId()) && dirty < 6) {
                room.setStatus(RoomStatus.NOT_READY);
                room.setDirtySince(today.minusDays(1).atTime(12, 20 + dirty * 7));
                if (dirty < 2) {
                    room.setCleaningAssignee(housekeepers.get(1 + dirty));
                }
                dirty++;
            } else {
                room.setStatus(RoomStatus.AVAILABLE);
            }
            rooms.save(room);
        }
    }

    // ===================== Ke toan: chi phi, ke hoach =====================

    private void costSettingsAndExpenses() {
        expenseService.updateCostSettings(ExpenseService.SUGGESTED_COST_PERCENT, new BigDecimal("1.1"), BigDecimal.valueOf(85_000));
        String by = accountant.getFullName();
        YearMonth first = YearMonth.from(START);
        YearMonth current = YearMonth.from(today);
        Expense.Recurrence monthly = Expense.Recurrence.MONTHLY;
        Expense.Recurrence once = Expense.Recurrence.ONE_OFF;
        expenseService.create(Expense.Category.PAYROLL, "Lương & BHXH 42 nhân viên", BigDecimal.valueOf(365_000_000), monthly, null, first, YearMonth.of(2025, 12), by);
        expenseService.create(Expense.Category.PAYROLL, "Lương & BHXH 42 nhân viên (điều chỉnh lương 2026)", BigDecimal.valueOf(395_000_000), monthly, null, YearMonth.of(2026, 1), null, by);
        expenseService.create(Expense.Category.RENT, "Khấu hao công trình & nội thất", BigDecimal.valueOf(150_000_000), monthly, null, first, null, by);
        expenseService.create(Expense.Category.TAX_FEES, "Bảo hiểm tài sản, phí môi trường, thuế môn bài", BigDecimal.valueOf(18_500_000), monthly, null, first, null, by);
        expenseService.create(Expense.Category.UTILITIES, "Internet cáp quang & truyền hình", BigDecimal.valueOf(4_800_000), monthly, null, first, null, by);
        expenseService.create(Expense.Category.MARKETING, "Quảng cáo Facebook, Google Ads", BigDecimal.valueOf(22_000_000), monthly, null, first, null, by);
        expenseService.create(Expense.Category.MAINTENANCE, "Hóa chất & bảo dưỡng hồ bơi", BigDecimal.valueOf(7_500_000), monthly, null, first, null, by);
        for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
            long[] n = monthNights.getOrDefault(m, new long[2]);
            boolean summer = m.getMonthValue() >= 6 && m.getMonthValue() <= 8;
            LocalDate end = m.atEndOfMonth();
            LocalDate mid = m.atDay(5);
            LocalDate later = m.atDay(20);
            String label = m.getMonthValue() + "/" + m.getYear();
            if (end.isBefore(today)) {
                expenseService.create(Expense.Category.UTILITIES, "Tiền điện tháng " + label + " (EVN Quảng Ninh)",
                        round(BigDecimal.valueOf(48_000_000 + n[0] * (summer ? 115_000 : 90_000)), 1000), once, end, null, null, by);
                expenseService.create(Expense.Category.UTILITIES, "Tiền nước tháng " + label, round(BigDecimal.valueOf(6_000_000 + n[1] * 12_000), 1000), once, end, null, null, by);
                expenseService.create(Expense.Category.OTHER, "Phí ngân hàng, văn phòng phẩm, chi lặt vặt", BigDecimal.valueOf(3_000_000 + rnd.nextInt(30) * 100_000), once, end, null, null, by);
            }
            for (LocalDate d : List.of(mid, later)) {
                if (!d.isAfter(today)) {
                    expenseService.create(Expense.Category.SUPPLIES, "Vật tư buồng phòng (amenity, giấy, túi rác) - đợt " + (d == mid ? 1 : 2),
                            round(BigDecimal.valueOf(Math.max(2_000_000, n[0] * 11_000)), 1000), once, d, null, null, by);
                    expenseService.create(Expense.Category.FOOD_BEVERAGE, "Nguyên liệu buffet sáng & nhà hàng - đợt " + (d == mid ? 1 : 2),
                            round(BigDecimal.valueOf(Math.max(5_000_000, n[1] * 30_000)), 1000), once, d, null, null, by);
                }
            }
            if (summer && !m.atDay(28).isAfter(today)) {
                expenseService.create(Expense.Category.PAYROLL, "Lương nhân viên thời vụ hè (8 người)", BigDecimal.valueOf(48_000_000), once, m.atDay(28), null, null, by);
            }
            if (m.getMonthValue() % 3 == 0 && !m.atDay(12).isAfter(today)) {
                expenseService.create(Expense.Category.MAINTENANCE, "Bảo dưỡng định kỳ hệ thống điều hòa", BigDecimal.valueOf(26_000_000), once, m.atDay(12), null, null, by);
            }
        }
        Object[][] oneOffs = {
                {Expense.Category.MARKETING, "Hợp tác KOL du lịch quảng bá mùa thu", 40_000_000, LocalDate.of(2025, 9, 10)},
                {Expense.Category.MARKETING, "Quảng cáo Tết trên Traveloka", 35_000_000, LocalDate.of(2026, 1, 8)},
                {Expense.Category.PAYROLL, "Thưởng Tết nhân viên", 280_000_000, LocalDate.of(2026, 2, 10)},
                {Expense.Category.MARKETING, "Chiến dịch hè trên Agoda / Traveloka", 55_000_000, LocalDate.of(2026, 5, 6)},
                {Expense.Category.MAINTENANCE, "Sơn lại mặt tiền và lan can ban công", 120_000_000, LocalDate.of(2026, 4, 2)},
                {Expense.Category.MAINTENANCE, "Thay 12 tivi phòng Deluxe", 84_000_000, LocalDate.of(2025, 10, 20)},
                {Expense.Category.SUPPLIES, "Thay ga gối, khăn tắm toàn bộ phòng", 96_000_000, LocalDate.of(2026, 5, 15)},
                {Expense.Category.OTHER, "Tiệc tất niên nhân viên", 45_000_000, LocalDate.of(2026, 1, 30)},
        };
        for (Object[] o : oneOffs) {
            LocalDate d = (LocalDate) o[3];
            if (!d.isAfter(today)) {
                expenseService.create((Expense.Category) o[0], (String) o[1], BigDecimal.valueOf((Integer) o[2]), once, d, null, null, by);
            }
        }
    }

    private void budget2026() {
        int roomCount = roomList.size();
        double avgPrice = roomList.stream().mapToDouble(r -> r.getPrice().doubleValue()).average().orElse(1_200_000);
        for (int month = 1; month <= 12; month++) {
            YearMonth m = YearMonth.of(2026, month);
            int occ = (int) Math.round(Math.min(0.92, MONTH_OCC[month - 1] * 1.07 + 0.04) * 100);
            double season = month >= 6 && month <= 8 ? 1.2 : month >= 11 || month <= 3 ? 0.9 : 1.0;
            BigDecimal adr = round(BigDecimal.valueOf(avgPrice * season * 1.08), 10_000);
            BigDecimal revenue = round(BigDecimal.valueOf(roomCount * m.lengthOfMonth() * occ / 100.0 * adr.doubleValue() * 1.12), 1_000_000);
            BigDecimal cost = round(BigDecimal.valueOf(640_000_000 + roomCount * m.lengthOfMonth() * occ / 100.0 * 200_000), 1_000_000);
            budgetService.save(m, revenue, cost, occ, adr);
        }
    }

    // ===================== Ngay tao: ghi de created_at (cot updatable = false) =====================

    private void fix(String table, Long id, LocalDateTime at) {
        fixes.computeIfAbsent(table, k -> new ArrayList<>()).add(new Object[]{java.sql.Timestamp.valueOf(at), id});
    }

    private void applyFixes() {
        fixes.forEach((table, rows) -> jdbc.batchUpdate("update " + table + " set created_at = ? where id = ?", rows));
        // Hang thanh vien + diem hien tai theo lich su
        jdbc.update("update users u set customer_type = case when (select count(*) from bookings b where b.customer_id = u.id and b.status = 'CHECKED_OUT') >= 5 then 'VIP' "
                + "when (select count(*) from bookings b where b.customer_id = u.id and b.status = 'CHECKED_OUT') >= 2 then 'REGULAR' else 'NEW' end, "
                + "loyalty_points = coalesce((select t.balance_after from point_transactions t where t.user_id = u.id order by t.id desc limit 1), 0) "
                + "where u.role = 'CUSTOMER' and u.email like '%@example.%'");
    }
}
