package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.entity.OtaBlock;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Gia han luu tru cho khach dang o (CHECKED_IN), gom 2 truong hop:
// 1) Khach xin o them: chon ngay tra moi, cac dem toi phai con phong (khong trung khach khac / dot da ban OTA).
// 2) Khach o qua ngay tra phong (qua han): cac dem da qua coi nhu da o -> bat buoc tinh tien (hoac mien co ly do)
//    truoc khi thu tien / check-out; khong kiem trung phong cho cac dem nay vi khach thuc te da o roi.
// Tien cac dem o them = gia niem yet tung dem (ngay thuong / cuoi tuan / dip le), khong ap lai ma giam gia cua don,
// ghi thanh 1 dong phu phi loai EXTENDED_STAY (hien ro tren man hinh check-out, hoa don, email).
@Service
public class StayExtensionService {

    public static final int MAX_EXTRA_NIGHTS = 30;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final PricingService pricingService;
    private final BookingChargeRepository chargeRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private Clock clock = Clock.systemDefaultZone();

    public StayExtensionService(BookingService bookingService, PaymentService paymentService, PricingService pricingService,
                                BookingChargeRepository chargeRepository, BookingRepository bookingRepository,
                                RoomRepository roomRepository) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.pricingService = pricingService;
        this.chargeRepository = chargeRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    // Cho test co dinh "hom nay"
    void setClock(Clock clock) {
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    // 1 dem o them: past = dem da qua (khach o qua han), chi de tinh tien, khong kiem trung phong
    public record Night(LocalDate date, BigDecimal price, String label, boolean past) {
    }

    // Bao gia gia han: conflicts / otaBlocks / turnoverMessage khac rong -> khong gia han duoc, phai xu ly truoc
    public record Quote(Booking booking, LocalDate oldCheckOut, LocalDate newCheckOut, List<Night> nights, BigDecimal amount,
                        long overdueNights, List<Booking> conflicts, List<OtaBlock> otaBlocks, String turnoverMessage) {

        public long getNightCount() {
            return nights.size();
        }

        public boolean isPossible() {
            return conflicts.isEmpty() && otaBlocks.isEmpty() && turnoverMessage == null;
        }

        public BigDecimal getOverdueAmount() {
            return nights.stream().filter(Night::past).map(Night::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // So dem khach da o qua ngay tra (0 = chua qua han hoac khong con o)
    public long overdueNights(Booking booking) {
        if (booking.getStatus() != BookingStatus.CHECKED_IN || !booking.getCheckOutDate().isBefore(today())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(booking.getCheckOutDate(), today());
    }

    // Ngay tra som nhat duoc chon: qua han thi it nhat la hom nay (ca cac dem da o), khong thi ngay tra cu + 1
    public LocalDate minNewCheckOut(Booking booking) {
        LocalDate next = booking.getCheckOutDate().plusDays(1);
        return next.isBefore(today()) ? today() : next;
    }

    public LocalDate maxNewCheckOut(Booking booking) {
        return booking.getCheckOutDate().plusDays(MAX_EXTRA_NIGHTS);
    }

    // 1 o tren lich gia han (dem `date` cua phong nay):
    // state: past = dem da qua (khach o qua han, luon tinh tien) / free = trong / booked = da co don khac /
    //        ota = da ban tren Booking.com, Airbnb / blocked = trong nhung bi chan boi dem da co khach truoc do
    // checkOut = ngay tra neu chon o den het dem nay (date + 1); total = tong tien o them neu chon den dem nay
    public record CalendarNight(LocalDate date, BigDecimal price, String label, String state, Booking by,
                                String otaSummary, LocalDate checkOut, BigDecimal total) {

        public boolean isSelectable() {
            return "past".equals(state) || "free".equals(state);
        }
    }

    // Lich cac dem tu ngay tra hien tai den gioi han gia han - le tan bam vao 1 ngay biet ngay ay da co khach dat chua
    @Transactional(readOnly = true)
    public List<CalendarNight> calendar(Booking booking) {
        LocalDate oldOut = booking.getCheckOutDate();
        LocalDate end = maxNewCheckOut(booking);
        LocalDate today = today();
        LocalDate from = oldOut.isBefore(today) ? today : oldOut;
        List<Booking> others = List.of();
        List<OtaBlock> ota = List.of();
        if (from.isBefore(end)) {
            BookingService.Availability availability = bookingService.checkAvailability(booking.getRoom(), from, end, booking.getId());
            others = availability.conflicts();
            ota = availability.otaBlocks();
        }
        List<CalendarNight> result = new java.util.ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        boolean reachable = true;
        for (PricingService.NightPrice n : pricingService.nightlyPrices(booking.getRoom().getPrice(), oldOut, end)) {
            LocalDate night = n.night();
            running = running.add(n.price());
            Booking by = others.stream()
                    .filter(b -> !b.getCheckInDate().isAfter(night) && b.getCheckOutDate().isAfter(night))
                    .findFirst().orElse(null);
            OtaBlock block = ota.stream()
                    .filter(o -> !o.getStartDate().isAfter(night) && o.getEndDate().isAfter(night))
                    .findFirst().orElse(null);
            String state;
            if (night.isBefore(today)) {
                state = "past";
            } else if (by != null) {
                state = "booked";
            } else if (block != null) {
                state = "ota";
            } else {
                state = reachable ? "free" : "blocked";
            }
            if ("booked".equals(state) || "ota".equals(state)) {
                reachable = false;
            }
            result.add(new CalendarNight(night, n.price(), n.label(), state, by,
                    block != null ? block.getSummary() : null, night.plusDays(1), running));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Quote quote(Long bookingId, LocalDate newCheckOut) {
        return quote(requireExtendable(bookingId), newCheckOut);
    }

    private Quote quote(Booking booking, LocalDate newCheckOut) {
        if (newCheckOut == null) {
            throw new BusinessException("Vui lòng chọn ngày trả phòng mới");
        }
        LocalDate oldOut = booking.getCheckOutDate();
        if (!newCheckOut.isAfter(oldOut)) {
            throw new BusinessException("Ngày trả phòng mới phải sau ngày trả hiện tại (" + oldOut.format(DAY) + ")");
        }
        LocalDate min = minNewCheckOut(booking);
        if (newCheckOut.isBefore(min)) {
            throw new BusinessException("Khách đã ở quá hạn đến hôm nay, ngày trả mới ít nhất là hôm nay (" + min.format(DAY) + ")");
        }
        if (newCheckOut.isAfter(maxNewCheckOut(booking))) {
            throw new BusinessException("Mỗi lần gia hạn tối đa " + MAX_EXTRA_NIGHTS + " đêm");
        }
        LocalDate today = today();
        List<Night> nights = pricingService.nightlyPrices(booking.getRoom().getPrice(), oldOut, newCheckOut).stream()
                .map(n -> new Night(n.night(), n.price(), n.label(), n.night().isBefore(today)))
                .toList();
        BigDecimal amount = nights.stream().map(Night::price).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Chi cac dem tu hom nay tro di moi can con phong
        LocalDate from = oldOut.isBefore(today) ? today : oldOut;
        List<Booking> conflicts = List.of();
        List<OtaBlock> ota = List.of();
        String turnover = null;
        if (from.isBefore(newCheckOut)) {
            BookingService.Availability availability =
                    bookingService.checkAvailability(booking.getRoom(), from, newCheckOut, booking.getId());
            conflicts = availability.conflicts();
            ota = availability.otaBlocks();
            LocalTime latest = availability.latestCheckOut();
            if (latest != null && booking.getCheckOutTime() != null && booking.getCheckOutTime().isAfter(latest)) {
                turnover = "Ngày " + newCheckOut.format(DAY) + " có khách khác nhận phòng này lúc " + latest
                        + ", khách phải trả phòng trước giờ đó (giờ trả hiện tại " + booking.getCheckOutTime() + ")";
            }
        }
        return new Quote(booking, oldOut, newCheckOut, nights, amount, overdueNights(booking), conflicts, ota, turnover);
    }

    // Gia han: waive = mien tien cac dem o them (bat buoc ghi ly do, VD resort tang khach do su co)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingCharge extend(Long bookingId, LocalDate newCheckOut, boolean waive, String reason, String staffName) {
        Booking booking = requireExtendable(bookingId);
        try {
            roomRepository.findByIdForUpdate(booking.getRoom().getId());
        } catch (PessimisticLockingFailureException | QueryTimeoutException ex) {
            throw BusinessException.of("err.roomBusy");
        }
        Quote quote = quote(booking, newCheckOut);
        if (!quote.conflicts().isEmpty()) {
            Booking next = quote.conflicts().get(0);
            throw new BusinessException("Phòng " + booking.getRoom().getRoomNumber() + " đã có đơn #" + next.getCode()
                    + " (" + next.getGuestName() + ") từ " + next.getCheckInDate().format(DAY)
                    + ". Hãy đổi phòng cho đơn đó hoặc chuyển khách này sang phòng khác (nút Đổi phòng) rồi gia hạn");
        }
        if (!quote.otaBlocks().isEmpty()) {
            throw new BusinessException("Các đêm này phòng đã bán trên Booking.com / Airbnb, không gia hạn được ở phòng này");
        }
        if (quote.turnoverMessage() != null) {
            throw new BusinessException(quote.turnoverMessage());
        }
        String why = reason == null ? "" : reason.trim();
        if (waive && why.length() < 5) {
            throw new BusinessException("Miễn tiền ở thêm phải ghi lý do (ít nhất 5 ký tự)");
        }

        StringBuilder desc = new StringBuilder()
                .append(quote.getNightCount()).append(" đêm ")
                .append(quote.oldCheckOut().format(DAY)).append(" → ").append(newCheckOut.format(DAY));
        long past = quote.nights().stream().filter(Night::past).count();
        if (past > 0) {
            desc.append(" (quá hạn ").append(past).append(" đêm)");
        }
        if (waive) {
            desc.append(" · MIỄN ").append(money(quote.amount())).append(": ").append(why);
        }
        if (staffName != null && !staffName.isBlank()) {
            desc.append(" · ").append(staffName.trim());
        }
        String text = desc.length() > 200 ? desc.substring(0, 200) : desc.toString();

        BookingCharge charge = chargeRepository.save(BookingCharge.builder()
                .booking(booking)
                .type(ChargeType.EXTENDED_STAY)
                .description(text)
                .amount(waive ? BigDecimal.ZERO : quote.amount())
                .stayFrom(quote.oldCheckOut())
                .stayTo(newCheckOut)
                .build());
        booking.setCheckOutDate(newCheckOut);
        bookingRepository.save(booking);
        return charge;
    }

    // Lan gia han gan nhat con hoan tac duoc (bam nham ngay): chua thu tien va chua dem nao trong do da qua
    @Transactional(readOnly = true)
    public Optional<BookingCharge> undoableExtension(Booking booking) {
        if (booking.getStatus() != BookingStatus.CHECKED_IN || paymentService.isPaid(booking.getId())) {
            return Optional.empty();
        }
        return lastExtension(booking)
                .filter(c -> booking.getCheckOutDate().equals(c.getStayTo()) && !c.getStayFrom().isBefore(today()));
    }

    @Transactional
    public Booking undoLastExtension(Long bookingId) {
        Booking booking = requireExtendable(bookingId);
        BookingCharge last = lastExtension(booking)
                .orElseThrow(() -> new BusinessException("Đơn này chưa gia hạn lần nào"));
        if (!booking.getCheckOutDate().equals(last.getStayTo())) {
            throw new BusinessException("Ngày trả phòng đã thay đổi sau lần gia hạn này, không hoàn tác được");
        }
        if (last.getStayFrom().isBefore(today())) {
            throw new BusinessException("Lần gia hạn này có đêm khách đã ở rồi, không hoàn tác được");
        }
        booking.setCheckOutDate(last.getStayFrom());
        chargeRepository.delete(last);
        return bookingRepository.save(booking);
    }

    private Optional<BookingCharge> lastExtension(Booking booking) {
        return chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream()
                .filter(c -> c.getType() == ChargeType.EXTENDED_STAY && c.getStayFrom() != null && c.getStayTo() != null)
                .max(Comparator.comparing(BookingCharge::getStayTo).thenComparing(BookingCharge::getId));
    }

    private Booking requireExtendable(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ gia hạn được đơn khách đang ở (đã check-in)");
        }
        if (paymentService.isPaid(bookingId)) {
            throw new BusinessException("Đơn đã thu tiền nên không gia hạn trên đơn này được. Hãy check-out rồi tạo đơn mới cho các đêm ở thêm");
        }
        return booking;
    }

    private static String money(BigDecimal amount) {
        return String.format("%,.0f", amount).replace(',', '.') + " VND";
    }
}
