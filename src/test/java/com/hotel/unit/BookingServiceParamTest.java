package com.hotel.unit;

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
import com.hotel.service.BookingService;
import com.hotel.service.EmailService;
import com.hotel.service.PricingService;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Đặt phòng - Logic nghiệp vụ (BookingService)")
class BookingServiceParamTest {

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

    @InjectMocks private BookingService bookingService;

    private User customer;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        customer = User.builder().id(1L).fullName("Nguyễn Văn An").email("an@gmail.com").phoneNumber("0912345678")
                .role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build();
        roomType = RoomType.builder().id(1L).name("Phòng thường").basePrice(bd(200000)).maxGuests(2).build();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.existsOverlappingBooking(anyLong(), any(), any())).thenReturn(false);
        // Moi don mock deu da khai bao luu tru 1 khach (dieu kien de check-in)
        when(guestRepository.countByBookingId(anyLong())).thenReturn(1L);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private Room stubRoom(long price, RoomStatus status) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(bd(price)).status(status).build();
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        return room;
    }

    private Booking create(int nights, Long comboId, String code) {
        LocalDate in = LocalDate.now().plusDays(3);
        return bookingService.createBooking(customer, 10L, in, in.plusDays(nights), LocalTime.of(14, 0), LocalTime.of(12, 0),
                2, comboId, code, "Nguyễn Văn An", "0912345678", "an@gmail.com");
    }

    private void stubCombo(long price) {
        when(comboRepository.findById(5L)).thenReturn(Optional.of(
                Combo.builder().id(5L).name("Combo").price(bd(price)).active(true).build()));
    }

    private void stubCode(String code, DiscountType type, String value, CustomerType applicable) {
        when(discountCodeRepository.findByCodeAndActiveTrue(code)).thenReturn(Optional.of(DiscountCode.builder()
                .id(7L).code(code).discountType(type).discountValue(new BigDecimal(value))
                .applicableCustomerType(applicable).active(true).build()));
    }

    // ===================== Tính tiền phòng =====================

    @TcSteps("Mock phòng có đơn giá P, gọi createBooking() với số đêm N và combo C (nếu có), đọc totalAmount")
    @ParameterizedTest(name = "Tính tổng tiền: {1} đêm × {0} VND + combo {2} VND ¦ price={0}, nights={1}, combo={2} ¦ totalAmount = {3} VND")
    @CsvSource({
            "200000,1,0,200000", "200000,2,0,400000", "200000,3,0,600000", "200000,5,0,1000000", "200000,7,0,1400000", "200000,14,0,2800000",
            "500000,1,0,500000", "500000,2,0,1000000", "500000,3,0,1500000", "500000,5,0,2500000", "500000,7,0,3500000", "500000,14,0,7000000",
            "799000,1,0,799000", "799000,2,0,1598000", "799000,3,0,2397000", "799000,5,0,3995000", "799000,7,0,5593000", "799000,14,0,11186000",
            "200000,1,399000,599000", "200000,2,399000,799000", "200000,3,399000,999000", "200000,5,399000,1399000", "200000,7,399000,1799000", "200000,14,399000,3199000",
            "500000,1,399000,899000", "500000,2,399000,1399000", "500000,3,399000,1899000", "500000,5,399000,2899000", "500000,7,399000,3899000", "500000,14,399000,7399000",
            "799000,1,299000,1098000", "799000,2,299000,1897000", "799000,3,299000,2696000", "799000,5,299000,4294000", "799000,7,299000,5892000", "799000,14,299000,11485000"})
    void totalAmount_roomNightsPlusCombo(long price, int nights, long combo, long expected) {
        stubRoom(price, RoomStatus.AVAILABLE);
        Long comboId = null;
        if (combo > 0) {
            stubCombo(combo);
            comboId = 5L;
        }
        Booking b = create(nights, comboId, null);
        assertThat(b.getTotalAmount()).isEqualByComparingTo(bd(expected));
        assertThat(b.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @TcSteps("Mock phòng 500.000/đêm, mã PERCENTAGE x%, gọi createBooking() N đêm (có/không combo 399.000)")
    @ParameterizedTest(name = "Giảm giá theo phần trăm {0}% trên {1} đêm, combo {2} ¦ PERCENTAGE {0}%, nights={1}, combo={2} ¦ discountAmount = {3}, totalAmount = {4}")
    @CsvSource({
            "5,1,0,25000,475000", "10,1,0,50000,450000", "15,1,0,75000,425000", "20,1,0,100000,400000", "50,1,0,250000,250000", "100,1,0,500000,0",
            "5,3,0,75000,1425000", "10,3,0,150000,1350000", "15,3,0,225000,1275000", "20,3,0,300000,1200000", "50,3,0,750000,750000", "100,3,0,1500000,0",
            "5,2,399000,69950,1329050", "10,2,399000,139900,1259100", "15,2,399000,209850,1189150", "20,2,399000,279800,1119200", "50,2,399000,699500,699500", "100,2,399000,1399000,0"})
    void discount_percentage(String percent, int nights, long combo, String expectedDiscount, String expectedTotal) {
        stubRoom(500000, RoomStatus.AVAILABLE);
        stubCode("SALE", DiscountType.PERCENTAGE, percent, null);
        Long comboId = null;
        if (combo > 0) {
            stubCombo(combo);
            comboId = 5L;
        }
        Booking b = create(nights, comboId, "SALE");
        assertThat(b.getDiscountAmount()).isEqualByComparingTo(new BigDecimal(expectedDiscount));
        assertThat(b.getTotalAmount()).isEqualByComparingTo(new BigDecimal(expectedTotal));
    }

    @TcSteps("Mock phòng 200.000/đêm, mã FIXED_AMOUNT x VND, gọi createBooking() N đêm")
    @ParameterizedTest(name = "Giảm giá số tiền cố định {0} VND trên {1} đêm ¦ FIXED_AMOUNT {0}, tạm tính {2} ¦ discountAmount = {3} (không vượt tạm tính), totalAmount = {4}")
    @CsvSource({
            "50000,1,200000,50000,150000", "100000,1,200000,100000,100000", "200000,1,200000,200000,0", "500000,1,200000,200000,0",
            "50000,3,600000,50000,550000", "100000,3,600000,100000,500000", "500000,3,600000,500000,100000", "5000000,3,600000,600000,0",
            "50000,7,1400000,50000,1350000", "100000,7,1400000,100000,1300000", "1000000,7,1400000,1000000,400000", "5000000,7,1400000,1400000,0"})
    void discount_fixedAmountCappedAtSubtotal(long value, int nights, long subtotal, long expectedDiscount, long expectedTotal) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        stubCode("FIX", DiscountType.FIXED_AMOUNT, String.valueOf(value), null);
        Booking b = create(nights, null, "FIX");
        assertThat(b.getDiscountAmount()).isEqualByComparingTo(bd(expectedDiscount));
        assertThat(b.getTotalAmount()).isEqualByComparingTo(bd(expectedTotal));
        assertThat(b.getTotalAmount().signum()).isGreaterThanOrEqualTo(0);
    }

    @TcSteps("Mock mã giảm giá áp dụng cho loại khách A, khách đặt có loại B, gọi createBooking() với mã đó")
    @ParameterizedTest(name = "Mã dành cho [{0}] - khách loại {1} ¦ applicableCustomerType={0}, customerType={1} ¦ Được áp dụng = {2}")
    @CsvSource({
            "ALL,NEW,true", "ALL,REGULAR,true", "ALL,VIP,true",
            "NEW,NEW,true", "NEW,REGULAR,false", "NEW,VIP,false",
            "REGULAR,NEW,false", "REGULAR,REGULAR,true", "REGULAR,VIP,false",
            "VIP,NEW,false", "VIP,REGULAR,false", "VIP,VIP,true"})
    void discount_applicableCustomerType(String applicable, CustomerType customerType, boolean allowed) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        stubCode("KM", DiscountType.PERCENTAGE, "10", applicable.equals("ALL") ? null : CustomerType.valueOf(applicable));
        customer.setCustomerType(customerType);
        if (allowed) {
            assertThat(create(1, null, "KM").getDiscountAmount()).isEqualByComparingTo(bd(20000));
        } else {
            assertThatThrownBy(() -> create(1, null, "KM"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("không áp dụng cho loại khách hàng");
        }
    }

    @TcSteps("Mock mã WELCOME10 đang hoạt động, khách nhập mã với chữ thường/khoảng trắng, gọi createBooking()")
    @ParameterizedTest(name = "Chuẩn hóa mã giảm giá khách nhập ¦ discountCode=\"{0}\" ¦ Được hiểu là WELCOME10 và giảm 10%")
    @ValueSource(strings = {"WELCOME10", "welcome10", "Welcome10", "  WELCOME10  ", "wElCoMe10 "})
    void discount_codeNormalized(String input) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        stubCode("WELCOME10", DiscountType.PERCENTAGE, "10", null);
        assertThat(create(1, null, input).getDiscountAmount()).isEqualByComparingTo(bd(20000));
    }

    @TcSteps("Repository không tìm thấy mã đang hoạt động, gọi createBooking() với mã đó")
    @ParameterizedTest(name = "Mã giảm giá không tồn tại / đã tắt ¦ discountCode=\"{0}\" ¦ Ném BusinessException \"không tồn tại hoặc đã ngừng áp dụng\"")
    @ValueSource(strings = {"KHONGCO", "NEWBIE2026", "HETHAN", "123"})
    void discount_unknownOrInactive_throws(String code) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        when(discountCodeRepository.findByCodeAndActiveTrue(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> create(1, null, code))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tồn tại hoặc đã ngừng áp dụng");
    }

    @TcSteps("Gọi createBooking() với mã giảm giá rỗng")
    @ParameterizedTest(name = "Không nhập mã giảm giá ¦ discountCode=[{0}] ¦ Không tra cứu mã, discountAmount = 0")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void discount_blankCode_ignored(String code) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        Booking b = create(2, null, code);
        assertThat(b.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(discountCodeRepository, never()).findByCodeAndActiveTrue(any());
    }

    // ===================== Kiểm tra ngày, thông tin khách =====================

    @TcSteps("Gọi createBooking() với ngày nhận = hôm nay + a, ngày trả = hôm nay + b")
    @ParameterizedTest(name = "Ngày trả không sau ngày nhận ¦ checkIn=hôm nay+{0}, checkOut=hôm nay+{1} ¦ Ném BusinessException \"Ngày trả phòng phải lớn hơn ngày nhận phòng\"")
    @CsvSource({"3,3", "5,4", "10,1", "30,29"})
    void dates_checkoutNotAfterCheckin_throws(int in, int out) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(in), LocalDate.now().plusDays(out),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", "an@gmail.com"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ngày trả phòng phải lớn hơn");
    }

    @TcSteps("Gọi createBooking() với ngày nhận phòng trong quá khứ")
    @ParameterizedTest(name = "Ngày nhận phòng ở quá khứ ¦ checkIn = hôm nay - {0} ngày ¦ Ném BusinessException \"không được ở trong quá khứ\"")
    @ValueSource(ints = {1, 2, 7, 30, 365})
    void dates_checkinInPast_throws(int daysAgo) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        LocalDate in = LocalDate.now().minusDays(daysAgo);
        assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, in, LocalDate.now().plusDays(1),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", "an@gmail.com"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("quá khứ");
    }

    @TcSteps("Gọi createBooking() với ngày nhận phòng là hôm nay")
    @ParameterizedTest(name = "Đặt phòng nhận ngay trong ngày ¦ checkIn = hôm nay, {0} đêm ¦ Tạo đơn thành công, trạng thái PENDING")
    @ValueSource(ints = {1, 2})
    void dates_checkinToday_allowed(int nights) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        Booking b = bookingService.createBooking(customer, 10L, LocalDate.now(), LocalDate.now().plusDays(nights),
                LocalTime.of(23, 30), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", "an@gmail.com");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @TcSteps("Gọi createBooking() thiếu ngày nhận hoặc ngày trả")
    @ParameterizedTest(name = "Thiếu ngày lưu trú ¦ thiếu {0} ¦ Ném BusinessException \"Vui lòng chọn đầy đủ ngày\"")
    @ValueSource(strings = {"checkInDate", "checkOutDate", "cả hai ngày"})
    void dates_missing_throws(String which) {
        LocalDate in = which.equals("checkOutDate") ? LocalDate.now().plusDays(1) : null;
        LocalDate out = which.equals("checkInDate") ? LocalDate.now().plusDays(2) : null;
        assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, in, out,
                LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", "an@gmail.com"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đầy đủ ngày");
    }

    @TcSteps("Gọi createBooking() thiếu giờ nhận hoặc giờ trả phòng")
    @ParameterizedTest(name = "Thiếu giờ nhận/trả phòng ¦ thiếu {0} ¦ Ném BusinessException \"Vui lòng chọn đầy đủ giờ\"")
    @ValueSource(strings = {"checkInTime", "checkOutTime"})
    void times_missing_throws(String which) {
        LocalTime inT = which.equals("checkInTime") ? null : LocalTime.of(14, 0);
        LocalTime outT = which.equals("checkOutTime") ? null : LocalTime.of(12, 0);
        assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                inT, outT, 1, null, null, "An", "0912345678", "an@gmail.com"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đầy đủ giờ");
    }

    @TcSteps("Gọi createBooking() với thông tin người nhận phòng bị thiếu")
    @ParameterizedTest(name = "Thiếu thông tin người nhận phòng - {0} ¦ {0}=[{1}] ¦ Ném BusinessException, không lưu đơn")
    @CsvSource(value = {"guestName,NULL", "guestName,''", "guestName,'   '", "guestPhone,NULL", "guestPhone,''", "guestPhone,'  '",
            "guestEmail,NULL", "guestEmail,''", "guestEmail,'  '"}, nullValues = "NULL")
    void guestInfo_missing_throws(String field, String value) {
        String name = field.equals("guestName") ? value : "An";
        String phone = field.equals("guestPhone") ? value : "0912345678";
        String email = field.equals("guestEmail") ? value : "an@gmail.com";
        assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, name, phone, email))
                .isInstanceOf(BusinessException.class);
        verify(bookingRepository, never()).save(any());
    }

    @TcSteps("Gọi createBooking() với email người nhận phòng theo bộ dữ liệu (kiểm tra regex ở tầng service)")
    @ParameterizedTest(name = "Email người nhận phòng ở tầng service ¦ guestEmail=\"{0}\" ¦ Hợp lệ = {1}")
    @CsvSource({"an@gmail.com,true", "khach.vang.lai@hotel.com.vn,true", "a@b.co,true", "'  an@gmail.com  ',true",
            "abc,false", "a@b,false", "a b@c.com,false", "@c.com,false", "a@.com,false", "a@@c.com,false"})
    void guestEmail_regex(String email, boolean valid) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        if (valid) {
            assertThat(bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                    LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", email).getGuestEmail())
                    .isEqualTo(email.trim());
        } else {
            assertThatThrownBy(() -> bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                    LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "An", "0912345678", email))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("hop le");
        }
    }

    @TcSteps("Mock phòng ở trạng thái S, gọi createBooking()")
    @ParameterizedTest(name = "Đặt phòng khi phòng ở trạng thái {0} ¦ room.status={0} ¦ Chỉ trạng thái MAINTENANCE bị chặn")
    @EnumSource(RoomStatus.class)
    void roomStatus_onlyMaintenanceBlocked(RoomStatus status) {
        stubRoom(200000, status);
        if (status == RoomStatus.MAINTENANCE) {
            assertThatThrownBy(() -> create(1, null, null)).isInstanceOf(BusinessException.class).hasMessageContaining("bảo trì");
        } else {
            assertThat(create(1, null, null).getStatus()).isEqualTo(BookingStatus.PENDING);
        }
    }

    @TcSteps("Gọi createBooking() với số khách theo bộ dữ liệu")
    @ParameterizedTest(name = "Số khách mặc định khi không hợp lệ ¦ guests={0} ¦ numberOfGuests lưu = {1}")
    @CsvSource(value = {"NULL,1", "0,1", "-1,1", "1,1", "2,2", "3,3"}, nullValues = "NULL")
    void guests_defaultToOne(Integer guests, int expected) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        Booking b = bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), guests, null, null, "An", "0912345678", "an@gmail.com");
        assertThat(b.getNumberOfGuests()).isEqualTo(expected);
    }

    @TcSteps("Mock existsOverlappingBooking=true, gọi createBooking() với số đêm N")
    @ParameterizedTest(name = "Phòng vừa bị người khác đặt trùng lịch ¦ trùng lịch, {0} đêm ¦ Ném BusinessException \"vừa được người khác đặt\", không lưu, không gửi email")
    @ValueSource(ints = {1, 3, 7})
    void overlap_throwsAndDoesNotSave(int nights) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        when(bookingRepository.existsOverlappingBooking(anyLong(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> create(nights, null, null)).isInstanceOf(BusinessException.class).hasMessageContaining("vừa được người khác đặt");
        verify(bookingRepository, never()).save(any());
        verify(emailService, never()).sendBookingConfirmation(any());
    }

    @TcSteps("Gọi createBooking() thành công, kiểm tra trạng thái đơn và việc gửi email")
    @ParameterizedTest(name = "Tạo đơn thành công - kiểm tra {0} ¦ dữ liệu hợp lệ ¦ {0} đúng như thiết kế")
    @ValueSource(strings = {"trạng thái PENDING", "cờ đơn mới = true", "gửi email xác nhận 1 lần", "lưu đúng họ tên người nhận (đã trim)"})
    void create_success_sideEffects(String aspect) {
        stubRoom(200000, RoomStatus.AVAILABLE);
        Booking b = bookingService.createBooking(customer, 10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 1, null, null, "  Nguyễn Văn An  ", "0912345678", "an@gmail.com");
        switch (aspect) {
            case "trạng thái PENDING" -> assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
            case "cờ đơn mới = true" -> assertThat(b.isNewBooking()).isTrue();
            case "gửi email xác nhận 1 lần" -> verify(emailService, times(1)).sendBookingConfirmation(b);
            default -> assertThat(b.getGuestName()).isEqualTo("Nguyễn Văn An");
        }
    }

    // ===================== Máy trạng thái đơn đặt phòng =====================

    private Booking stubBooking(BookingStatus status) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(bd(200000)).status(RoomStatus.BOOKED).build();
        Booking b = Booking.builder().id(99L).customer(customer).room(room)
                .checkInDate(LocalDate.now().plusDays(5)).checkOutDate(LocalDate.now().plusDays(7))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .totalAmount(bd(400000)).discountAmount(BigDecimal.ZERO).status(status).build();
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(b));
        return b;
    }

    @TcSteps("Mock đơn ở trạng thái S, nhân viên gọi confirmBooking()")
    @ParameterizedTest(name = "Xác nhận đơn khi đơn đang {0} ¦ status={0} ¦ Chỉ PENDING → CONFIRMED, trạng thái khác báo lỗi")
    @EnumSource(BookingStatus.class)
    void stateMachine_confirm(BookingStatus status) {
        Booking b = stubBooking(status);
        if (status == BookingStatus.PENDING) {
            assertThat(bookingService.confirmBooking(99L).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verify(emailService).sendBookingConfirmation(b);
        } else {
            assertThatThrownBy(() -> bookingService.confirmBooking(99L)).isInstanceOf(BusinessException.class);
            assertThat(b.getStatus()).isEqualTo(status);
        }
    }

    @TcSteps("Mock đơn ở trạng thái S, nhân viên gọi checkIn()")
    @ParameterizedTest(name = "Check-in khi đơn đang {0} ¦ status={0} ¦ Chỉ CONFIRMED → CHECKED_IN và phòng → OCCUPIED")
    @EnumSource(BookingStatus.class)
    void stateMachine_checkIn(BookingStatus status) {
        Booking b = stubBooking(status);
        if (status == BookingStatus.CONFIRMED) {
            bookingService.checkIn(99L);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
            assertThat(b.getRoom().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        } else {
            assertThatThrownBy(() -> bookingService.checkIn(99L)).isInstanceOf(BusinessException.class);
            assertThat(b.getRoom().getStatus()).isEqualTo(RoomStatus.BOOKED);
        }
    }

    @TcSteps("Mock đơn ở trạng thái S, nhân viên gọi checkOut()")
    @ParameterizedTest(name = "Check-out khi đơn đang {0} ¦ status={0} ¦ Chỉ CHECKED_IN → CHECKED_OUT và phòng → NOT_READY")
    @EnumSource(BookingStatus.class)
    void stateMachine_checkOut(BookingStatus status) {
        Booking b = stubBooking(status);
        if (status == BookingStatus.CHECKED_IN) {
            bookingService.checkOut(99L);
            assertThat(b.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
            assertThat(b.getRoom().getStatus()).isEqualTo(RoomStatus.NOT_READY);
        } else {
            assertThatThrownBy(() -> bookingService.checkOut(99L)).isInstanceOf(BusinessException.class);
        }
    }

    @TcSteps("Mock đơn ở trạng thái S, nhân viên gọi cancelBookingByStaff()")
    @ParameterizedTest(name = "Nhân viên hủy đơn khi đơn đang {0} ¦ status={0} ¦ Chỉ PENDING/CONFIRMED được hủy")
    @EnumSource(BookingStatus.class)
    void stateMachine_cancelByStaff(BookingStatus status) {
        Booking b = stubBooking(status);
        boolean allowed = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
        if (allowed) {
            assertThat(bookingService.cancelBookingByStaff(99L).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        } else {
            assertThatThrownBy(() -> bookingService.cancelBookingByStaff(99L)).isInstanceOf(BusinessException.class);
            assertThat(b.getStatus()).isEqualTo(status);
        }
    }

    @TcSteps("Mock đơn của chính khách ở trạng thái S, khách gọi cancelBooking()")
    @ParameterizedTest(name = "Khách tự hủy đơn khi đơn đang {0} ¦ status={0} ¦ Chỉ PENDING/CONFIRMED được hủy")
    @EnumSource(BookingStatus.class)
    void stateMachine_cancelByCustomer(BookingStatus status) {
        Booking b = stubBooking(status);
        boolean allowed = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
        if (allowed) {
            assertThat(bookingService.cancelBooking(99L, 1L).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        } else {
            assertThatThrownBy(() -> bookingService.cancelBooking(99L, 1L)).isInstanceOf(BusinessException.class);
            assertThat(b.getStatus()).isEqualTo(status);
        }
    }

    @TcSteps("Mock đơn thuộc khách id=1, khách khác (id=X) thao tác trên đơn")
    @ParameterizedTest(name = "Khách khác thao tác trên đơn không phải của mình - {0} ¦ customerId=2 ¦ Ném BusinessException \"không có quyền\"")
    @ValueSource(strings = {"xem đơn", "hủy đơn", "đổi giờ", "đổi combo"})
    void ownership_enforced(String action) {
        stubBooking(BookingStatus.PENDING);
        Runnable call = switch (action) {
            case "xem đơn" -> () -> bookingService.findByIdForCustomer(99L, 2L);
            case "hủy đơn" -> () -> bookingService.cancelBooking(99L, 2L);
            case "đổi giờ" -> () -> bookingService.updateTimes(99L, 2L, LocalTime.of(15, 0), LocalTime.of(11, 0));
            default -> () -> bookingService.updateCombo(99L, 2L, null);
        };
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).hasMessageContaining("không có quyền");
    }

    // ===================== Quy tắc 6 tiếng (canModify) =====================

    private Booking bookingAt(BookingStatus status, long minutesUntilCheckIn) {
        LocalDateTime checkIn = LocalDateTime.now().plusMinutes(minutesUntilCheckIn);
        return Booking.builder().id(99L).customer(customer)
                .room(Room.builder().id(10L).roomNumber("101").roomType(roomType).price(bd(200000)).status(RoomStatus.BOOKED).build())
                .checkInDate(checkIn.toLocalDate()).checkInTime(checkIn.toLocalTime())
                .checkOutDate(checkIn.toLocalDate().plusDays(2)).checkOutTime(LocalTime.of(12, 0))
                .totalAmount(bd(400000)).discountAmount(BigDecimal.ZERO).status(status).build();
    }

    @TcSteps("Tạo đơn trạng thái S có giờ nhận phòng cách hiện tại M phút, gọi canModify()")
    @ParameterizedTest(name = "Cho phép sửa đơn {0}, còn {1} phút tới giờ nhận phòng ¦ status={0}, còn {1} phút ¦ canModify = {2}")
    @CsvSource({
            "PENDING,-60,false", "PENDING,60,false", "PENDING,300,false", "PENDING,355,false", "PENDING,365,true", "PENDING,1440,true", "PENDING,4320,true",
            "CONFIRMED,-60,false", "CONFIRMED,60,false", "CONFIRMED,300,false", "CONFIRMED,355,false", "CONFIRMED,365,true", "CONFIRMED,1440,true", "CONFIRMED,4320,true",
            "CHECKED_IN,365,false", "CHECKED_IN,1440,false", "CHECKED_IN,4320,false",
            "CHECKED_OUT,365,false", "CHECKED_OUT,1440,false", "CHECKED_OUT,4320,false",
            "CANCELLED,365,false", "CANCELLED,1440,false", "CANCELLED,4320,false"})
    void canModify_sixHourRule(BookingStatus status, long minutes, boolean expected) {
        assertThat(bookingService.canModify(bookingAt(status, minutes))).isEqualTo(expected);
    }

    @TcSteps("Mock đơn PENDING còn M phút tới giờ nhận, khách gọi updateTimes() đổi giờ 15:00/11:00")
    @ParameterizedTest(name = "Khách đổi giờ nhận/trả, còn {0} phút ¦ status=PENDING, còn {0} phút ¦ Thành công = {1}; thành công thì bật cờ \"Khách đổi giờ nhận phòng\"")
    @CsvSource({"60,false", "300,false", "420,true", "1440,true", "2880,true"})
    void updateTimes_respectsCutoff(long minutes, boolean ok) {
        Booking b = bookingAt(BookingStatus.PENDING, minutes);
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(b));
        if (ok) {
            bookingService.updateTimes(99L, 1L, LocalTime.of(15, 0), LocalTime.of(11, 0));
            assertThat(b.getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
            assertThat(b.isCheckInTimeChanged()).isTrue();
        } else {
            assertThatThrownBy(() -> bookingService.updateTimes(99L, 1L, LocalTime.of(15, 0), LocalTime.of(11, 0)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("6 tiếng");
        }
    }

    // ===================== Đổi combo - tính lại tiền =====================

    @TcSteps("Mock đơn PENDING 500.000/đêm × N đêm (còn 3 ngày), mã giảm D, khách gọi updateCombo() sang combo C")
    @ParameterizedTest(name = "Đổi combo: {0} đêm, combo mới {1}, mã {2} ¦ nights={0}, combo={1}, discount={2} ¦ discountAmount = {3}, totalAmount = {4}")
    @CsvSource({
            "1,0,NONE,0,500000", "1,199000,NONE,0,699000", "1,399000,NONE,0,899000",
            "1,0,P10,50000,450000", "1,199000,P10,69900,629100", "1,399000,P10,89900,809100",
            "1,0,F100K,100000,400000", "1,199000,F100K,100000,599000", "1,399000,F100K,100000,799000",
            "3,0,NONE,0,1500000", "3,199000,NONE,0,1699000", "3,399000,NONE,0,1899000",
            "3,0,P10,150000,1350000", "3,199000,P10,169900,1529100", "3,399000,P10,189900,1709100",
            "3,0,F100K,100000,1400000", "3,199000,F100K,100000,1599000", "3,399000,F100K,100000,1799000"})
    void updateCombo_recalculates(int nights, long combo, String discount, String expectedDiscount, long expectedTotal) {
        LocalDateTime in = LocalDateTime.now().plusDays(3);
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(bd(500000)).status(RoomStatus.BOOKED).build();
        DiscountCode dc = switch (discount) {
            case "P10" -> DiscountCode.builder().code("P10").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).active(true).build();
            case "F100K" -> DiscountCode.builder().code("F100K").discountType(DiscountType.FIXED_AMOUNT).discountValue(bd(100000)).active(true).build();
            default -> null;
        };
        Booking b = Booking.builder().id(99L).customer(customer).room(room)
                .checkInDate(in.toLocalDate()).checkOutDate(in.toLocalDate().plusDays(nights))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0))
                .discountCode(dc).totalAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO).status(BookingStatus.PENDING).build();
        when(bookingRepository.findById(99L)).thenReturn(Optional.of(b));
        Long comboId = null;
        if (combo > 0) {
            stubCombo(combo);
            comboId = 5L;
        }
        bookingService.updateCombo(99L, 1L, comboId);
        assertThat(b.getDiscountAmount()).isEqualByComparingTo(new BigDecimal(expectedDiscount));
        assertThat(b.getTotalAmount()).isEqualByComparingTo(bd(expectedTotal));
    }

    // ===================== Thống kê dashboard =====================

    private Booking statBooking(BookingStatus status, String typeName, long amount, LocalDateTime createdAt, String guest) {
        RoomType rt = RoomType.builder().id((long) typeName.hashCode()).name(typeName).basePrice(bd(1)).maxGuests(2).build();
        Booking b = Booking.builder().room(Room.builder().roomNumber("X").roomType(rt).price(bd(1)).status(RoomStatus.AVAILABLE).build())
                .customer(customer).guestName(guest).guestPhone("0912345678").guestEmail(guest.toLowerCase() + "@mail.com")
                .totalAmount(bd(amount)).status(status).build();
        b.setCreatedAt(createdAt);
        return b;
    }

    @TcSteps("Gọi getRevenueTrend(from, to) với khoảng D ngày, đếm số nhãn trên trục thời gian")
    @ParameterizedTest(name = "Biểu đồ doanh thu theo thời gian - khoảng {0} ngày ¦ from..to dài {0} ngày ¦ Gom theo {1}, có {2} nhãn")
    @CsvSource({"1,ngày,1", "7,ngày,7", "30,ngày,30", "31,ngày,31"})
    void revenueTrend_groupByDay(int days, String unit, int labels) {
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        LocalDate to = LocalDate.of(2026, 3, 31);
        Map<String, BigDecimal> trend = bookingService.getRevenueTrend(to.minusDays(days - 1), to);
        assertThat(trend).hasSize(labels);
    }

    @TcSteps("Gọi getRevenueTrend(from, to) với khoảng dài hơn 31 ngày")
    @ParameterizedTest(name = "Biểu đồ doanh thu - khoảng từ {0} đến {1} ¦ from={0}, to={1} ¦ Gom theo tháng, có {2} nhãn")
    @CsvSource({"2026-01-01,2026-02-01,2", "2026-01-01,2026-03-31,3", "2026-01-15,2026-06-15,6", "2025-01-01,2025-12-31,12"})
    void revenueTrend_groupByMonth(LocalDate from, LocalDate to, int labels) {
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        assertThat(bookingService.getRevenueTrend(from, to)).hasSize(labels);
    }

    @TcSteps("Mock 3 đơn (một đơn có trạng thái S) cùng loại phòng, gọi getRevenueByRoomType()")
    @ParameterizedTest(name = "Doanh thu theo loại phòng khi có đơn {0} ¦ 2 đơn × 500.000 + 1 đơn {0} 300.000 ¦ Tổng = {1} (đơn hủy không tính)")
    @CsvSource({"PENDING,1300000", "CONFIRMED,1300000", "CHECKED_IN,1300000", "CHECKED_OUT,1300000", "CANCELLED,1000000"})
    void revenueByRoomType_excludesCancelled(BookingStatus status, long expected) {
        LocalDateTime t = LocalDateTime.of(2026, 3, 10, 9, 0);
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                statBooking(BookingStatus.CONFIRMED, "Phòng VIP", 500000, t, "A"),
                statBooking(BookingStatus.CHECKED_OUT, "Phòng VIP", 500000, t, "B"),
                statBooking(status, "Phòng VIP", 300000, t, "C")));
        assertThat(bookingService.getRevenueByRoomType(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).get("Phòng VIP"))
                .isEqualByComparingTo(bd(expected));
    }

    @TcSteps("Mock đơn tạo ngày X, gọi getBookingStatusCounts(01/03/2026, 31/03/2026)")
    @ParameterizedTest(name = "Đếm đơn theo trạng thái - đơn tạo ngày {0} ¦ createdAt={0}, lọc 01/03..31/03/2026 ¦ Được đếm = {1}")
    @CsvSource({"2026-02-28,false", "2026-03-01,true", "2026-03-15,true", "2026-03-31,true", "2026-04-01,false"})
    void statusCounts_rangeInclusive(LocalDate created, boolean counted) {
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                statBooking(BookingStatus.PENDING, "Phòng thường", 200000, created.atTime(10, 0), "A")));
        Map<BookingStatus, Long> counts = bookingService.getBookingStatusCounts(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(counts).hasSize(5);
        assertThat(counts.get(BookingStatus.PENDING)).isEqualTo(counted ? 1L : 0L);
    }

    // ===================== Tìm kiếm / lọc danh sách đơn =====================

    @TcSteps("Mock 3 đơn (Nguyễn Văn An/0912345678, Trần Thị Hoa/0768879223, Lê Minh Tuấn/0363175675), gọi findAllBookings(null, q)")
    @ParameterizedTest(name = "Tìm đơn theo từ khóa ¦ q=\"{0}\" ¦ Tìm thấy {1} đơn")
    @CsvSource({"an,2", "NGUYỄN,1", "trần thị,1", "0912,1", "0363175675,1", "hoa@mail.com,1", "@mail.com,3", "khongco,0", "'  tuấn  ',1", "'',3"})
    void search_byNamePhoneEmail(String q, int expected) {
        List<Booking> all = new ArrayList<>();
        Booking a = statBooking(BookingStatus.PENDING, "T", 1, LocalDateTime.now(), "An");
        a.setGuestName("Nguyễn Văn An");
        a.setGuestPhone("0912345678");
        a.setGuestEmail("an@mail.com");
        Booking h = statBooking(BookingStatus.CONFIRMED, "T", 1, LocalDateTime.now(), "Hoa");
        h.setGuestName("Trần Thị Hoa");
        h.setGuestPhone("0768879223");
        h.setGuestEmail("hoa@mail.com");
        Booking t = statBooking(BookingStatus.CANCELLED, "T", 1, LocalDateTime.now(), "Tuan");
        t.setGuestName("Lê Minh Tuấn");
        t.setGuestPhone("0363175675");
        t.setGuestEmail("tuan@mail.com");
        all.add(a);
        all.add(h);
        all.add(t);
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(all);
        assertThat(bookingService.findAllBookings(null, q)).hasSize(expected);
    }

    @TcSteps("Mock 5 đơn, mỗi trạng thái 1 đơn, gọi findAllBookings(status)")
    @ParameterizedTest(name = "Lọc danh sách đơn theo trạng thái {0} ¦ statusFilter={0} ¦ Trả về đúng 1 đơn có trạng thái {0}")
    @EnumSource(BookingStatus.class)
    void filter_byStatus(BookingStatus status) {
        List<Booking> all = new ArrayList<>();
        for (BookingStatus s : BookingStatus.values()) {
            all.add(statBooking(s, "T", 1, LocalDateTime.now(), s.name()));
        }
        when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(all);
        List<Booking> result = bookingService.findAllBookings(status);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(status);
    }

    @TcSteps("Mock đơn có cờ đơn mới / đổi giờ, nhân viên mở xem chi tiết (findByIdAndMarkSeenByStaff)")
    @ParameterizedTest(name = "Nhân viên mở xem đơn - cờ đơn mới={0}, cờ đổi giờ={1} ¦ newBooking={0}, checkInTimeChanged={1} ¦ Cả hai cờ về false; chỉ lưu DB khi có cờ bật")
    @CsvSource({"true,false", "false,true", "true,true", "false,false"})
    void markSeen_clearsFlags(boolean isNew, boolean changed) {
        Booking b = stubBooking(BookingStatus.PENDING);
        b.setNewBooking(isNew);
        b.setCheckInTimeChanged(changed);
        bookingService.findByIdAndMarkSeenByStaff(99L);
        assertThat(b.isNewBooking()).isFalse();
        assertThat(b.isCheckInTimeChanged()).isFalse();
        verify(bookingRepository, times(isNew || changed ? 1 : 0)).save(b);
    }
}
