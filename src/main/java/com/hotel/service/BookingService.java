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

    private final LoyaltyService loyaltyService;
    private final PromotionService promotionService;
    private com.hotel.repository.BookingGroupRepository bookingGroupRepository;
    private GiftVoucherService giftVoucherService;

    @org.springframework.beans.factory.annotation.Autowired
    void setGiftVoucherService(@org.springframework.context.annotation.Lazy GiftVoucherService giftVoucherService) {
        this.giftVoucherService = giftVoucherService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setBookingGroupRepository(com.hotel.repository.BookingGroupRepository bookingGroupRepository) {
        this.bookingGroupRepository = bookingGroupRepository;
    }

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository,
                           ComboRepository comboRepository, DiscountCodeRepository discountCodeRepository,
                           EmailService emailService, BookingChargeRepository chargeRepository,
                           PricingService pricingService, BookingGuestRepository guestRepository,
                           TourBookingRepository tourBookingRepository, AirportTransferRepository transferRepository,
                           LoyaltyService loyaltyService, PromotionService promotionService) {
        this.loyaltyService = loyaltyService;
        this.promotionService = promotionService;
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
        return createBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime, guests,
                comboId, discountCodeStr, guestName, guestPhone, guestEmail, 0);
    }

    // pointsToUse > 0: khach (da dang nhap) dung diem thuong tru vao tien don, tien coc tinh tren so con lai
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                  LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                  Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                  String guestEmail, int pointsToUse) {
        return placeOnline(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime, guests, comboId,
                discountCodeStr, guestName, guestPhone, guestEmail, pointsToUse, null, null, false);
    }

    // giftCode: ma the qua tang tru vao tien don (sau khuyen mai + diem thuong), tien coc tinh tren so con lai
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                  LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                  Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                  String guestEmail, int pointsToUse, String giftCode) {
        return placeOnline(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime, guests, comboId,
                discountCodeStr, guestName, guestPhone, guestEmail, pointsToUse, giftCode, null, false);
    }

    // member = khach dang nhap (khong phai tai khoan tu tao cho khach vang lai) -> duoc gia thanh vien theo hang
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                  LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                  Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                  String guestEmail, int pointsToUse, String giftCode, boolean member) {
        return placeOnline(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime, guests, comboId,
                discountCodeStr, guestName, guestPhone, guestEmail, pointsToUse, giftCode, null, member);
    }

    // group != null: 1 phong trong nhom dat nhieu phong (khong gui email tung don - gui 1 email tong hop)
    private Booking placeOnline(User customer, Long roomId, LocalDate checkInDate, LocalDate checkOutDate,
                                LocalTime checkInTime, LocalTime checkOutTime, Integer guests,
                                Long comboId, String discountCodeStr, String guestName, String guestPhone,
                                String guestEmail, int pointsToUse, String giftCode, BookingGroup group, boolean member) {
        requireStandardTimes(checkInTime, checkOutTime);
        Booking booking = buildBooking(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime,
                guests, comboId, discountCodeStr, guestName, guestPhone, guestEmail);
        // Khuyen mai tu dong (chi dat online): giam % tien phong theo luat co loi nhat
        applyPromotion(booking, promotionService.best(booking.getRoomAmount(), checkInDate, checkOutDate, LocalDate.now())
                .orElse(null));
        booking.setTotalAmount(booking.getTotalAmount().subtract(booking.getPromotionAmountOrZero()).max(BigDecimal.ZERO));
        // Gia thanh vien: giam them % tien phong (sau khuyen mai) theo hang khach
        int memberPercent = member ? pricingService.memberDiscountPercent(customer) : 0;
        if (memberPercent > 0) {
            BigDecimal memberAmount = PricingService.memberDiscountAmount(
                    booking.getRoomAmount().subtract(booking.getPromotionAmountOrZero()), memberPercent);
            booking.setMemberDiscountPercent(memberPercent);
            booking.setMemberDiscountAmount(memberAmount);
            booking.setTotalAmount(booking.getTotalAmount().subtract(memberAmount).max(BigDecimal.ZERO));
        }
        if (pointsToUse > 0) {
            BigDecimal pointsDiscount = loyaltyService.redeemDiscount(customer, pointsToUse, booking.getTotalAmount());
            booking.setPointsUsed(pointsToUse);
            booking.setPointsDiscount(pointsDiscount);
            booking.setTotalAmount(booking.getTotalAmount().subtract(pointsDiscount).max(BigDecimal.ZERO));
        }
        if (giftCode != null && !giftCode.isBlank()) {
            BigDecimal voucherAmount = giftVoucherService.quote(giftCode, booking.getTotalAmount());
            booking.setGiftVoucherCode(giftCode.trim().toUpperCase());
            booking.setGiftVoucherAmount(voucherAmount);
            booking.setTotalAmount(booking.getTotalAmount().subtract(voucherAmount).max(BigDecimal.ZERO));
        }

        booking.setChannel(BookingChannel.ONLINE);
        BigDecimal deposit = pricingService.depositFor(booking.getTotalAmount());
        booking.setDepositAmount(deposit);
        if (deposit.signum() > 0) {
            booking.setDepositDeadline(depositDeadline(checkInDate, checkInTime));
        }

        booking.setGroup(group);
        Booking savedBooking = bookingRepository.save(booking);
        loyaltyService.recordRedeem(savedBooking);
        if (savedBooking.getGiftVoucherCode() != null) {
            giftVoucherService.apply(savedBooking);
        }
        if (group == null) {
            emailService.sendBookingConfirmation(savedBooking);
        }
        return savedBooking;
    }

    // ===== Dat nhieu phong 1 lan =====

    public static final int MIN_GROUP_ROOMS = 2;
    public static final int MAX_GROUP_ROOMS = 5;

    // Tao tat ca don trong 1 giao dich: 1 phong vua bi dat mat -> khong tao don nao. guestsByRoom: so khach tung phong
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingGroup createGroupBooking(User customer, List<Long> roomIds, LocalDate checkInDate, LocalDate checkOutDate,
                                           LocalTime checkInTime, LocalTime checkOutTime,
                                           java.util.Map<Long, Integer> guestsByRoom,
                                           String guestName, String guestPhone, String guestEmail) {
        List<Long> distinct = roomIds == null ? List.of() : roomIds.stream().distinct().toList();
        if (distinct.size() < MIN_GROUP_ROOMS || distinct.size() > MAX_GROUP_ROOMS) {
            throw BusinessException.of("group.error.count", MIN_GROUP_ROOMS, MAX_GROUP_ROOMS);
        }
        BookingGroup group = bookingGroupRepository.save(BookingGroup.builder().customer(customer)
                .guestName(guestName == null ? "" : guestName.trim())
                .guestEmail(guestEmail == null ? null : guestEmail.trim())
                .guestPhone(guestPhone == null ? null : guestPhone.trim()).build());
        List<Booking> bookings = new java.util.ArrayList<>();
        for (Long roomId : distinct) {
            Integer guests = guestsByRoom != null ? guestsByRoom.get(roomId) : null;
            Room room = roomRepository.findById(roomId).orElseThrow(() -> BusinessException.of("err.roomNotFound"));
            if (guests != null && room.getRoomType().getMaxGuests() != null && guests > room.getRoomType().getMaxGuests()) {
                throw BusinessException.of("group.error.guests", room.getRoomNumber(), room.getRoomType().getMaxGuests());
            }
            bookings.add(placeOnline(customer, roomId, checkInDate, checkOutDate, checkInTime, checkOutTime, guests,
                    null, null, guestName, guestPhone, guestEmail, 0, null, group, false));
        }
        emailService.sendGroupBooking(group, bookings, false);
        return group;
    }

    public List<Booking> groupBookings(Long groupId) {
        return bookingRepository.findByGroupId(groupId);
    }

    // Giong findForViewing: dung ma truy cap cua nhom, hoac chu nhom dang dang nhap
    public BookingGroup findGroupForViewing(Long groupId, String accessToken, Long viewerCustomerId) {
        BookingGroup group = bookingGroupRepository.findById(groupId).orElse(null);
        if (group != null) {
            if (accessToken != null && java.security.MessageDigest.isEqual(
                    accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    group.getAccessToken().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return group;
            }
            if (viewerCustomerId != null && group.getCustomer().getId().equals(viewerCustomerId)) {
                return group;
            }
        }
        throw BusinessException.of("err.bookingLinkInvalid");
    }

    public BookingGroup findGroup(Long groupId) {
        return bookingGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException("Không tìm thấy đoàn"));
    }

    // Khach huy ca nhom: chi cac don con huy duoc (cho / da xac nhan), het han sua thi bao loi
    @Transactional
    public int cancelGroupByCustomer(BookingGroup group) {
        List<Booking> cancellable = groupBookings(group.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
                .toList();
        if (cancellable.isEmpty()) {
            throw BusinessException.of("group.error.nothingToCancel");
        }
        for (Booking b : cancellable) {
            applyCancellation(b, false);
            bookingRepository.save(b);
        }
        emailService.sendGroupBooking(group, groupBookings(group.getId()), true);
        return cancellable.size();
    }

    // Le tan nhan coc gop cua ca nhom -> ghi nhan coc + xac nhan tung don con cho coc
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int confirmGroupDeposit(Long groupId) {
        int count = 0;
        for (Booking b : groupBookings(groupId)) {
            if (b.isAwaitingDeposit()) {
                confirmDeposit(b.getId());
                count++;
            }
        }
        if (count == 0) {
            throw new BusinessException("Không có phòng nào trong đoàn đang chờ cọc");
        }
        return count;
    }

    @Transactional
    public int cancelGroupByStaff(Long groupId, boolean fullRefund) {
        int count = 0;
        for (Booking b : groupBookings(groupId)) {
            if (b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED) {
                cancelBookingByStaff(b.getId(), fullRefund);
                count++;
            }
        }
        if (count == 0) {
            throw new BusinessException("Không có phòng nào trong đoàn còn hủy được");
        }
        return count;
    }

    private static BigDecimal memberDiscountFor(Booking booking, BigDecimal roomAmount, BigDecimal promotionAmount) {
        return booking.getMemberDiscountPercent() == null ? BigDecimal.ZERO
                : PricingService.memberDiscountAmount(roomAmount.subtract(promotionAmount), booking.getMemberDiscountPercent());
    }

    private static void applyPromotion(Booking booking, PromotionService.Applied promo) {
        booking.setPromotionName(promo != null ? promo.name() : null);
        booking.setPromotionNameEn(promo != null ? promo.nameEn() : null);
        booking.setPromotionPercent(promo != null ? promo.percent() : null);
        booking.setPromotionAmount(promo != null ? promo.amount() : null);
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

    // So lan luu tru hoan tat (da tra phong) - de tinh hang khach than thiet
    public long countCompletedStays(Long customerId) {
        return bookingRepository.countByCustomerIdAndStatus(customerId, BookingStatus.CHECKED_OUT);
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
        // Gio da duoc le tan duyet (nhan som / tra muon) thi giu nguyen duoc; gio moi phai trong khung tieu chuan
        requireStandardTimes(newCheckInTime.equals(booking.getCheckInTime()) ? StayTimePolicy.STANDARD_CHECK_IN : newCheckInTime,
                newCheckOutTime.equals(booking.getCheckOutTime()) ? StayTimePolicy.STANDARD_CHECK_OUT : newCheckOutTime);
        checkTurnoverTimes(checkAvailability(booking.getRoom(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId()),
                booking.getCheckInDate(), newCheckInTime, booking.getCheckOutDate(), newCheckOutTime);
        booking.setCheckInTime(newCheckInTime);
        booking.setCheckOutTime(newCheckOutTime);
        booking.setCheckInTimeChanged(true);
        return bookingRepository.save(booking);
    }

    private static void requireStandardTimes(LocalTime checkInTime, LocalTime checkOutTime) {
        if (StayTimePolicy.isEarlyCheckIn(checkInTime) || StayTimePolicy.isLateCheckOut(checkOutTime)) {
            throw BusinessException.of("err.timeOutsideWindow",
                    StayTimePolicy.STANDARD_CHECK_IN.format(TIME_FMT), StayTimePolicy.STANDARD_CHECK_OUT.format(TIME_FMT));
        }
    }

    // ===== Le tan doi phong cho khach =====

    // 1 phong co the chuyen sang: tien phong moi cua don va chenh lech so voi hien tai (> 0 = khach tra them)
    public record RoomMoveOption(Room room, BigDecimal newRoomAmount, BigDecimal difference) {
    }

    public boolean canMoveRoom(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.CHECKED_IN;
    }

    // Khach dang o: phong moi tinh tu hom nay (cac dem da o giu nguyen tien phong cu)
    private static LocalDate moveFrom(Booking booking) {
        LocalDate today = LocalDate.now();
        return booking.getStatus() == BookingStatus.CHECKED_IN && booking.getCheckInDate().isBefore(today)
                ? today : booking.getCheckInDate();
    }

    // Tien phong cua don neu chuyen sang phong moi: dem da o (khach dang o) tinh theo tien cu, dem con lai theo bang gia phong moi
    private BigDecimal roomAmountAfterMove(Booking booking, Room newRoom) {
        LocalDate from = moveFrom(booking);
        BigDecimal remaining = pricingService.roomAmount(newRoom.getPrice(), from, booking.getCheckOutDate());
        long nights = booking.getNights();
        long stayed = ChronoUnit.DAYS.between(booking.getCheckInDate(), from);
        if (stayed <= 0 || nights <= 0) {
            return remaining;
        }
        BigDecimal stayedAmount = booking.getEffectiveRoomAmount().multiply(BigDecimal.valueOf(stayed))
                .divide(BigDecimal.valueOf(nights), 0, RoundingMode.HALF_UP);
        return stayedAmount.add(remaining);
    }

    // Phong con trong dung cac dem con lai, du cho, khong bao tri; khach dang o thi phong moi phai dang sach.
    // Cung hang phong len dau, roi chenh lech it nhat
    @Transactional(readOnly = true)
    public List<RoomMoveOption> roomMoveOptions(Long bookingId) {
        Booking booking = findById(bookingId);
        LocalDate from = moveFrom(booking);
        if (!canMoveRoom(booking) || !booking.getCheckOutDate().isAfter(from)) {
            return List.of();
        }
        int guests = booking.getNumberOfGuests() != null ? booking.getNumberOfGuests() : 1;
        Long typeId = booking.getRoom().getRoomType().getId();
        return roomRepository.findAll().stream()
                .filter(r -> !r.getId().equals(booking.getRoom().getId()))
                .filter(r -> r.getStatus() != RoomStatus.MAINTENANCE)
                .filter(r -> r.getRoomType().getMaxGuests() == null || r.getRoomType().getMaxGuests() >= guests)
                .filter(r -> booking.getStatus() != BookingStatus.CHECKED_IN || r.getStatus() == RoomStatus.AVAILABLE)
                .filter(r -> fitsMove(booking, r, from))
                .map(r -> {
                    BigDecimal amount = roomAmountAfterMove(booking, r);
                    return new RoomMoveOption(r, amount, amount.subtract(booking.getEffectiveRoomAmount()));
                })
                .sorted(java.util.Comparator.comparing((RoomMoveOption o) -> !o.room().getRoomType().getId().equals(typeId))
                        .thenComparing(o -> o.difference().abs())
                        .thenComparing(o -> o.room().getRoomNumber()))
                .toList();
    }

    private boolean fitsMove(Booking booking, Room room, LocalDate from) {
        Availability availability = checkAvailability(room, from, booking.getCheckOutDate(), booking.getId());
        if (!availability.conflicts().isEmpty()) {
            return false;
        }
        try {
            // Khach dang o da nhan phong roi -> chi xet gio tra phong
            LocalTime in = booking.getStatus() == BookingStatus.CHECKED_IN ? LocalTime.MAX : booking.getCheckInTime();
            checkTurnoverTimes(availability, from, in, booking.getCheckOutDate(), booking.getCheckOutTime());
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    // waiveDifference = true: loi phia resort (phong hong, trung phong) / nang hang mien phi -> giu nguyen tien phong
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking moveRoom(Long bookingId, Long newRoomId, boolean waiveDifference, String note) {
        Booking booking = findById(bookingId);
        if (!canMoveRoom(booking)) {
            throw new BusinessException("Chỉ đổi phòng cho đơn đang chờ, đã xác nhận hoặc khách đang ở");
        }
        if (newRoomId == null || newRoomId.equals(booking.getRoom().getId())) {
            throw new BusinessException("Vui lòng chọn phòng mới khác phòng hiện tại");
        }
        Room newRoom = lockRoom(newRoomId).orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
        RoomMoveOption option = roomMoveOptions(bookingId).stream()
                .filter(o -> o.room().getId().equals(newRoomId)).findFirst()
                .orElseThrow(() -> new BusinessException("Phòng " + newRoom.getRoomNumber()
                        + " không còn trống / không phù hợp cho các đêm của đơn này"));
        Room oldRoom = booking.getRoom();

        if (!waiveDifference && option.difference().signum() != 0) {
            booking.setTotalAmount(booking.getTotalAmount().add(option.difference()).max(BigDecimal.ZERO));
            booking.setRoomAmount(option.newRoomAmount());
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            // Phong cu can don, phong moi co khach
            oldRoom.setStatus(RoomStatus.NOT_READY);
            oldRoom.setDirtySince(LocalDateTime.now());
            roomRepository.save(oldRoom);
            newRoom.setStatus(RoomStatus.OCCUPIED);
            roomRepository.save(newRoom);
        }
        String text = note == null ? null : note.trim();
        booking.setRoom(newRoom);
        booking.setMovedFromRoom(oldRoom.getRoomNumber());
        booking.setRoomMovedAt(LocalDateTime.now());
        booking.setRoomMoveNote(text == null || text.isEmpty() ? null : text.substring(0, Math.min(200, text.length())));
        Booking saved = bookingRepository.save(booking);
        emailService.sendRoomMoved(saved, oldRoom.getRoomNumber());
        return saved;
    }

    // ===== Yeu cau nhan phong som / tra phong muon =====

    // Nhan som chi khi chua nhan phong; tra muon ca khi dang o (truoc gio tra hien tai)
    public boolean canRequestStayTime(Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        return switch (booking.getStatus()) {
            case PENDING, CONFIRMED -> now.isBefore(checkInDateTime(booking));
            case CHECKED_IN -> now.isBefore(LocalDateTime.of(booking.getCheckOutDate(), booking.getCheckOutTime()));
            default -> false;
        };
    }

    // Khach gui yeu cau: newCheckInTime / newCheckOutTime null = giu nguyen. It nhat 1 gio phai ngoai khung tieu chuan.
    // Kiem tra truoc khach den truoc / khach den sau cung phong, bao truoc phu phi; le tan duyet moi doi gio
    @Transactional
    public Booking requestStayTime(Booking booking, LocalTime newCheckInTime, LocalTime newCheckOutTime) {
        if (!canRequestStayTime(booking)) {
            throw BusinessException.of("err.stayTime.closed");
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            newCheckInTime = null;
        }
        LocalTime in = newCheckInTime != null ? newCheckInTime : booking.getCheckInTime();
        LocalTime out = newCheckOutTime != null ? newCheckOutTime : booking.getCheckOutTime();
        boolean early = newCheckInTime != null && StayTimePolicy.isEarlyCheckIn(newCheckInTime) && !newCheckInTime.equals(booking.getCheckInTime());
        boolean late = newCheckOutTime != null && StayTimePolicy.isLateCheckOut(newCheckOutTime) && !newCheckOutTime.equals(booking.getCheckOutTime());
        if (!early && !late) {
            throw BusinessException.of("err.stayTime.nothing", StayTimePolicy.STANDARD_CHECK_IN.format(TIME_FMT),
                    StayTimePolicy.STANDARD_CHECK_OUT.format(TIME_FMT));
        }
        checkTurnoverTimes(checkAvailability(booking.getRoom(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId()),
                booking.getCheckInDate(), in, booking.getCheckOutDate(), out);
        BigDecimal fee = StayTimePolicy.fee(booking, early ? StayTimePolicy.earlyPercent(in) : 0)
                .add(StayTimePolicy.fee(booking, late ? StayTimePolicy.latePercent(out) : 0));
        booking.setRequestedCheckInTime(early ? in : null);
        booking.setRequestedCheckOutTime(late ? out : null);
        booking.setTimeRequestFee(fee);
        booking.setTimeRequestStatus(StayTimeRequestStatus.PENDING);
        booking.setTimeRequestedAt(LocalDateTime.now());
        booking.setTimeRequestNote(null);
        return bookingRepository.save(booking);
    }

    // Khach rut lai yeu cau dang cho duyet
    @Transactional
    public Booking withdrawStayTime(Booking booking) {
        if (!booking.isTimeRequestPending()) {
            throw BusinessException.of("err.stayTime.notPending");
        }
        clearStayTimeRequest(booking);
        return bookingRepository.save(booking);
    }

    // Le tan duyet: kiem tra lai lich (co the da co don moi tu luc khach gui), doi gio + cong phu phi vao don
    @Transactional
    public Booking approveStayTime(Long bookingId) {
        Booking booking = findById(bookingId);
        if (!booking.isTimeRequestPending()) {
            throw new BusinessException("Đơn này không có yêu cầu nhận sớm / trả muộn đang chờ duyệt");
        }
        lockRoom(booking.getRoom().getId());
        LocalTime in = booking.getRequestedCheckInTime() != null ? booking.getRequestedCheckInTime() : booking.getCheckInTime();
        LocalTime out = booking.getRequestedCheckOutTime() != null ? booking.getRequestedCheckOutTime() : booking.getCheckOutTime();
        checkTurnoverTimes(checkAvailability(booking.getRoom(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId()),
                booking.getCheckInDate(), in, booking.getCheckOutDate(), out);
        if (booking.getRequestedCheckInTime() != null) {
            int percent = StayTimePolicy.earlyPercent(in);
            addStayTimeCharge(booking, ChargeType.EARLY_CHECKIN, "Nhận phòng lúc " + in.format(TIME_FMT) + " (" + percent + "% giá 1 đêm)",
                    StayTimePolicy.fee(booking, percent));
            booking.setCheckInTime(in);
            booking.setCheckInTimeChanged(true);
        }
        if (booking.getRequestedCheckOutTime() != null) {
            int percent = StayTimePolicy.latePercent(out);
            addStayTimeCharge(booking, ChargeType.LATE_CHECKOUT, "Trả phòng lúc " + out.format(TIME_FMT) + " (" + percent + "% giá 1 đêm)",
                    StayTimePolicy.fee(booking, percent));
            booking.setCheckOutTime(out);
        }
        booking.setTimeRequestStatus(StayTimeRequestStatus.APPROVED);
        Booking saved = bookingRepository.save(booking);
        emailService.sendStayTimeDecision(saved, true);
        return saved;
    }

    @Transactional
    public Booking rejectStayTime(Long bookingId, String note) {
        Booking booking = findById(bookingId);
        if (!booking.isTimeRequestPending()) {
            throw new BusinessException("Đơn này không có yêu cầu nhận sớm / trả muộn đang chờ duyệt");
        }
        String reason = note == null ? null : note.trim();
        booking.setTimeRequestStatus(StayTimeRequestStatus.REJECTED);
        booking.setTimeRequestNote(reason == null || reason.isEmpty() ? null : reason.substring(0, Math.min(200, reason.length())));
        Booking saved = bookingRepository.save(booking);
        emailService.sendStayTimeDecision(saved, false);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Booking> findPendingStayTimeRequests() {
        return bookingRepository.findByTimeRequestStatusOrderByTimeRequestedAtAsc(StayTimeRequestStatus.PENDING);
    }

    // So yeu cau dang cho duyet - hien tren sidebar / man hinh Hom nay
    public long countPendingStayTimeRequests() {
        return bookingRepository.countByTimeRequestStatus(StayTimeRequestStatus.PENDING);
    }

    private void addStayTimeCharge(Booking booking, ChargeType type, String description, BigDecimal amount) {
        if (amount.signum() > 0) {
            chargeRepository.save(BookingCharge.builder().booking(booking).type(type).description(description).amount(amount).build());
        }
    }

    private static void clearStayTimeRequest(Booking booking) {
        booking.setRequestedCheckInTime(null);
        booking.setRequestedCheckOutTime(null);
        booking.setTimeRequestFee(null);
        booking.setTimeRequestStatus(null);
        booking.setTimeRequestedAt(null);
        booking.setTimeRequestNote(null);
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
        // Tien da tru bang diem thuong / khuyen mai luc dat giu nguyen (tien phong khong doi)
        BigDecimal totalAmount = subtotal.subtract(discountAmount).subtract(booking.getPointsDiscountAmount())
                .subtract(booking.getGiftVoucherAmountOrZero()).subtract(booking.getMemberDiscountAmountOrZero())
                .subtract(booking.getPromotionAmountOrZero()).max(BigDecimal.ZERO);

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
                                  BigDecimal depositAmount, boolean depositPaid, PromotionService.Applied promotion) {
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
        applyPromotion(booking, quote.promotion());
        if (booking.getMemberDiscountPercent() != null) {
            booking.setMemberDiscountAmount(memberDiscountFor(booking, quote.roomAmount(), booking.getPromotionAmountOrZero()));
        }
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
        // Khuyen mai tu dong xet lai theo ngay moi (dieu kien dat som / o dai tinh tu ngay khach dat), chi don online
        PromotionService.Applied promotion = booking.getEffectiveChannel() == BookingChannel.ONLINE
                ? promotionService.best(roomAmount, newCheckIn, newCheckOut,
                        booking.getCreatedAt() != null ? booking.getCreatedAt().toLocalDate() : LocalDate.now()).orElse(null)
                : null;
        BigDecimal promotionAmount = promotion != null ? promotion.amount() : BigDecimal.ZERO;
        BigDecimal memberAmount = memberDiscountFor(booking, roomAmount, promotionAmount);
        // Tien da tru bang diem thuong luc dat giu nguyen
        BigDecimal totalAmount = subtotal.subtract(discountAmount).subtract(promotionAmount).subtract(memberAmount)
                .subtract(booking.getPointsDiscountAmount()).subtract(booking.getGiftVoucherAmountOrZero()).max(BigDecimal.ZERO);
        BigDecimal deposit = booking.isDepositRequired() && !booking.isDepositPaid()
                ? pricingService.depositFor(totalAmount) : booking.getDepositPaidAmount();
        return new DateChangeQuote(newCheckIn, newCheckOut, ChronoUnit.DAYS.between(newCheckIn, newCheckOut),
                roomAmount, discountAmount, totalAmount, totalAmount.subtract(booking.getTotalAmount()),
                deposit, booking.isDepositPaid(), promotion);
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
        loyaltyService.refundUsed(booking);
        // Unit test khoi tao BookingService bang constructor nen co the chua co (setter injection)
        if (giftVoucherService != null) {
            giftVoucherService.refund(booking);
        }
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

    public enum OnlineDepositResult { CONFIRMED, ALREADY_PAID, NOT_FOUND, WRONG_AMOUNT, NOT_AWAITING, PAID_BUT_ROOM_TAKEN }

    // Cong thanh toan (VNPay) bao khach da tra coc thanh cong -> ghi nhan coc + xac nhan don, khong can nhan vien.
    // Goi lap lai (trang tra ve + IPN cua VNPay cung bao 1 giao dich) chi ghi nhan 1 lan.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnlineDepositResult confirmOnlineDeposit(Long bookingId, BigDecimal paidAmount, String transactionRef) {
        return confirmOnlineDeposit(bookingId, paidAmount, transactionRef, null, null);
    }

    // txnRef / payDate: luu lai de sau nay hoan coc tu dong qua API cua VNPay
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnlineDepositResult confirmOnlineDeposit(Long bookingId, BigDecimal paidAmount, String transactionRef,
                                                    String txnRef, String payDate) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || !booking.isDepositRequired()) {
            return OnlineDepositResult.NOT_FOUND;
        }
        if (booking.isDepositPaid()) {
            return OnlineDepositResult.ALREADY_PAID;
        }
        if (paidAmount == null || paidAmount.compareTo(booking.getDepositAmount()) != 0) {
            return OnlineDepositResult.WRONG_AMOUNT;
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            return OnlineDepositResult.NOT_AWAITING;
        }
        booking.setDepositPaidAt(LocalDateTime.now());
        booking.setDepositOnlineRef(transactionRef);
        booking.setDepositOnlineTxnRef(txnRef);
        booking.setDepositOnlinePayDate(payDate);
        try {
            ensureNoConfirmedConflict(booking);
        } catch (BusinessException roomTaken) {
            // Tien da tru cua khach nhung phong vua bi don khac giu: ghi nhan da coc, de don o Cho xac nhan
            // de le tan lien he khach (doi phong hoac huy + hoan coc 100%)
            bookingRepository.save(booking);
            return OnlineDepositResult.PAID_BUT_ROOM_TAKEN;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        Booking confirmedBooking = bookingRepository.save(booking);
        emailService.sendBookingConfirmation(confirmedBooking);
        return OnlineDepositResult.CONFIRMED;
    }

    // VNPay bao ca doan da tra coc gop: so tien phai bang tong coc cac phong dang cho coc -> ghi nhan tung phong.
    // Goi lap lai (trang tra ve + IPN) chi ghi nhan 1 lan; phong bi don khac giu thi van ghi da coc, cho le tan xu ly
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OnlineDepositResult confirmOnlineGroupDeposit(Long groupId, BigDecimal paidAmount, String transactionRef,
                                                         String txnRef, String payDate) {
        if (groupId == null || bookingGroupRepository.findById(groupId).isEmpty()) {
            return OnlineDepositResult.NOT_FOUND;
        }
        List<Booking> bookings = groupBookings(groupId);
        List<Booking> awaiting = bookings.stream().filter(Booking::isAwaitingDeposit).toList();
        if (awaiting.isEmpty()) {
            boolean sameTxn = txnRef != null && bookings.stream().anyMatch(b -> txnRef.equals(b.getDepositOnlineTxnRef()));
            return sameTxn ? OnlineDepositResult.ALREADY_PAID : OnlineDepositResult.NOT_AWAITING;
        }
        BigDecimal due = awaiting.stream().map(Booking::getDepositAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paidAmount == null || paidAmount.compareTo(due) != 0) {
            return OnlineDepositResult.WRONG_AMOUNT;
        }
        OnlineDepositResult overall = OnlineDepositResult.CONFIRMED;
        for (Booking b : awaiting) {
            OnlineDepositResult r = confirmOnlineDeposit(b.getId(), b.getDepositAmount(), transactionRef, txnRef, payDate);
            if (r != OnlineDepositResult.CONFIRMED) {
                overall = OnlineDepositResult.PAID_BUT_ROOM_TAKEN;
            }
        }
        return overall;
    }

    // Truoc khi chuyen khach sang cong thanh toan: don con cho coc, chua qua han, phong chua bi don khac giu
    @Transactional(readOnly = true)
    public void ensureCanPayDepositOnline(Booking booking) {
        if (!booking.isAwaitingDeposit()) {
            throw new BusinessException("Đơn này không còn chờ đặt cọc");
        }
        if (booking.getDepositDeadline() != null && !booking.getDepositDeadline().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Đã quá hạn chuyển cọc cho đơn này");
        }
        ensureNoConfirmedConflict(booking);
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
        // Khach truoc vua tra phong: phai don xong (bang "Don phong") moi giao phong cho khach moi
        if (booking.getRoom().getStatus() == RoomStatus.NOT_READY) {
            throw new BusinessException("Phòng " + booking.getRoom().getRoomNumber()
                    + " chưa dọn xong sau khách trước. Vui lòng hoàn tất ở mục Dọn phòng rồi mới nhận phòng");
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
        // Tich diem theo hang hien tai (truoc khi xet len hang ben duoi)
        loyaltyService.earn(booking);

        Room room = booking.getRoom();
        room.setStatus(RoomStatus.NOT_READY);
        room.setDirtySince(LocalDateTime.now());
        room.setCleaningBy(null);
        room.setCleaningStartedAt(null);
        roomRepository.save(room);

        Booking checkedOutBooking = bookingRepository.save(booking);
        upgradeLoyaltyTier(checkedOutBooking);
        emailService.sendBookingConfirmation(checkedOutBooking);
        return checkedOutBooking;
    }

    // Khach than thiet: sau moi lan tra phong, du so lan luu tru hoan tat thi tu len hang (khong bao gio tu ha hang)
    private void upgradeLoyaltyTier(Booking booking) {
        User customer = booking.getCustomer();
        // Don vua luu thanh CHECKED_OUT - Hibernate flush truoc truy van dem nen da tinh ca don nay
        long stays = bookingRepository.countByCustomerIdAndStatus(customer.getId(), BookingStatus.CHECKED_OUT);
        CustomerType earned = CustomerType.forCompletedStays(stays);
        CustomerType current = customer.getCustomerType() != null ? customer.getCustomerType() : CustomerType.NEW;
        if (earned.ordinal() > current.ordinal()) {
            customer.setCustomerType(earned);
        }
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

    // VNPay bao hoan tien thanh cong -> ghi nhan da hoan (kem ma giao dich hoan) va email bao khach
    @Transactional
    public Booking confirmRefundOnline(Long bookingId, String refundTransactionNo) {
        Booking booking = findById(bookingId);
        if (!booking.isRefundPending()) {
            throw new BusinessException("Đơn này không có khoản hoàn cọc nào đang chờ xử lý");
        }
        booking.setRefundedAt(LocalDateTime.now());
        booking.setRefundOnlineRef(refundTransactionNo);
        booking.setRefundNote("Hoàn qua VNPay" + (refundTransactionNo != null ? ", mã GD " + refundTransactionNo : ""));
        Booking refunded = bookingRepository.save(booking);
        emailService.sendRefundCompleted(refunded);
        return refunded;
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
