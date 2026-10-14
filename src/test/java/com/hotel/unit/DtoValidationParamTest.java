package com.hotel.unit;

import com.hotel.dto.*;
import com.hotel.entity.CustomerType;
import com.hotel.entity.DiscountType;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TcSuite(level = "UT", module = "Kiểm tra dữ liệu đầu vào (DTO Validation)")
class DtoValidationParamTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static <T> boolean hasViolation(T bean, String field) {
        Set<ConstraintViolation<T>> v = validator.validate(bean);
        return v.stream().anyMatch(c -> c.getPropertyPath().toString().equals(field));
    }

    private static RegisterRequest validRegister() {
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Nguyễn Quốc Hoàn");
        r.setEmail("hoan@gmail.com");
        r.setPhoneNumber("0337196255");
        r.setPassword("matkhau123");
        r.setConfirmPassword("matkhau123");
        return r;
    }

    private static BookingConfirmRequest validConfirm() {
        BookingConfirmRequest r = new BookingConfirmRequest();
        r.setGuestName("Nguyễn Văn An");
        r.setGuestEmail("an@gmail.com");
        r.setGuestPhone("0912345678");
        r.setCheckInTime(LocalTime.of(14, 0));
        r.setCheckOutTime(LocalTime.of(12, 0));
        return r;
    }

    // ===================== RegisterRequest =====================

    @TcSteps("Tạo RegisterRequest hợp lệ, gán phoneNumber theo bộ dữ liệu, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - số điện thoại hợp lệ được chấp nhận ¦ phoneNumber=\"{0}\" ¦ Không có vi phạm trên trường phoneNumber")
    @ValueSource(strings = {"0337196255", "0901234567", "0912345678", "0987654321", "0353000111",
            "0700000000", "0799999999", "0811223344", "0868686868", "0000000000"})
    void register_validPhone_accepted(String phone) {
        RegisterRequest r = validRegister();
        r.setPhoneNumber(phone);
        assertThat(hasViolation(r, "phoneNumber")).isFalse();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, gán phoneNumber sai định dạng, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - số điện thoại sai định dạng bị từ chối ¦ phoneNumber=\"{0}\" ¦ Có vi phạm @Pattern/@NotBlank trên phoneNumber")
    @ValueSource(strings = {"123", "090123456", "09012345678", "1901234567", "+84901234567", "0901 234 567",
            "090-123-4567", "abcdefghij", "09o1234567", " 0901234567", "0901234567 ", "84901234567", "090123456a", "０９０１２３４５６７"})
    void register_invalidPhone_rejected(String phone) {
        RegisterRequest r = validRegister();
        r.setPhoneNumber(phone);
        assertThat(hasViolation(r, "phoneNumber")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, để trống phoneNumber, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - số điện thoại bỏ trống bị từ chối ¦ phoneNumber=[{0}] ¦ Có vi phạm @NotBlank trên phoneNumber")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void register_blankPhone_rejected(String phone) {
        RegisterRequest r = validRegister();
        r.setPhoneNumber(phone);
        assertThat(hasViolation(r, "phoneNumber")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, gán email theo bộ dữ liệu, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - email hợp lệ được chấp nhận ¦ email=\"{0}\" ¦ Không có vi phạm trên trường email")
    @ValueSource(strings = {"hoan@gmail.com", "nguyen.van.an@yahoo.com", "a1@mail.vn", "khach_hang@hotel.com.vn",
            "staff+test@hotel.com", "ABC@GMAIL.COM", "x@y.io", "ten.ho-123@sub.domain.org"})
    void register_validEmail_accepted(String email) {
        RegisterRequest r = validRegister();
        r.setEmail(email);
        assertThat(hasViolation(r, "email")).isFalse();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, gán email sai định dạng, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - email sai định dạng bị từ chối ¦ email=\"{0}\" ¦ Có vi phạm @Email trên trường email")
    @ValueSource(strings = {"plainaddress", "@gmail.com", "hoan@", "hoan gmail@mail.com", "hoan@@gmail.com",
            "hoan@gmail..com", "hoan@.com", ".hoan@gmail.com", "hoan.@gmail.com", "hoan@gm ail.com"})
    void register_invalidEmail_rejected(String email) {
        RegisterRequest r = validRegister();
        r.setEmail(email);
        assertThat(hasViolation(r, "email")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, để trống email, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - email bỏ trống bị từ chối ¦ email=[{0}] ¦ Có vi phạm @NotBlank trên email")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void register_blankEmail_rejected(String email) {
        RegisterRequest r = validRegister();
        r.setEmail(email);
        assertThat(hasViolation(r, "email")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, gán mật khẩu có độ dài N ký tự, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - mật khẩu dài {0} ký tự (biên dưới 6) ¦ password có {0} ký tự ¦ Hợp lệ = {1}")
    @CsvSource({"1,false", "2,false", "3,false", "4,false", "5,false", "6,true", "7,true", "8,true", "12,true", "20,true", "64,true"})
    void register_passwordLengthBoundary(int length, boolean valid) {
        RegisterRequest r = validRegister();
        r.setPassword("a".repeat(length));
        assertThat(hasViolation(r, "password")).isEqualTo(!valid);
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, để trống mật khẩu, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - mật khẩu bỏ trống bị từ chối ¦ password=[{0}] ¦ Có vi phạm trên password")
    @NullAndEmptySource
    @ValueSource(strings = {"      "})
    void register_blankPassword_rejected(String password) {
        RegisterRequest r = validRegister();
        r.setPassword(password);
        assertThat(hasViolation(r, "password")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, gán họ tên, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - họ tên hợp lệ được chấp nhận ¦ fullName=\"{0}\" ¦ Không có vi phạm trên fullName")
    @ValueSource(strings = {"Nguyễn Quốc Hoàn", "An", "Trần Thị Thu Hà", "Lê Văn Đức Anh Minh", "Đỗ Ân"})
    void register_validFullName_accepted(String name) {
        RegisterRequest r = validRegister();
        r.setFullName(name);
        assertThat(hasViolation(r, "fullName")).isFalse();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, để trống họ tên, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - họ tên bỏ trống bị từ chối ¦ fullName=[{0}] ¦ Có vi phạm @NotBlank trên fullName")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void register_blankFullName_rejected(String name) {
        RegisterRequest r = validRegister();
        r.setFullName(name);
        assertThat(hasViolation(r, "fullName")).isTrue();
    }

    @TcSteps("Tạo RegisterRequest hợp lệ, để trống confirmPassword, gọi Validator.validate()")
    @ParameterizedTest(name = "Đăng ký - xác nhận mật khẩu bỏ trống bị từ chối ¦ confirmPassword=[{0}] ¦ Có vi phạm @NotBlank trên confirmPassword")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void register_blankConfirm_rejected(String confirm) {
        RegisterRequest r = validRegister();
        r.setConfirmPassword(confirm);
        assertThat(hasViolation(r, "confirmPassword")).isTrue();
    }

    // ===================== BookingConfirmRequest =====================

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, gán guestPhone, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - SĐT người nhận phòng hợp lệ ¦ guestPhone=\"{0}\" ¦ Không có vi phạm trên guestPhone")
    @ValueSource(strings = {"0912345678", "0337196255", "0388888888", "0909001122", "0777674593", "0831234567"})
    void confirm_validPhone_accepted(String phone) {
        BookingConfirmRequest r = validConfirm();
        r.setGuestPhone(phone);
        assertThat(hasViolation(r, "guestPhone")).isFalse();
    }

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, gán guestPhone sai định dạng, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - SĐT người nhận phòng sai định dạng ¦ guestPhone=\"{0}\" ¦ Có vi phạm trên guestPhone")
    @ValueSource(strings = {"", "091234567", "09123456789", "1912345678", "+84912345678", "0912 345 678", "số điện thoại", "0912-345-678"})
    void confirm_invalidPhone_rejected(String phone) {
        BookingConfirmRequest r = validConfirm();
        r.setGuestPhone(phone);
        assertThat(hasViolation(r, "guestPhone")).isTrue();
    }

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, gán guestEmail, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - email người nhận phòng ¦ guestEmail=\"{0}\" ¦ Hợp lệ = {1}")
    @CsvSource({"an@gmail.com,true", "khach.vanglai@hotel.vn,true", "a@b.co,true",
            "khongphaiemail,false", "an@,false", "@gmail.com,false", "an gmail@x.com,false", "an@@x.com,false"})
    void confirm_email(String email, boolean valid) {
        BookingConfirmRequest r = validConfirm();
        r.setGuestEmail(email);
        assertThat(hasViolation(r, "guestEmail")).isEqualTo(!valid);
    }

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, để trống họ tên người nhận phòng, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - họ tên người nhận bỏ trống ¦ guestName=[{0}] ¦ Có vi phạm @NotBlank trên guestName")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void confirm_blankName_rejected(String name) {
        BookingConfirmRequest r = validConfirm();
        r.setGuestName(name);
        assertThat(hasViolation(r, "guestName")).isTrue();
    }

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, để null giờ nhận hoặc giờ trả phòng, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - thiếu {0} ¦ {0}=null ¦ Có vi phạm @NotNull trên {0}")
    @ValueSource(strings = {"checkInTime", "checkOutTime"})
    void confirm_missingTime_rejected(String field) {
        BookingConfirmRequest r = validConfirm();
        if (field.equals("checkInTime")) {
            r.setCheckInTime(null);
        } else {
            r.setCheckOutTime(null);
        }
        assertThat(hasViolation(r, field)).isTrue();
    }

    @TcSteps("Tạo BookingConfirmRequest hợp lệ, gán discountCode tùy chọn, gọi Validator.validate()")
    @ParameterizedTest(name = "Xác nhận đặt phòng - mã giảm giá là trường không bắt buộc ¦ discountCode=[{0}] ¦ Không có vi phạm nào")
    @NullAndEmptySource
    @ValueSource(strings = {"WELCOME10"})
    void confirm_discountCodeOptional(String code) {
        BookingConfirmRequest r = validConfirm();
        r.setDiscountCode(code);
        assertThat(validator.validate(r)).isEmpty();
    }

    // ===================== RoomRequest =====================

    @TcSteps("Tạo RoomRequest (số phòng 101, loại phòng 1), gán giá, gọi Validator.validate()")
    @ParameterizedTest(name = "Phòng - giá phòng {0} VND ¦ price={0} ¦ Hợp lệ = {1}")
    @CsvSource({"-100000,false", "-1,false", "0,false", "0.01,true", "1,true", "200000,true", "799000,true", "99999999,true"})
    void room_priceBoundary(String price, boolean valid) {
        RoomRequest r = new RoomRequest();
        r.setRoomNumber("101");
        r.setRoomTypeId(1L);
        r.setPrice(new BigDecimal(price));
        assertThat(hasViolation(r, "price")).isEqualTo(!valid);
    }

    @TcSteps("Tạo RoomRequest hợp lệ, để trống số phòng, gọi Validator.validate()")
    @ParameterizedTest(name = "Phòng - số phòng bỏ trống bị từ chối ¦ roomNumber=[{0}] ¦ Có vi phạm @NotBlank trên roomNumber")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void room_blankNumber_rejected(String number) {
        RoomRequest r = new RoomRequest();
        r.setRoomNumber(number);
        r.setRoomTypeId(1L);
        r.setPrice(BigDecimal.valueOf(200000));
        assertThat(hasViolation(r, "roomNumber")).isTrue();
    }

    @TcSteps("Tạo RoomRequest hợp lệ nhưng không chọn loại phòng, gọi Validator.validate()")
    @ParameterizedTest(name = "Phòng - không chọn loại phòng bị từ chối ¦ roomNumber=\"{0}\", roomTypeId=null ¦ Có vi phạm @NotNull trên roomTypeId")
    @ValueSource(strings = {"101", "VIP-01"})
    void room_missingType_rejected(String number) {
        RoomRequest r = new RoomRequest();
        r.setRoomNumber(number);
        r.setPrice(BigDecimal.valueOf(200000));
        assertThat(hasViolation(r, "roomTypeId")).isTrue();
    }

    // ===================== RoomTypeRequest =====================

    private static RoomTypeRequest validRoomType() {
        RoomTypeRequest r = new RoomTypeRequest();
        r.setName("Phòng VIP");
        r.setBasePrice(BigDecimal.valueOf(500000));
        r.setMaxGuests(2);
        return r;
    }

    @TcSteps("Tạo RoomTypeRequest hợp lệ, gán giá cơ bản, gọi Validator.validate()")
    @ParameterizedTest(name = "Loại phòng - giá cơ bản {0} VND ¦ basePrice={0} ¦ Hợp lệ = {1}")
    @CsvSource({"-1,false", "0,false", "0.5,true", "100000,true", "1500000,true"})
    void roomType_basePriceBoundary(String price, boolean valid) {
        RoomTypeRequest r = validRoomType();
        r.setBasePrice(new BigDecimal(price));
        assertThat(hasViolation(r, "basePrice")).isEqualTo(!valid);
    }

    @TcSteps("Tạo RoomTypeRequest hợp lệ, gán sức chứa, gọi Validator.validate()")
    @ParameterizedTest(name = "Loại phòng - sức chứa {0} người ¦ maxGuests={0} ¦ Hợp lệ = {1}")
    @CsvSource({"-1,false", "0,false", "1,true", "2,true", "4,true", "10,true"})
    void roomType_maxGuestsBoundary(int guests, boolean valid) {
        RoomTypeRequest r = validRoomType();
        r.setMaxGuests(guests);
        assertThat(hasViolation(r, "maxGuests")).isEqualTo(!valid);
    }

    @TcSteps("Tạo RoomTypeRequest hợp lệ, để trống tên loại phòng, gọi Validator.validate()")
    @ParameterizedTest(name = "Loại phòng - tên bỏ trống bị từ chối ¦ name=[{0}] ¦ Có vi phạm @NotBlank trên name")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void roomType_blankName_rejected(String name) {
        RoomTypeRequest r = validRoomType();
        r.setName(name);
        assertThat(hasViolation(r, "name")).isTrue();
    }

    // ===================== ComboRequest =====================

    @TcSteps("Tạo ComboRequest (tên Lẩu), gán giá combo, gọi Validator.validate()")
    @ParameterizedTest(name = "Combo - giá combo {0} VND ¦ price={0} ¦ Hợp lệ = {1}")
    @CsvSource({"-1,false", "-399000,false", "0,true", "199000,true", "399000,true"})
    void combo_priceBoundary(String price, boolean valid) {
        ComboRequest r = new ComboRequest();
        r.setName("Lẩu");
        r.setPrice(new BigDecimal(price));
        assertThat(hasViolation(r, "price")).isEqualTo(!valid);
    }

    @TcSteps("Tạo ComboRequest hợp lệ, gán số người áp dụng tối đa, gọi Validator.validate()")
    @ParameterizedTest(name = "Combo - số người áp dụng {0} ¦ maxGuests={0} ¦ Hợp lệ = {1}")
    @CsvSource({"-2,false", "0,false", "1,true", "2,true", "6,true"})
    void combo_maxGuestsBoundary(int guests, boolean valid) {
        ComboRequest r = new ComboRequest();
        r.setName("Nướng BBQ");
        r.setPrice(BigDecimal.valueOf(299000));
        r.setMaxGuests(guests);
        assertThat(hasViolation(r, "maxGuests")).isEqualTo(!valid);
    }

    @TcSteps("Tạo ComboRequest hợp lệ, để trống số người áp dụng, gọi Validator.validate()")
    @ParameterizedTest(name = "Combo - bỏ trống số người áp dụng (không giới hạn) ¦ name=\"{0}\", maxGuests=null ¦ Không có vi phạm nào")
    @ValueSource(strings = {"Lẩu", "Phòng thường"})
    void combo_nullMaxGuests_accepted(String name) {
        ComboRequest r = new ComboRequest();
        r.setName(name);
        r.setPrice(BigDecimal.ZERO);
        assertThat(validator.validate(r)).isEmpty();
    }

    @TcSteps("Tạo ComboRequest hợp lệ, để trống tên combo, gọi Validator.validate()")
    @ParameterizedTest(name = "Combo - tên combo bỏ trống bị từ chối ¦ name=[{0}] ¦ Có vi phạm @NotBlank trên name")
    @NullAndEmptySource
    void combo_blankName_rejected(String name) {
        ComboRequest r = new ComboRequest();
        r.setName(name);
        r.setPrice(BigDecimal.ONE);
        assertThat(hasViolation(r, "name")).isTrue();
    }

    // ===================== DiscountCodeRequest =====================

    @TcSteps("Tạo DiscountCodeRequest (mã TEST), gán loại giảm và giá trị giảm, gọi Validator.validate()")
    @ParameterizedTest(name = "Mã giảm giá - loại {0}, giá trị {1} ¦ discountType={0}, discountValue={1} ¦ Hợp lệ = {2}")
    @CsvSource({
            "PERCENTAGE,0,false", "PERCENTAGE,0.01,true", "PERCENTAGE,1,true", "PERCENTAGE,10,true", "PERCENTAGE,50,true",
            "PERCENTAGE,99.99,true", "PERCENTAGE,100,true", "PERCENTAGE,100.01,false", "PERCENTAGE,150,false", "PERCENTAGE,-10,false",
            "FIXED_AMOUNT,0,false", "FIXED_AMOUNT,-50000,false", "FIXED_AMOUNT,50000,true", "FIXED_AMOUNT,150,true", "FIXED_AMOUNT,5000000,true"})
    void discount_valueRules(DiscountType type, String value, boolean valid) {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("TEST");
        r.setDiscountType(type);
        r.setDiscountValue(new BigDecimal(value));
        assertThat(validator.validate(r).isEmpty()).isEqualTo(valid);
    }

    @TcSteps("Tạo DiscountCodeRequest hợp lệ, gán đối tượng khách hàng áp dụng, gọi Validator.validate()")
    @ParameterizedTest(name = "Mã giảm giá - áp dụng cho loại khách {0} ¦ applicableCustomerType={0} ¦ Không có vi phạm (trường không bắt buộc)")
    @ValueSource(strings = {"NEW", "REGULAR", "VIP", "ALL"})
    void discount_applicableTypeOptional(String type) {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode("WELCOME10");
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.TEN);
        r.setApplicableCustomerType(type.equals("ALL") ? null : CustomerType.valueOf(type));
        assertThat(validator.validate(r)).isEmpty();
    }

    @TcSteps("Tạo DiscountCodeRequest thiếu trường bắt buộc, gọi Validator.validate()")
    @ParameterizedTest(name = "Mã giảm giá - thiếu trường bắt buộc {0} ¦ {0} để trống ¦ Có vi phạm trên {0}")
    @ValueSource(strings = {"code", "discountType", "discountValue"})
    void discount_missingRequired_rejected(String field) {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode(field.equals("code") ? "" : "TEST");
        r.setDiscountType(field.equals("discountType") ? null : DiscountType.FIXED_AMOUNT);
        r.setDiscountValue(field.equals("discountValue") ? null : BigDecimal.valueOf(10000));
        assertThat(hasViolation(r, field)).isTrue();
    }
}
