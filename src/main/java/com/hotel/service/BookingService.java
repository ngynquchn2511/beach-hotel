package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.AirportTransferRepository;
import com.hotel.repository.TourBookingRepository;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingService {

    // Chi cho phep sua gio nhan/tra hoac doi combo neu con truoc it nhat 6 tieng so voi gio nhan phong
    private static final int EDIT_CUTOFF_HOURS = 6;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final EmailService emailService;
    private final BookingChargeRepository chargeRepository;
    private final PricingService pricingService;
    private final BookingGuestRepository guestRepository;
    private final TourBookingRepository tourBookingRepository;
    private final AirportTransferRepository transferRepository;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository,
                           ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                           EmailService emailService, BookingChargeRepository chargeRepository,
                           PricingService pricingService, BookingGuestRepository guestRepository,
                           TourBookingRepository tourBookingRepository, AirportTransferRepository transferRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.emailService = emailService;
        this.chargeRepository = chargeRepository;
        this.pricingService = pricingService;
        this.guestRepository = guestRepository;
        this.tourBookingRepository = tourBookingRepository;
        this.transferRepository = transferRepository;
    }

    // Cac don dang giu phong (chua huy, chua tra phong)
    private static final List<BookingStatus> HOLDING_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Ket qua kiem tra phong trong cho 1 khoang ngay - dung chung cho trang chi tiet phong, trang xac nhan va luc luu don
    // conflicts: cac don dang giu phong trung khoang ngay (co the la don CHO XAC NHAN)
    // freeFrom: thoi diem phong trong tro lai sau cac don trung (tinh ca cac don noi tiep nhau)
    // earliestCheckIn: ngay nhan phong co khach truoc tra phong -> chi nhan phong tu gio nay
    // latestCheckOut: ngay tra phong co khach sau nhan phong -> phai tra phong truoc gio nay
    public record Availability(boolean maintenance, List<Booking> conflicts, LocalDateTime freeFrom,
                               LocalTime earliestCheckIn, LocalTime latestCheckOut) {

        public boolean isAvailable() {
            return !maintenance && conflicts.isEmpty();
        }

        public boolean isOnlyPendingConflicts() {
            return !conflicts.isEmpty() && conflicts.stream().allMatch(b -> b.getStatus() == BookingStatus.PENDING);
        }

        // Cau thong bao cho khach khi khong dat duoc (tieng Viet)
        public String describe() {
            return describe(BusinessException::vietnamese);
        }

        // Cau thong bao theo ngon ngu cua translator (giao dien khach song ngu)
        public String describe(BusinessException.Translator t) {
            if (maintenance) {
                return t.t("avail.maintenance");
            }
            if (conflicts.isEmpty()) {
                return null;
            }
            List<String> parts = new java.util.ArrayList<>();
            for (Booking b : conflicts) {
                parts.add(t.t(b.getStatus() == BookingStatus.PENDING ? "avail.pendingRange" : "avail.confirmedRange",
                        b.getCheckInDate().format(DAY_FMT), b.getCheckOutDate().format(DAY_FMT)));
            }
            StringBuilder sb = new StringBuilder(t.t("avail.taken", String.join(", ", parts)));
            if (freeFrom != null) {
                sb.append(' ').append(t.t("avail.freeFrom", freeFrom.format(TIME_FMT),
                        freeFrom.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            }
            if (isOnlyPendingConflicts()) {
                sb.append(' ').append(t.t("avail.pendingNote"));
            }
            return sb.toString();
        }
    }

    public Availability checkAvailability(Room room, LocalDate checkIn, LocalDate checkOut) {
        return checkAvailability(room, checkIn, checkOut, null);
    }

    // excludeBookingId: bo qua chinh don dang sua (VD khach doi gio nhan/tra)
    public Availability checkAvailability(Room room, LocalDate checkIn, LocalDate checkOut, Long excludeBookingId) {
        List<Booking> active = bookingRepository.findByRoomIdAndStatusIn(room.getId(), HOLDING_STATUSES).stream()
                .filter(b -> excludeBookingId == null || !excludeBookingId.equals(b.getId()))
                .toList();

        List<Booking> conflicts = active.stream()
                .filter(b -> b.getCheckInDate().isBefore(checkOut) && b.getCheckOutDate().isAfter(checkIn))
                .sorted(java.util.Comparator.comparing(Booking::getCheckInDate))
                .toList();

        // Phong trong lai sau don trung ket thuc muon nhat; neu ngay do lai co don khac noi tiep thi tinh tiep
        LocalDateTime freeFrom = null;
        if (!conflicts.isEmpty()) {
            Booking last = conflicts.stream().max(java.util.Comparator.comparing(Booking::getCheckOutDate)).get();
            LocalDate freeDate = last.getCheckOutDate();
            LocalTime freeTime = last.getCheckOutTime();
            boolean extended = true;
            while (extended) {
                extended = false;
                for (Booking b : active) {
                    if (!b.getCheckInDate().isAfter(freeDate) && b.getCheckOutDate().isAfter(freeDate)) {
                        freeDate = b.getCheckOutDate();
                        freeTime = b.getCheckOutTime();
                        extended = true;
                    }
                }
            }
            freeFrom = LocalDateTime.of(freeDate, freeTime);
        }

        LocalTime earliestCheckIn = active.stream()
                .filter(b -> b.getCheckOutDate().equals(checkIn))
                .map(Booking::getCheckOutTime).max(LocalTime::compareTo).orElse(null);
        LocalTime latestCheckOut = active.stream()
                .filter(b -> b.getCheckInDate().equals(checkOut))
                .map(Booking::getCheckInTime).min(LocalTime::compareTo).orElse(null);

        return new Availability(room.getStatus() == RoomStatus.MAINTENANCE, conflicts, freeFrom,
                earliestCheckIn, latestCheckOut);
    }

    // Ngay doi khach: khong cho nhan phong truoc gio khach truoc tra / tra phong sau gio khach sau nhan
    private void checkTurnoverTimes(Availability availability, LocalDate checkInDate, LocalTime checkInTime,
                                    LocalDate checkOutDate, LocalTime checkOutTime) {
        if (availability.earliestCheckIn() != null && checkInTime.isBefore(availability.earliestCheckIn())) {
            throw BusinessException.of("err.turnover.checkIn", checkInDate.format(DAY_FMT),
                    availability.earliestCheckIn().format(TIME_FMT));
        }
        if (availability.latestCheckOut() != null && checkOutTime.isAfter(availability.latestCheckOut())) {
            throw BusinessException.of("err.turnover.checkOut", checkOutDate.format(DAY_FMT),
                    availability.latestCheckOut().format(TIME_FMT));
        }
    }

    // Chong dat trung khi nhieu nguoi thao tac cung luc: khoa dong phong den het giao dich.
    // Ket hop READ_COMMITTED o cac ham goi -> sau khi cho khoa, cau kiem tra trung lich doc duoc don nguoi truoc vua luu
    // (MySQL mac dinh REPEATABLE READ se doc "anh chup" cu va bo sot don do)
    private java.util.Optional<Room> lockRoom(Long roomId) {
        try {
            return roomRepository.findByIdForUpdate(roomId);
        } catch (PessimisticLockingFailureException | QueryTimeoutException ex) {
            throw BusinessException.of("err.roomBusy");
        }
    }

    // Chan xac nhan don neu phong da co don KHAC da xac nhan / dang o trung ngay (tranh trung phong that)
    private void ensureNoConfirmedConflict(Booking booking) {
        lockRoom(booking.getRoom().getId());
        checkAvailability(booking.getRoom(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId())
                .conflicts().stream()
                .filter(b -> b.getStatus() != BookingStatus.PENDING)
                .findFirst()
                .ifPresent(other -> {
                    throw new BusinessException("Không thể xác nhận: phòng " + booking.getRoom().getRoomNumber()
                            + " đã có đơn #" + other.getId() + " (" + other.getStatus().getVietnameseLabel() + ") từ "
                            + other.getCheckInDate().format(DAY_FMT) + " đến " + other.getCheckOutDate().format(DAY_FMT)
                            + ". Hãy liên hệ khách để đổi phòng/ngày hoặc hủy đơn này");
                });
    }

    // Dung cho nut "Kiem tra ma" (xem truoc) - luong tao booking van tu kiem tra lai trong createBooking
    public boolean hasUsedDiscountCode(Long customerId, String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        return discountCodeRepository.findByCodeAndActiveTrue(normalizedCode)
                .map(dc -> bookingRepository.existsByCustomerIdAndDiscountCodeIdAndStatusNot(
                        customerId, dc.getId(), BookingStatus.CANCELLED))
                .orElse(false);
    }

    // Khach dat online: don cho xac nhan, neu resort bat dat coc thi kem so tien + han chuyen coc
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                  LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                  Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                  String guestEmail) {
        Booking booking = buildBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime,
                guests, comboId, discountCodeStr, guestName, guestPhone, guestEmail);

        booking.setChannel(BookingChannel.ONLINE);
        BigDecimal deposit = pricingService.depositFor(booking.getTotalAmount());
        booking.setDepositAmount(deposit);
        if (deposit.signum() > 0) {
            booking.setDepositDeadline(depositDeadline(checkInDate, checkInTime));
        }

        Booking savedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(savedBooking);
        return savedBooking;
    }

    // Han chuyen coc = luc dat + so gio cai dat, nhung khong muon hon 6 tieng truoc gio nhan phong
    // (moc tu dong huy don chua xac nhan cua BookingExpirationScheduler)
    private LocalDateTime depositDeadline(LocalDate checkInDate, LocalTime checkInTime) {
        LocalDateTime deadline = LocalDateTime.now().plusHours(pricingService.getSettings().getDepositDeadlineHours());
        LocalDateTime latest = LocalDateTime.of(checkInDate, checkInTime).minusHours(EDIT_CUTOFF_HOURS);
        return deadline.isAfter(latest) ? latest : deadline;
    }

    // Kiem tra du lieu + tinh tien (theo tung dem) -> tra ve don PENDING CHUA luu
    private Booking buildBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                 LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                 Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                 String guestEmail) {

        if (guestName == null || guestName.isBlank()) {
            throw BusinessException.of("err.guestName");
        }
        if (guestPhone == null || guestPhone.isBlank()) {
            throw BusinessException.of("err.guestPhone");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw BusinessException.of("err.guestEmail");
        }
        if (!guestEmail.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw BusinessException.of("err.guestEmailInvalid");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw BusinessException.of("err.datesRequired");
        }
        if (checkInTime == null || checkOutTime == null) {
            throw BusinessException.of("err.timesRequired");
        }
        // BR-02
        if (!checkOutDate.isAfter(checkInDate)) {
            throw BusinessException.of("err.checkOutAfterCheckIn");
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            throw BusinessException.of("err.checkInPast");
        }

        Room room = lockRoom(roomId)
                .orElseThrow(() -> BusinessException.of("err.roomNotFound"));

        // BR-03
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            throw BusinessException.of("err.roomMaintenance");
        }

        // BR-01 - kiem tra lai lan cuoi ngay truoc khi luu, phong truong hop phong bi dat mat trong luc khach dang xem
        Availability availability = checkAvailability(room, checkInDate, checkOutDate);
        if (bookingRepository.existsOverlappingBooking(roomId, checkInDate, checkOutDate)) {
            throw BusinessException.rendered(t -> {
                String detail = availability.describe(t);
                return detail != null ? detail : t.t("err.roomJustTaken");
            });
        }
        checkTurnoverTimes(availability, checkInDate, checkInTime, checkOutDate, checkOutTime);

        // Tien phong cong tung dem: ngay thuong / cuoi tuan / ngay le co gia khac nhau
        BigDecimal roomAmount = pricingService.roomAmount(room.getPrice(), checkInDate, checkOutDate);

        Combo combo = null;
        BigDecimal comboAmount = BigDecimal.ZERO;
        if (comboId != null) {
            combo = comboRepository.findById(comboId)
                    .orElseThrow(() -> BusinessException.of("err.comboNotFound"));
            comboAmount = combo.getPrice();
        }

        BigDecimal subtotal = roomAmount.add(comboAmount);

        DiscountCode discountCode = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountCodeStr != null && !discountCodeStr.isBlank()) {
            discountCode = discountCodeRepository.findByCodeAndActiveTrue(discountCodeStr.trim().toUpperCase())
                    .orElseThrow(() -> BusinessException.of("err.discountInvalid"));

            if (discountCode.getApplicableCustomerType() != null
                    && discountCode.getApplicableCustomerType() != customer.getCustomerType()) {
                throw BusinessException.of("err.discountCustomerType");
            }

            if (bookingRepository.existsByCustomerIdAndDiscountCodeIdAndStatusNot(
                    customer.getId(), discountCode.getId(), BookingStatus.CANCELLED)) {
                throw BusinessException.of("err.discountUsed");
            }

            if (discountCode.getDiscountType() == DiscountType.PERCENTAGE) {
                discountAmount = subtotal.multiply(discountCode.getDiscountValue())
                        .divide(BigDecimal.valueOf(100));
            } else {
                discountAmount = discountCode.getDiscountValue().min(subtotal);
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // Mac dinh 1 khach neu khong nhap (cot number_of_guests khong cho phep null)
        int actualGuests = (guests != null && guests > 0) ? guests : 1;

        return Booking.builder()
                .customer(customer)
                .room(room)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .checkInTime(checkInTime)
                .checkOutTime(checkOutTime)
                .numberOfGuests(actualGuests)
                .guestName(guestName.trim())
                .guestPhone(guestPhone.trim())
                .guestEmail(guestEmail != null ? guestEmail.trim() : null)
                .combo(combo)
                .discountCode(discountCode)
                .discountAmount(discountAmount)
                .roomAmount(roomAmount)
                .totalAmount(totalAmount)
                .depositAmount(BigDecimal.ZERO)
                .status(BookingStatus.PENDING)
                .build();
    }

    public Booking findByIdForCustomer(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> BusinessException.of("err.bookingNotFound"));
        if (!booking.getCustomer().getId().equals(customerId)) {
            throw BusinessException.of("err.bookingForbidden");
        }
        return booking;
    }

    // Xem chi tiet don tu phia khach: hop le neu link co dung ma truy cap cua don, hoac nguoi xem
    // dang dang nhap va la chu don. Moi truong hop khac bao "khong tim thay" (khong lo don co ton tai hay khong)
    public Booking findForViewing(Long bookingId, String accessToken, Long viewerCustomerId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            if (accessToken != null && booking.getAccessToken() != null
                    && java.security.MessageDigest.isEqual(
                            accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            booking.getAccessToken().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return booking;
            }
            if (viewerCustomerId != null && booking.getCustomer().getId().equals(viewerCustomerId)) {
                return booking;
            }
        }
        throw BusinessException.of("err.bookingLinkInvalid");
    }

    public List<Booking> findAllForCustomer(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // Chi cho sua khi don chua check-in/checkout/huy, va con truoc >= 6 tieng so voi gio nhan phong
    public boolean canModify(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            return false;
        }
        LocalDateTime checkInDateTime = LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
        return LocalDateTime.now().isBefore(checkInDateTime.minusHours(EDIT_CUTOFF_HOURS));
    }

    @Transactional
    public Booking updateTimes(Long bookingId, Long customerId, LocalTime newCheckInTime, LocalTime newCheckOutTime) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (!canModify(booking)) {
            throw BusinessException.of("err.editTimesClosed", EDIT_CUTOFF_HOURS);
        }
        if (newCheckInTime == null || newCheckOutTime == null) {
            throw BusinessException.of("err.editTimesRequired");
        }
        checkTurnoverTimes(checkAvailability(booking.getRoom(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId()),
                booking.getCheckInDate(), newCheckInTime, booking.getCheckOutDate(), newCheckOutTime);
        booking.setCheckInTime(newCheckInTime);
        booking.setCheckOutTime(newCheckOutTime);
        booking.setCheckInTimeChanged(true);
        return bookingRepository.save(booking);
    }

    // Doi hoac huy combo cho booking da tao - tinh lai tien combo va ma giam gia (neu co) theo tong tien moi
    @Transactional
    public Booking updateCombo(Long bookingId, Long customerId, Long newComboId) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (!canModify(booking)) {
            throw BusinessException.of("err.editComboClosed", EDIT_CUTOFF_HOURS);
        }

        // Giu nguyen tien phong da chot luc dat (gia cuoi tuan/le co the da doi sau do)
        BigDecimal roomAmount = booking.getEffectiveRoomAmount();

        Combo newCombo = null;
        BigDecimal comboAmount = BigDecimal.ZERO;
        if (newComboId != null) {
            newCombo = comboRepository.findById(newComboId)
                    .orElseThrow(() -> BusinessException.of("err.comboMissing"));
            comboAmount = newCombo.getPrice();
        }

        BigDecimal subtotal = roomAmount.add(comboAmount);

        BigDecimal discountAmount = discountFor(booking.getDiscountCode(), subtotal);
        BigDecimal totalAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        booking.setCombo(newCombo);
        booking.setDiscountAmount(discountAmount);
        booking.setTotalAmount(totalAmount);
        // Chua chuyen coc thi tinh lai tien coc theo tong tien moi; da coc roi thi giu nguyen
        if (booking.isDepositRequired() && !booking.isDepositPaid()) {
            booking.setDepositAmount(pricingService.depositFor(totalAmount));
        }

        return bookingRepository.save(booking);
    }

    // Tien giam theo ma da ap dung luc dat (tinh lai khi tong tien thay doi: doi combo / doi ngay)
    private static BigDecimal discountFor(DiscountCode dc, BigDecimal subtotal) {
        if (dc == null) {
            return BigDecimal.ZERO;
        }
        if (dc.getDiscountType() == DiscountType.PERCENTAGE) {
            return subtotal.multiply(dc.getDiscountValue()).divide(BigDecimal.valueOf(100));
        }
        return dc.getDiscountValue().min(subtotal);
    }

    // ===== Khach tu doi ngay nhan / tra phong (giu nguyen phong, gio, combo, ma giam gia) =====

    // Tien moi khi doi sang [checkIn, checkOut): gia phong tinh lai theo bang gia hien tai (cuoi tuan / le),
    // difference = tong moi - tong cu (> 0: tra them khi tra phong, < 0: duoc giam)
    public record DateChangeQuote(LocalDate checkIn, LocalDate checkOut, long nights, BigDecimal roomAmount,
                                  BigDecimal discountAmount, BigDecimal totalAmount, BigDecimal difference,
                                  BigDecimal depositAmount, boolean depositPaid) {
    }

    // Xem truoc (khong luu) - nem BusinessException neu khong doi duoc
    public DateChangeQuote quoteDateChange(Long bookingId, Long customerId, LocalDate newCheckIn, LocalDate newCheckOut) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        return validateAndQuoteDateChange(booking, newCheckIn, newCheckOut);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking changeDates(Long bookingId, Long customerId, LocalDate newCheckIn, LocalDate newCheckOut) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        // Khoa phong truoc khi kiem tra trung lich (giong luc dat moi) - tranh 2 nguoi cung chiem 1 khoang ngay
        lockRoom(booking.getRoom().getId());
        DateChangeQuote quote = validateAndQuoteDateChange(booking, newCheckIn, newCheckOut);

        booking.setCheckInDate(newCheckIn);
        booking.setCheckOutDate(newCheckOut);
        booking.setRoomAmount(quote.roomAmount());
        booking.setDiscountAmount(quote.discountAmount());
        booking.setTotalAmount(quote.totalAmount());
        if (booking.isDepositRequired() && !booking.isDepositPaid()) {
            booking.setDepositAmount(quote.depositAmount());
            // Han coc khong duoc muon hon moc tu huy don chua xac nhan cua ngay nhan phong moi
            LocalDateTime latest = LocalDateTime.of(newCheckIn, booking.getCheckInTime()).minusHours(EDIT_CUTOFF_HOURS);
            if (booking.getDepositDeadline() == null || booking.getDepositDeadline().isAfter(latest)) {
                booking.setDepositDeadline(latest);
            }
        }
        booking.setDatesChanged(true);
        // Ngay moi -> email nhac lich gui lai theo ngay moi
        booking.setReminderSentAt(null);
        Booking saved = bookingRepository.save(booking);
        emailService.sendDatesChanged(saved);
        return saved;
    }

    private DateChangeQuote validateAndQuoteDateChange(Booking booking, LocalDate newCheckIn, LocalDate newCheckOut) {
        if (!canModify(booking)) {
            throw BusinessException.of("err.changeDatesClosed", EDIT_CUTOFF_HOURS);
        }
        if (newCheckIn == null || newCheckOut == null) {
            throw BusinessException.of("err.datesRequired");
        }
        if (!newCheckOut.isAfter(newCheckIn)) {
            throw BusinessException.of("err.checkOutAfterCheckIn");
        }
        if (newCheckIn.equals(booking.getCheckInDate()) && newCheckOut.equals(booking.getCheckOutDate())) {
            throw BusinessException.of("err.changeDatesSame");
        }
        // Ngay nhan moi cung phai con >= 6 tieng nhu luc sua don (nhan vien kip chuan bi phong)
        if (!LocalDateTime.now().isBefore(LocalDateTime.of(newCheckIn, booking.getCheckInTime()).minusHours(EDIT_CUTOFF_HOURS))) {
            throw BusinessException.of("err.changeDatesTooSoon", EDIT_CUTOFF_HOURS);
        }
        Room room = booking.getRoom();
        if (room.getStatus() == RoomStatus.MAINTENANCE) {
            throw BusinessException.of("err.roomMaintenance");
        }
        Availability availability = checkAvailability(room, newCheckIn, newCheckOut, booking.getId());
        if (!availability.conflicts().isEmpty()) {
            throw BusinessException.rendered(availability::describe);
        }
        checkTurnoverTimes(availability, newCheckIn, booking.getCheckInTime(), newCheckOut, booking.getCheckOutTime());
        ensureServicesFitNewDates(booking, newCheckIn, newCheckOut);

        BigDecimal roomAmount = pricingService.roomAmount(room.getPrice(), newCheckIn, newCheckOut);
        BigDecimal subtotal = roomAmount.add(booking.getCombo() != null ? booking.getCombo().getPrice() : BigDecimal.ZERO);
        BigDecimal discountAmount = discountFor(booking.getDiscountCode(), subtotal);
        BigDecimal totalAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal deposit = booking.isDepositRequired() && !booking.isDepositPaid()
                ? pricingService.depositFor(totalAmount) : booking.getDepositPaidAmount();
        return new DateChangeQuote(newCheckIn, newCheckOut, ChronoUnit.DAYS.between(newCheckIn, newCheckOut),
                roomAmount, discountAmount, totalAmount, totalAmount.subtract(booking.getTotalAmount()),
                deposit, booking.isDepositPaid());
    }

    // Tour lan / xe dua don da dat (chua huy) phai con nam trong ngay luu tru moi - khong thi khach huy dich vu truoc
    private void ensureServicesFitNewDates(Booking booking, LocalDate newCheckIn, LocalDate newCheckOut) {
        for (TourBooking tour : tourBookingRepository.findByBookingIdOrderByTourDateAscIdAsc(booking.getId())) {
            if (tour.getStatus() != ServiceStatus.CANCELLED
                    && (tour.getTourDate().isBefore(newCheckIn) || tour.getTourDate().isAfter(newCheckOut))) {
                throw BusinessException.of("err.changeDatesTour", tour.getTour().getLocalizedName(),
                        tour.getTourDate().format(DAY_FMT));
            }
        }
        for (AirportTransfer transfer : transferRepository.findByBookingIdOrderByPickupAtAsc(booking.getId())) {
            if (transfer.getStatus() == ServiceStatus.CANCELLED) {
                continue;
            }
            LocalDate expected = transfer.getDirection() == TransferDirection.PICKUP ? newCheckIn : newCheckOut;
            if (!transfer.getPickupAt().toLocalDate().equals(expected)) {
                throw BusinessException.of("err.changeDatesTransfer", transfer.getPickupAt().format(DAY_FMT));
            }
        }
    }

    // Theo dung state flow: chi PENDING hoac CONFIRMED moi duoc chuyen sang CANCELLED
    @Transactional
    public Booking cancelBooking(Long bookingId, Long customerId) {
        Booking booking = findByIdForCustomer(bookingId, customerId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw BusinessException.of("err.cancelState", booking.getStatus().name());
        }
        applyCancellation(booking, false);
        Booking cancelledBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(cancelledBooking);
        return cancelledBooking;
    }

    // Chuyen don sang CANCELLED va chot tien hoan coc theo chinh sach (so gio con lai truoc gio nhan phong).
    // fullRefund = true: hoan 100% bat ke thoi diem (VD resort huy do su co phia khach san)
    private void applyCancellation(Booking booking, boolean fullRefund) {
        LocalDateTime now = LocalDateTime.now();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        if (booking.isDepositRequired() && booking.isDepositPaid()) {
            int percent = fullRefund ? 100 : pricingService.refundPercent(checkInDateTime(booking), now);
            booking.setRefundPercent(percent);
            booking.setRefundAmount(PricingService.refundAmount(booking.getDepositAmount(), percent));
        }
    }

    private static LocalDateTime checkInDateTime(Booking booking) {
        return LocalDateTime.of(booking.getCheckInDate(), booking.getCheckInTime());
    }

    // Uoc tinh tien hoan neu huy ngay bay gio - hien cho khach/nhan vien truoc khi bam huy.
    // null = don khong the huy hoac chua coc (khong co gi de hoan)
    public PricingService.RefundQuote refundQuoteIfCancelledNow(Booking booking) {
        if ((booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED)
                || !booking.isDepositRequired() || !booking.isDepositPaid()) {
            return null;
        }
        int percent = pricingService.refundPercent(checkInDateTime(booking), LocalDateTime.now());
        return new PricingService.RefundQuote(percent, PricingService.refundAmount(booking.getDepositAmount(), percent));
    }

    // ===== Cac thao tac danh cho STAFF/ADMIN =====

    public List<Booking> findAllBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    public Booking findById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn đặt phòng"));
    }

    // Staff mo xem chi tiet don -> tat cac canh bao (don moi / doi gio nhan phong) cua don do
    @Transactional
    public Booking findByIdAndMarkSeenByStaff(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.isNewBooking() || booking.isCheckInTimeChanged() || booking.isDatesChanged()) {
            booking.setNewBooking(false);
            booking.setCheckInTimeChanged(false);
            booking.setDatesChanged(false);
            bookingRepository.save(booking);
        }
        return booking;
    }

    // So don moi (khach vua dat, staff chua mo xem) - dung de hien cham do o menu admin
    public long countNewBookings() {
        return bookingRepository.countByNewBookingTrue();
    }

    // Nhan vien xac nhan booking: PENDING -> CONFIRMED
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking confirmBooking(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ có thể xác nhận đơn đang ở trạng thái Chờ xác nhận");
        }
        ensureNoConfirmedConflict(booking);
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(confirmedBooking);
        return confirmedBooking;
    }

    // Nhan vien thay tien coc da vao tai khoan -> ghi nhan da coc va xac nhan don luon
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking confirmDeposit(Long bookingId) {
        Booking booking = findById(bookingId);
        if (!booking.isDepositRequired()) {
            throw new BusinessException("Đơn này không yêu cầu đặt cọc");
        }
        if (booking.isDepositPaid()) {
            throw new BusinessException("Đơn này đã được ghi nhận đặt cọc trước đó");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessException("Chỉ ghi nhận đặt cọc cho đơn đang ở trạng thái Chờ xác nhận");
        }
        ensureNoConfirmedConflict(booking);
        booking.setDepositPaidAt(LocalDateTime.now());
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(confirmedBooking);
        return confirmedBooking;
    }

    // BR-04: chi booking da CONFIRMED moi duoc check-in
    // Khai bao luu tru: phai nhap giay to it nhat 1 khach truoc khi cho nhan phong
    @Transactional
    public Booking checkIn(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Chỉ có thể check-in đơn đã được xác nhận (CONFIRMED)");
        }
        if (guestRepository.countByBookingId(bookingId) == 0) {
            throw new BusinessException("Vui lòng khai báo lưu trú (nhập giấy tờ ít nhất 1 khách) trước khi check-in");
        }
        booking.setStatus(BookingStatus.CHECKED_IN);

        Room room = booking.getRoom();
        room.setStatus(RoomStatus.OCCUPIED);
        roomRepository.save(room);

        Booking checkedInBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(checkedInBooking);
        return checkedInBooking;
    }

    // BR-05: chi booking da CHECKED_IN moi duoc check-out
    // Sau khi check-out, phong chuyen sang NOT_READY - can nhan vien/admin xac nhan da don dep xong
    @Transactional
    public Booking checkOut(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessException("Chỉ có thể check-out đơn đã check-in (CHECKED_IN)");
        }
        booking.setStatus(BookingStatus.CHECKED_OUT);

        Room room = booking.getRoom();
        room.setStatus(RoomStatus.NOT_READY);
        roomRepository.save(room);

        Booking checkedOutBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(checkedOutBooking);
        return checkedOutBooking;
    }

    // Dat phong truc tiep tai quay cho khach vang lai (khong can dang ky tai khoan truoc),
    // nhan vien xac nhan luon (CONFIRMED) vi da lam viec truc tiep voi khach
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createWalkInBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                        LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                        Long comboId, String guestName, String guestPhone, String guestEmail) {

        Booking booking = buildBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime,
                guests, comboId, null, guestName, guestPhone, guestEmail);
        // Khach o ngay tai quay -> khong can dat coc, xac nhan luon
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setChannel(BookingChannel.WALK_IN);
        // Nhan vien tu tay tao don nay nen khong can bao "don moi"
        booking.setNewBooking(false);
        Booking savedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(savedBooking);
        return savedBooking;
    }

    // Nhan vien/admin huy don thay khach (VD: khach goi dien nho huy) - khong kiem tra chu don nhu ben customer
    @Transactional
    public Booking cancelBookingByStaff(Long bookingId) {
        return cancelBookingByStaff(bookingId, false);
    }

    // fullRefund = true: resort huy (het phong, su co...) -> hoan 100% coc, khong ap chinh sach
    @Transactional
    public Booking cancelBookingByStaff(Long bookingId, boolean fullRefund) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Không thể hủy đơn ở trạng thái hiện tại (" + booking.getStatus().getVietnameseLabel() + ")");
        }
        applyCancellation(booking, fullRefund);
        Booking cancelledBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(cancelledBooking);
        return cancelledBooking;
    }

    // Nhan vien da chuyen tra tien hoan coc cho khach -> ghi nhan (kem ghi chu ma giao dich neu co)
    @Transactional
    public Booking confirmRefund(Long bookingId, String note) {
        Booking booking = findById(bookingId);
        if (!booking.isRefundPending()) {
            throw new BusinessException("Đơn này không có khoản hoàn cọc nào đang chờ xử lý");
        }
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() > 255) {
            throw new BusinessException("Ghi chú hoàn tiền tối đa 255 ký tự");
        }
        booking.setRefundedAt(LocalDateTime.now());
        booking.setRefundNote(trimmed.isEmpty() ? null : trimmed);
        Booking refundedBooking = bookingRepository.save(booking);
        emailService.sendRefundCompleted(refundedBooking);
        return refundedBooking;
    }

    // So don da huy dang cho hoan coc - hien so o sidebar va dashboard
    public long countPendingRefunds() {
        return bookingRepository.countPendingRefunds(BookingStatus.CANCELLED);
    }

    // Tien coc resort giu lai (khach huy muon, mat coc) cua cac don huy trong [from, to] - theo ngay huy
    public BigDecimal getForfeitedDeposits(LocalDate from, LocalDate to) {
        return bookingRepository.findByStatusAndCancelledAtBetween(BookingStatus.CANCELLED,
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)).stream()
                .map(Booking::getForfeitedDepositAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Loc danh sach booking theo trang thai, dung cho man hinh quan ly cua STAFF/ADMIN
    public List<Booking> findAllBookings(BookingStatus statusFilter) {
        return findAllBookings(statusFilter, null);
    }

    // Loc theo trang thai va/hoac tim theo ten, email, sdt khach hang (khong phan biet hoa thuong)
    public List<Booking> findAllBookings(BookingStatus statusFilter, String search) {
        List<Booking> all = bookingRepository.findAllByOrderByCreatedAtDesc();
        if (statusFilter != null) {
            all = all.stream().filter(b -> b.getStatus() == statusFilter).toList();
        }
        if (search != null && !search.isBlank()) {
            String keyword = search.trim().toLowerCase();
            all = all.stream().filter(b -> matchesSearch(b, keyword)).toList();
        }
        return all;
    }

    // Chi khop theo gia tri THUC SU hien thi tren bang (uu tien thong tin nguoi nhan phong,
    // fallback ve thong tin tai khoan neu don khong co) - tranh khop nham khi 1 khach hang dat nhieu don
    // voi thong tin nguoi nhan phong khac voi so dien thoai/email dang ky tai khoan
    private boolean matchesSearch(Booking b, String keyword) {
        String name = b.getGuestName() != null ? b.getGuestName() : b.getCustomer().getFullName();
        String phone = b.getGuestPhone() != null ? b.getGuestPhone() : b.getCustomer().getPhoneNumber();
        String email = b.getGuestEmail() != null ? b.getGuestEmail() : b.getCustomer().getEmail();
        return containsIgnoreCase(name, keyword)
                || containsIgnoreCase(phone, keyword)
                || containsIgnoreCase(email, keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    // ===== Thong ke cho bieu do dashboard admin - loc theo khoang ngay tao don (createdAt) =====

    // Bieu do tron: so luong don theo tung trang thai, trong khoang [fromDate, toDate]
    public Map<BookingStatus, Long> getBookingStatusCounts(LocalDate fromDate, LocalDate toDate) {
        Map<BookingStatus, Long> counts = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status, 0L);
        }
        for (Booking booking : findAllBookings()) {
            if (isWithinRange(booking, fromDate, toDate)) {
                counts.merge(booking.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }

    // Bieu do cot: doanh thu theo tung loai phong trong khoang [fromDate, toDate] (bo qua don da huy)
    // Doanh thu 1 don = tien don (phong + combo - giam gia) + phu phi phat sinh khi o
    public Map<String, BigDecimal> getRevenueByRoomType(LocalDate fromDate, LocalDate toDate) {
        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        Map<Long, BigDecimal> charges = chargeTotalsByBooking();
        for (Booking booking : findAllBookings()) {
            if (booking.getStatus() == BookingStatus.CANCELLED || !isWithinRange(booking, fromDate, toDate)) {
                continue;
            }
            String typeName = booking.getRoom().getRoomType().getName();
            revenue.merge(typeName, revenueOf(booking, charges), BigDecimal::add);
        }
        return revenue;
    }

    // Bieu do duong: doanh thu theo tung ngay trong khoang [fromDate, toDate] neu <= 31 ngay,
    // neu khoang dai hon thi gom theo thang de bieu do khong bi qua nhieu diem (bo qua don da huy)
    public Map<String, BigDecimal> getRevenueTrend(LocalDate fromDate, LocalDate toDate) {
        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        long totalDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        boolean groupByDay = totalDays <= 31;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy");

        if (groupByDay) {
            for (LocalDate d = fromDate; !d.isAfter(toDate); d = d.plusDays(1)) {
                revenue.put(d.format(dayFormatter), BigDecimal.ZERO);
            }
        } else {
            YearMonth startMonth = YearMonth.from(fromDate);
            YearMonth endMonth = YearMonth.from(toDate);
            for (YearMonth m = startMonth; !m.isAfter(endMonth); m = m.plusMonths(1)) {
                revenue.put(m.format(monthFormatter), BigDecimal.ZERO);
            }
        }

        Map<Long, BigDecimal> charges = chargeTotalsByBooking();
        for (Booking booking : findAllBookings()) {
            if (booking.getStatus() == BookingStatus.CANCELLED || !isWithinRange(booking, fromDate, toDate)) {
                continue;
            }
            LocalDate createdDate = booking.getCreatedAt().toLocalDate();
            String label = groupByDay ? createdDate.format(dayFormatter) : YearMonth.from(createdDate).format(monthFormatter);
            revenue.computeIfPresent(label, (key, existing) -> existing.add(revenueOf(booking, charges)));
        }
        return revenue;
    }

    // ===== Chi so khach san - tinh theo ngay khach O (dem luu tru), khong theo ngay tao don =====

    // Don duoc tinh la da ban phong: da xac nhan / dang o / da tra phong (bo qua don cho duyet va don huy)
    private static final Set<BookingStatus> ROOM_SOLD_STATUSES =
            EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);

    // occupancyRate: % dem-phong da ban / tong dem-phong co the ban (so phong x so ngay)
    // adr: gia trung binh moi dem-phong da ban; revPar: doanh thu tren moi dem-phong co the ban
    public record HotelKpi(double occupancyRate, BigDecimal adr, BigDecimal revPar,
                           long soldRoomNights, long availableRoomNights) {
    }

    // Tien don (phong + combo - giam gia, khong gom phu phi) chia deu cho tung dem cua don,
    // chi cong cac dem nam trong khoang [fromDate, toDate]
    public HotelKpi getHotelKpi(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        long availableRoomNights = roomRepository.count() * days;
        LocalDate rangeEnd = toDate.plusDays(1);

        long soldRoomNights = 0;
        BigDecimal roomRevenue = BigDecimal.ZERO;
        for (Booking booking : findAllBookings()) {
            if (!ROOM_SOLD_STATUSES.contains(booking.getStatus())) {
                continue;
            }
            long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
            LocalDate start = booking.getCheckInDate().isAfter(fromDate) ? booking.getCheckInDate() : fromDate;
            LocalDate end = booking.getCheckOutDate().isBefore(rangeEnd) ? booking.getCheckOutDate() : rangeEnd;
            long nightsInRange = ChronoUnit.DAYS.between(start, end);
            if (nights <= 0 || nightsInRange <= 0) {
                continue;
            }
            soldRoomNights += nightsInRange;
            roomRevenue = roomRevenue.add(booking.getTotalAmount().multiply(BigDecimal.valueOf(nightsInRange))
                    .divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP));
        }

        double occupancyRate = availableRoomNights == 0 ? 0
                : Math.round(soldRoomNights * 1000.0 / availableRoomNights) / 10.0;
        BigDecimal adr = soldRoomNights == 0 ? BigDecimal.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(soldRoomNights), 0, RoundingMode.HALF_UP);
        BigDecimal revPar = availableRoomNights == 0 ? BigDecimal.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(availableRoomNights), 0, RoundingMode.HALF_UP);
        return new HotelKpi(occupancyRate, adr, revPar, soldRoomNights, availableRoomNights);
    }

    // Don tao trong khoang [fromDate, toDate] - cung co so voi cac bieu do, dung cho file Excel
    public List<Booking> findBookingsCreatedBetween(LocalDate fromDate, LocalDate toDate) {
        return findAllBookings().stream().filter(b -> isWithinRange(b, fromDate, toDate)).toList();
    }

    private BigDecimal revenueOf(Booking booking, Map<Long, BigDecimal> charges) {
        return booking.getTotalAmount().add(charges.getOrDefault(booking.getId(), BigDecimal.ZERO));
    }

    // Tong phu phi theo ma don (don khong co phu phi thi khong co trong map)
    public Map<Long, BigDecimal> chargeTotalsByBooking() {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Object[] row : chargeRepository.sumAmountGroupByBooking()) {
            totals.put((Long) row[0], (BigDecimal) row[1]);
        }
        return totals;
    }

    private boolean isWithinRange(Booking booking, LocalDate fromDate, LocalDate toDate) {
        LocalDate createdDate = booking.getCreatedAt().toLocalDate();
        return !createdDate.isBefore(fromDate) && !createdDate.isAfter(toDate);
    }
}
