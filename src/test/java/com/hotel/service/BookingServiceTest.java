package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingGuestRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.PricingSettingsRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.SpecialRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private EmailService emailService;
    @Mock private BookingChargeRepository chargeRepository;
    @Mock private BookingGuestRepository guestRepository;
    // Gia that (khong phu thu, khong coc vi repo cai dat rong) - test nao can thi stub them
    @Spy private PricingService pricingService =
            new PricingService(mock(PricingSettingsRepository.class), mock(SpecialRateRepository.class));

    @InjectMocks
    private BookingService bookingService;

    private User customer;
    private RoomType roomType;
    private Room room;

    @BeforeEach
    void setUp() {
        customer = User.builder().id(1L).fullName("Nguyen Van A").email("a@mail.com")
                .phoneNumber("0901111111").role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build();
        roomType = RoomType.builder().id(1L).name("Phong Doi").basePrice(bd(300000)).maxGuests(2).build();
        room = Room.builder().id(10L).roomNumber("101").roomType(roomType)
                .price(bd(300000)).status(RoomStatus.AVAILABLE).build();
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    // ---------- Validation truoc khi tao booking ----------

    @Test
    void createBooking_missingGuestName_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "", "0901234567", "guest@mail.com"));
        assertTrue(ex.getMessage().contains("họ tên"));
    }

    @Test
    void createBooking_missingGuestPhone_throws() {
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", " ", "guest@mail.com"));
    }

    @Test
    void createBooking_missingGuestEmail_throws() {
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", ""));
    }

    @Test
    void createBooking_invalidGuestEmailFormat_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "khong-hop-le"));
        assertTrue(ex.getMessage().toLowerCase().contains("khong hop le") || ex.getMessage().toLowerCase().contains("không hợp lệ"));
    }

    @Test
    void createBooking_checkOutNotAfterCheckIn_throws() {
        LocalDate d = LocalDate.now().plusDays(3);
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, d, d, LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    @Test
    void createBooking_checkInInPast_throws() {
        LocalDate past = LocalDate.now().minusDays(1);
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, past, past.plusDays(2), LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    @Test
    void createBooking_roomUnderMaintenance_throws() {
        room.setStatus(RoomStatus.MAINTENANCE);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com"));
        assertTrue(ex.getMessage().contains("bảo trì"));
    }

    @Test
    void createBooking_overlappingDates_throws() {
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(3);
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(true);
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 10L, in, out, LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    @Test
    void createBooking_roomNotFound_throws() {
        when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> bookingService.createBooking(
                customer, 99L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    // ---------- Tao booking thanh cong + tinh tien ----------

    @Test
    void createBooking_success_defaultsStatusPendingAndNewBookingTrue() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(3); // 2 dem
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertTrue(result.isNewBooking());
        assertEquals(0, bd(600000).compareTo(result.getTotalAmount())); // 300000 * 2 dem
        verify(emailService, times(1)).sendBookingConfirmation(result);
    }

    @Test
    void createBooking_withCombo_addsComboAmount() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2); // 1 dem
        Combo combo = Combo.builder().id(5L).name("Combo A").price(bd(150000)).active(true).build();
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(comboRepository.findById(5L)).thenReturn(Optional.of(combo));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, 5L, null,
                "Nguyen Van A", "0901234567", "guest@mail.com");

        // 300000 (1 dem) + 150000 combo = 450000
        assertEquals(0, bd(450000).compareTo(result.getTotalAmount()));
    }

    @Test
    void createBooking_withPercentageDiscount_calculatesCorrectly() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2); // 1 dem = 300000
        DiscountCode code = DiscountCode.builder().id(1L).code("SALE10").discountType(DiscountType.PERCENTAGE)
                .discountValue(bd(10)).applicableCustomerType(null).active(true).build();
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(discountCodeRepository.findByCodeAndActiveTrue("SALE10")).thenReturn(Optional.of(code));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, "sale10",
                "Nguyen Van A", "0901234567", "guest@mail.com");

        assertEquals(0, bd(30000).compareTo(result.getDiscountAmount()));
        assertEquals(0, bd(270000).compareTo(result.getTotalAmount()));
    }

    @Test
    void createBooking_withFixedDiscount_cappedAtSubtotal() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2); // subtotal = 300000
        DiscountCode code = DiscountCode.builder().id(2L).code("FIX500K").discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(bd(500000)).applicableCustomerType(null).active(true).build();
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(discountCodeRepository.findByCodeAndActiveTrue("FIX500K")).thenReturn(Optional.of(code));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, "FIX500K",
                "Nguyen Van A", "0901234567", "guest@mail.com");

        // Giam gia khong duoc vuot subtotal -> discountAmount = 300000, total = 0
        assertEquals(0, bd(300000).compareTo(result.getDiscountAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalAmount()));
    }

    @Test
    void createBooking_discountWrongCustomerType_throws() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        DiscountCode code = DiscountCode.builder().id(3L).code("VIPONLY").discountType(DiscountType.PERCENTAGE)
                .discountValue(bd(20)).applicableCustomerType(CustomerType.VIP).active(true).build();
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(discountCodeRepository.findByCodeAndActiveTrue("VIPONLY")).thenReturn(Optional.of(code));

        assertThrows(BusinessException.class, () -> bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, "VIPONLY",
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    @Test
    void createBooking_discountNotFoundOrInactive_throws() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(discountCodeRepository.findByCodeAndActiveTrue("NOPE")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, "nope",
                "Nguyen Van A", "0901234567", "guest@mail.com"));
    }

    @Test
    void createBooking_defaultGuestsWhenNullOrNonPositive() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), null, null, null,
                "Nguyen Van A", "0901234567", "guest@mail.com");

        assertEquals(1, result.getNumberOfGuests());
    }

    // ---------- canModify / updateTimes / updateCombo ----------

    private Booking bookingWithCheckIn(LocalDateTime checkInDateTime, BookingStatus status) {
        return Booking.builder().id(100L).customer(customer).room(room)
                .checkInDate(checkInDateTime.toLocalDate()).checkOutDate(checkInDateTime.toLocalDate().plusDays(1))
                .checkInTime(checkInDateTime.toLocalTime()).checkOutTime(LocalTime.of(12, 0))
                .totalAmount(bd(300000)).discountAmount(BigDecimal.ZERO).status(status).build();
    }

    @Test
    void canModify_pendingWithEnoughLeadTime_true() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(8), BookingStatus.PENDING);
        assertTrue(bookingService.canModify(b));
    }

    @Test
    void canModify_withinCutoffHours_false() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(3), BookingStatus.CONFIRMED);
        assertFalse(bookingService.canModify(b));
    }

    @Test
    void canModify_wrongStatus_false() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(20), BookingStatus.CHECKED_IN);
        assertFalse(bookingService.canModify(b));
    }

    @Test
    void updateTimes_success_setsCheckInTimeChangedTrue() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.updateTimes(100L, 1L, LocalTime.of(16, 0), LocalTime.of(11, 0));

        assertTrue(result.isCheckInTimeChanged());
        assertEquals(LocalTime.of(16, 0), result.getCheckInTime());
    }

    @Test
    void updateTimes_tooLate_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(2), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class,
                () -> bookingService.updateTimes(100L, 1L, LocalTime.of(16, 0), LocalTime.of(11, 0)));
    }

    @Test
    void updateCombo_recalculatesTotal() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        b.setCheckOutDate(b.getCheckInDate().plusDays(2)); // 2 dem
        Combo newCombo = Combo.builder().id(7L).name("Combo B").price(bd(100000)).active(true).build();
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(comboRepository.findById(7L)).thenReturn(Optional.of(newCombo));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.updateCombo(100L, 1L, 7L);

        // 300000 * 2 dem + 100000 combo = 700000
        assertEquals(0, bd(700000).compareTo(result.getTotalAmount()));
    }

    // ---------- Huy / Xac nhan / Check-in / Check-out ----------

    @Test
    void cancelBooking_fromPending_success() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.cancelBooking(100L, 1L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelBooking_fromCheckedIn_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CHECKED_IN);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.cancelBooking(100L, 1L));
    }

    @Test
    void confirmBooking_fromPending_success() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.confirmBooking(100L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void confirmBooking_notPending_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.confirmBooking(100L));
    }

    @Test
    void checkIn_fromConfirmed_setsRoomOccupied() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(1), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(guestRepository.countByBookingId(100L)).thenReturn(1L);

        Booking result = bookingService.checkIn(100L);

        assertEquals(BookingStatus.CHECKED_IN, result.getStatus());
        assertEquals(RoomStatus.OCCUPIED, room.getStatus());
    }

    @Test
    void checkIn_notConfirmed_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(1), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.checkIn(100L));
    }

    @Test
    void checkOut_fromCheckedIn_setsRoomNotReady() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().minusHours(1), BookingStatus.CHECKED_IN);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.checkOut(100L);

        assertEquals(BookingStatus.CHECKED_OUT, result.getStatus());
        assertEquals(RoomStatus.NOT_READY, room.getStatus());
    }

    @Test
    void checkOut_notCheckedIn_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(1), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.checkOut(100L));
    }

    @Test
    void createWalkInBooking_setsConfirmedAndNewBookingFalse() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createWalkInBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null,
                "Khach Vang Lai", "0909999999", "walkin@mail.com");

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertFalse(result.isNewBooking());
    }

    @Test
    void createWalkInBooking_stillRejectsOverlap() {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(true);

        assertThrows(BusinessException.class, () -> bookingService.createWalkInBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null,
                "Khach Vang Lai", "0909999999", "walkin@mail.com"));
    }

    @Test
    void cancelBookingByStaff_fromConfirmed_success() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.cancelBookingByStaff(100L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelBookingByStaff_wrongStatus_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CHECKED_OUT);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.cancelBookingByStaff(100L));
    }

    // ---------- Tim kiem / loc ----------

    @Test
    void findAllBookings_filterByStatus() {
        Booking pending = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        Booking confirmed = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(pending, confirmed));

        List<Booking> result = bookingService.findAllBookings(BookingStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(BookingStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    void findAllBookings_searchByKeyword_matchesGuestFields() {
        Booking b1 = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        b1.setGuestPhone("0901234567");
        b1.setGuestName("Tran Thi B");
        Booking b2 = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        b2.setGuestPhone("0987654321");
        b2.setGuestName("Le Van C");
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(b1, b2));

        List<Booking> result = bookingService.findAllBookings(null, "0901234567");

        assertEquals(1, result.size());
        assertEquals("Tran Thi B", result.get(0).getGuestName());
    }

    @Test
    void findByIdAndMarkSeenByStaff_clearsFlags() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        b.setNewBooking(true);
        b.setCheckInTimeChanged(true);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.findByIdAndMarkSeenByStaff(100L);

        assertFalse(result.isNewBooking());
        assertFalse(result.isCheckInTimeChanged());
    }

    @Test
    void countNewBookings_delegatesToRepository() {
        when(bookingRepository.countByNewBookingTrue()).thenReturn(3L);
        assertEquals(3L, bookingService.countNewBookings());
    }

    @Test
    void findByIdForCustomer_wrongOwner_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.findByIdForCustomer(100L, 999L));
    }

    // ---------- Thong ke dashboard ----------

    @Test
    void getBookingStatusCounts_onlyCountsWithinRange() {
        LocalDate today = LocalDate.now();
        Booking inRange = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        inRange.setCreatedAt(today.atTime(10, 0));
        Booking outOfRange = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        outOfRange.setCreatedAt(today.minusDays(90).atTime(10, 0));
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(inRange, outOfRange));

        Map<BookingStatus, Long> counts = bookingService.getBookingStatusCounts(today.minusDays(5), today.plusDays(5));

        assertEquals(1L, counts.get(BookingStatus.CONFIRMED));
    }

    @Test
    void getRevenueByRoomType_excludesCancelled() {
        LocalDate today = LocalDate.now();
        Booking cancelled = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CANCELLED);
        cancelled.setCreatedAt(today.atTime(10, 0));
        Booking confirmed = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        confirmed.setCreatedAt(today.atTime(10, 0));
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(cancelled, confirmed));

        Map<String, BigDecimal> revenue = bookingService.getRevenueByRoomType(today.minusDays(1), today.plusDays(1));

        assertEquals(1, revenue.size());
        assertEquals(0, bd(300000).compareTo(revenue.get("Phong Doi")));
    }

    @Test
    void revenueCharts_includeSurcharges() {
        LocalDate today = LocalDate.now();
        Booking checkedOut = bookingWithCheckIn(LocalDateTime.now().minusDays(1), BookingStatus.CHECKED_OUT);
        checkedOut.setCreatedAt(today.atTime(10, 0));
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(checkedOut));
        // Don #100 co 50.000d phu phi (minibar, giat ui...)
        when(chargeRepository.sumAmountGroupByBooking()).thenReturn(List.<Object[]>of(new Object[]{100L, bd(50000)}));

        Map<String, BigDecimal> byType = bookingService.getRevenueByRoomType(today.minusDays(1), today.plusDays(1));
        Map<String, BigDecimal> trend = bookingService.getRevenueTrend(today.minusDays(1), today.plusDays(1));

        assertThat(byType.get("Phong Doi")).isEqualByComparingTo(bd(350000));
        assertThat(trend.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(bd(350000));
    }

    private Booking stay(LocalDate in, LocalDate out, long total, BookingStatus status) {
        return Booking.builder().id(200L).customer(customer).room(room).checkInDate(in).checkOutDate(out)
                .totalAmount(bd(total)).discountAmount(BigDecimal.ZERO).status(status).build();
    }

    @Test
    void getHotelKpi_countsOnlyNightsInsideRangeAndSoldStatuses() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 10);                       // 10 ngay x 2 phong = 20 dem-phong
        when(roomRepository.count()).thenReturn(2L);
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                stay(from.plusDays(2), from.plusDays(5), 900000, BookingStatus.CONFIRMED),     // 3 dem, ca 3 trong khoang
                stay(from.minusDays(1), from.plusDays(1), 400000, BookingStatus.CHECKED_OUT),  // 2 dem, 1 dem trong khoang
                stay(from.plusDays(1), from.plusDays(3), 500000, BookingStatus.PENDING),       // chua xac nhan -> bo qua
                stay(from.plusDays(1), from.plusDays(3), 500000, BookingStatus.CANCELLED),     // da huy -> bo qua
                stay(to, to.plusDays(4), 800000, BookingStatus.CHECKED_IN)));                  // 4 dem, 1 dem (dem 10/3) trong khoang

        BookingService.HotelKpi kpi = bookingService.getHotelKpi(from, to);

        // da ban 3 + 1 + 1 = 5 dem; tien = 900.000 + 200.000 + 200.000 = 1.300.000
        assertEquals(5, kpi.soldRoomNights());
        assertEquals(20, kpi.availableRoomNights());
        assertEquals(25.0, kpi.occupancyRate());
        assertThat(kpi.adr()).isEqualByComparingTo(bd(260000));
        assertThat(kpi.revPar()).isEqualByComparingTo(bd(65000));
    }

    @Test
    void getHotelKpi_noRoomsOrBookings_returnsZeros() {
        when(roomRepository.count()).thenReturn(0L);
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        BookingService.HotelKpi kpi = bookingService.getHotelKpi(LocalDate.now().minusDays(6), LocalDate.now());

        assertEquals(0.0, kpi.occupancyRate());
        assertThat(kpi.adr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(kpi.revPar()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getRevenueTrend_groupsByDayForShortRange() {
        LocalDate today = LocalDate.now();
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        b.setCreatedAt(today.atTime(10, 0));
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(b));

        Map<String, BigDecimal> trend = bookingService.getRevenueTrend(today.minusDays(5), today.plusDays(5));

        // 11 ngay -> gom theo ngay -> 11 nhan
        assertEquals(11, trend.size());
    }

    @Test
    void getRevenueTrend_groupsByMonthForLongRange() {
        LocalDate today = LocalDate.now();
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(10), BookingStatus.CONFIRMED);
        b.setCreatedAt(today.atTime(10, 0));
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(b));

        Map<String, BigDecimal> trend = bookingService.getRevenueTrend(today.minusDays(90), today);

        assertThat(trend.size()).isLessThanOrEqualTo(4);
        assertThat(trend.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(bd(300000));
    }

    @Test
    void getRevenueTrend_emptyRange_noErrorAndZeroTotal() {
        LocalDate today = LocalDate.now();
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        Map<String, BigDecimal> trend = bookingService.getRevenueTrend(today, today);

        assertEquals(1, trend.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(trend.values().iterator().next()));
    }

    // ---------- Dat coc / khai bao luu tru ----------

    @Test
    void createBooking_depositEnabled_setsDepositAndDeadline() {
        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = in.plusDays(1);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, in, out)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(bd(300000)).when(pricingService).roomAmount(room.getPrice(), in, out);
        doReturn(bd(90000)).when(pricingService).depositFor(any());
        doReturn(PricingSettings.builder().weekendSurchargePercent(0).depositPercent(30).depositDeadlineHours(12).build())
                .when(pricingService).getSettings();

        Booking b = bookingService.createBooking(customer, 10L, in, out, LocalTime.of(14, 0), LocalTime.of(12, 0),
                2, null, null, "A", "0901111111", "a@mail.com");

        assertEquals(0, bd(300000).compareTo(b.getRoomAmount()));
        assertEquals(0, bd(90000).compareTo(b.getDepositAmount()));
        assertTrue(b.isAwaitingDeposit());
        assertTrue(b.getDepositDeadline().isBefore(LocalDateTime.now().plusHours(12).plusMinutes(1)));
    }

    @Test
    void confirmDeposit_pendingWithDeposit_confirmsAndRecordsTime() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusDays(3), BookingStatus.PENDING);
        b.setDepositAmount(bd(90000));
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.confirmDeposit(100L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertNotNull(result.getDepositPaidAt());
        assertEquals(0, bd(90000).compareTo(result.getDepositPaidAmount()));
        verify(emailService).sendBookingConfirmation(result);
    }

    @Test
    void confirmDeposit_noDepositOrAlreadyPaid_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusDays(3), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        assertThrows(BusinessException.class, () -> bookingService.confirmDeposit(100L));

        b.setDepositAmount(bd(90000));
        b.setDepositPaidAt(LocalDateTime.now());
        assertThrows(BusinessException.class, () -> bookingService.confirmDeposit(100L));
    }

    @Test
    void checkIn_withoutDeclaredGuests_throws() {
        Booking b = bookingWithCheckIn(LocalDateTime.now().plusHours(1), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(b));
        when(guestRepository.countByBookingId(100L)).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.checkIn(100L));
        assertTrue(ex.getMessage().contains("khai báo lưu trú"));
        assertEquals(BookingStatus.CONFIRMED, b.getStatus());
    }

    // ---------- Kiem tra phong trong chi tiet ----------

    private Booking active(long id, LocalDate in, LocalDate out, BookingStatus status) {
        return Booking.builder().id(id).customer(customer).room(room).checkInDate(in).checkOutDate(out)
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .totalAmount(bd(300000)).status(status).build();
    }

    @Test
    void checkAvailability_pendingConflict_describesPendingAndFreeFromAfterChainedBookings() {
        LocalDate d = LocalDate.now().plusDays(10);
        // Don cho xac nhan d -> d+2, ngay d+2 lai co don da xac nhan noi tiep d+2 -> d+3
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), anyList())).thenReturn(List.of(
                active(1L, d, d.plusDays(2), BookingStatus.PENDING),
                active(2L, d.plusDays(2), d.plusDays(3), BookingStatus.CONFIRMED)));

        BookingService.Availability a = bookingService.checkAvailability(room, d.plusDays(1), d.plusDays(2));

        assertFalse(a.isAvailable());
        assertTrue(a.isOnlyPendingConflicts());
        assertEquals(LocalDateTime.of(d.plusDays(3), LocalTime.of(12, 0)), a.freeFrom());
        String msg = a.describe();
        assertTrue(msg.contains("đơn chờ xác nhận"), msg);
        assertTrue(msg.contains("Phòng trống trở lại từ 12:00"), msg);
    }

    @Test
    void checkAvailability_turnoverDays_giveEarliestCheckInAndLatestCheckOut() {
        LocalDate d = LocalDate.now().plusDays(10);
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), anyList())).thenReturn(List.of(
                active(1L, d.minusDays(2), d, BookingStatus.CONFIRMED),
                active(2L, d.plusDays(2), d.plusDays(4), BookingStatus.PENDING)));

        BookingService.Availability a = bookingService.checkAvailability(room, d, d.plusDays(2));

        assertTrue(a.isAvailable());
        assertEquals(LocalTime.of(12, 0), a.earliestCheckIn());
        assertEquals(LocalTime.of(14, 0), a.latestCheckOut());
    }

    @Test
    void checkAvailability_maintenanceRoom_notAvailableWithClearMessage() {
        room.setStatus(RoomStatus.MAINTENANCE);
        BookingService.Availability a = bookingService.checkAvailability(room, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        assertFalse(a.isAvailable());
        assertTrue(a.describe().contains("bảo trì"));
    }

    @Test
    void createBooking_checkInBeforePreviousGuestLeaves_rejectedWithTime() {
        LocalDate d = LocalDate.now().plusDays(10);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(10L, d, d.plusDays(1))).thenReturn(false);
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), anyList()))
                .thenReturn(List.of(active(1L, d.minusDays(1), d, BookingStatus.CONFIRMED)));

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(customer, 10L, d, d.plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(12, 0), 2, null, null, "A", "0901111111", "a@mail.com"));
        assertTrue(ex.getMessage().contains("từ 12:00"), ex.getMessage());
    }

    @Test
    void updateTimes_checkOutAfterNextGuestArrives_rejected() {
        LocalDate d = LocalDate.now().plusDays(10);
        Booking mine = active(100L, d, d.plusDays(1), BookingStatus.CONFIRMED);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(mine));
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), anyList())).thenReturn(List.of(
                mine, active(2L, d.plusDays(1), d.plusDays(2), BookingStatus.CONFIRMED)));

        assertThrows(BusinessException.class,
                () -> bookingService.updateTimes(100L, 1L, LocalTime.of(14, 0), LocalTime.of(15, 0)));
    }

    @Test
    void confirmBooking_roomAlreadyConfirmedForOtherBooking_rejected() {
        LocalDate d = LocalDate.now().plusDays(10);
        Booking pending = active(100L, d, d.plusDays(1), BookingStatus.PENDING);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(pending));
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), anyList())).thenReturn(List.of(
                pending, active(7L, d, d.plusDays(1), BookingStatus.CONFIRMED)));

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.confirmBooking(100L));
        assertTrue(ex.getMessage().contains("#7"), ex.getMessage());
        assertEquals(BookingStatus.PENDING, pending.getStatus());
    }
}
