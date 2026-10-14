package com.hotel.unit;

import com.hotel.dto.DiscountCodeRequest;
import com.hotel.dto.RoomRequest;
import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.*;
import com.hotel.service.*;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Phòng, Loại phòng, Combo & Mã giảm giá")
class CatalogServiceParamTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomImageRepository roomImageRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private FileStorageService fileStorageService;

    private RoomService roomService;
    private RoomTypeService roomTypeService;
    private ComboService comboService;
    private DiscountCodeService discountCodeService;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        roomTypeService = new RoomTypeService(roomTypeRepository, roomRepository);
        roomService = new RoomService(roomRepository, roomTypeService, bookingRepository, fileStorageService, roomImageRepository);
        comboService = new ComboService(comboRepository, fileStorageService);
        discountCodeService = new DiscountCodeService(discountCodeRepository);
        roomType = RoomType.builder().id(1L).name("Phòng thường").basePrice(BigDecimal.valueOf(200000)).maxGuests(2).build();
        when(roomTypeRepository.findById(1L)).thenReturn(Optional.of(roomType));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(10L);
            }
            return r;
        });
        when(fileStorageService.store(any(), anyString())).thenReturn("/uploads/rooms/x.jpg");
    }

    private static List<MultipartFile> images(int n) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            files.add(new MockMultipartFile("imageFiles", "anh" + i + ".jpg", "image/jpeg", new byte[]{1, 2, 3}));
        }
        return files;
    }

    private static RoomRequest roomRequest(String number) {
        RoomRequest r = new RoomRequest();
        r.setRoomNumber(number);
        r.setRoomTypeId(1L);
        r.setPrice(BigDecimal.valueOf(200000));
        return r;
    }

    // ===================== Phòng =====================

    @TcSteps("Gọi RoomService.create() với N ảnh hợp lệ (giới hạn 6 ảnh/phòng)")
    @ParameterizedTest(name = "Thêm phòng kèm {0} ảnh ¦ imageFiles có {0} ảnh ¦ Thành công = {1}; thành công thì lưu đúng {0} ảnh")
    @CsvSource({"0,true", "1,true", "3,true", "5,true", "6,true", "7,false", "10,false"})
    void createRoom_imageLimit(int count, boolean ok) {
        if (ok) {
            Room room = roomService.create(roomRequest("101"), images(count));
            assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
            verify(roomImageRepository, times(count)).save(any(RoomImage.class));
        } else {
            assertThatThrownBy(() -> roomService.create(roomRequest("101"), images(count)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("tối đa 6 ảnh");
            verify(roomRepository, never()).save(any());
        }
    }

    @TcSteps("Phòng đang có E ảnh, gọi RoomService.update() thêm N ảnh mới")
    @ParameterizedTest(name = "Sửa phòng: đang có {0} ảnh, thêm {1} ảnh ¦ hiện có {0}, thêm {1} ¦ Thành công = {2}")
    @CsvSource({"0,6,true", "2,4,true", "5,1,true", "6,0,true", "5,2,false", "6,1,false", "3,4,false"})
    void updateRoom_imageLimit(long existing, int added, boolean ok) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomImageRepository.countByRoomId(10L)).thenReturn(existing);
        if (ok) {
            roomService.update(10L, roomRequest("101"), images(added));
            verify(roomImageRepository, times(added)).save(any(RoomImage.class));
        } else {
            assertThatThrownBy(() -> roomService.update(10L, roomRequest("101"), images(added)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("hiện có " + existing);
        }
    }

    @TcSteps("Mock existsByRoomNumber, gọi RoomService.create()/update() với số phòng theo bộ dữ liệu")
    @ParameterizedTest(name = "Trùng số phòng khi {0} ¦ số phòng \"{1}\", đã tồn tại = {2} ¦ Bị từ chối = {3}")
    @CsvSource({"thêm mới,101,true,true", "thêm mới,102,false,false",
            "sửa - đổi sang số phòng khác,201,true,true", "sửa - giữ nguyên số phòng,101,true,false", "sửa - đổi sang số chưa dùng,305,false,false"})
    void roomNumber_mustBeUnique(String action, String number, boolean exists, boolean rejected) {
        when(roomRepository.existsByRoomNumber(number)).thenReturn(exists);
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        Runnable call = action.startsWith("thêm")
                ? () -> roomService.create(roomRequest(number), null)
                : () -> roomService.update(10L, roomRequest(number), null);
        if (rejected) {
            assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");
        } else {
            call.run();
            verify(roomRepository).save(any(Room.class));
        }
    }

    @TcSteps("Mock phòng ở trạng thái S, nhân viên gọi markReady() (xác nhận dọn dẹp xong)")
    @ParameterizedTest(name = "Xác nhận phòng sẵn sàng khi phòng đang {0} ¦ room.status={0} ¦ Chỉ NOT_READY → AVAILABLE, trạng thái khác báo lỗi")
    @EnumSource(RoomStatus.class)
    void markReady_onlyFromNotReady(RoomStatus status) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(status).build();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        if (status == RoomStatus.NOT_READY) {
            roomService.markReady(10L);
            assertThat(room.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        } else {
            assertThatThrownBy(() -> roomService.markReady(10L)).isInstanceOf(BusinessException.class).hasMessageContaining("chờ dọn dẹp");
            assertThat(room.getStatus()).isEqualTo(status);
        }
    }

    @TcSteps("Mock phòng AVAILABLE, nhân viên đổi trạng thái sang S bằng changeStatus()")
    @ParameterizedTest(name = "Đổi trạng thái phòng trực tiếp sang {0} ¦ status mới={0} ¦ Phòng được lưu với trạng thái {0}")
    @EnumSource(RoomStatus.class)
    void changeStatus_anyStatus(RoomStatus status) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        roomService.changeStatus(10L, status);
        assertThat(room.getStatus()).isEqualTo(status);
        verify(roomRepository).save(room);
    }

    @TcSteps("Mock phòng ở trạng thái S, existsOverlappingBooking = O, gọi isAvailableForDates()")
    @ParameterizedTest(name = "Phòng còn trống cho khoảng ngày - trạng thái {0}, trùng lịch = {1} ¦ status={0}, overlap={1} ¦ isAvailable = {2}")
    @CsvSource({"AVAILABLE,false,true", "AVAILABLE,true,false", "BOOKED,false,true", "OCCUPIED,false,true",
            "NOT_READY,false,true", "MAINTENANCE,false,false", "MAINTENANCE,true,false"})
    void isAvailableForDates(RoomStatus status, boolean overlap, boolean expected) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(status).build();
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsOverlappingBooking(eq(10L), any(), any())).thenReturn(overlap);
        assertThat(roomService.isAvailableForDates(10L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2))).isEqualTo(expected);
    }

    @TcSteps("Gọi searchAvailableRooms() với ngày nhận = hôm nay + a, ngày trả = hôm nay + b")
    @ParameterizedTest(name = "Tìm phòng trống - kiểm tra ngày ¦ checkIn=hôm nay{0}, checkOut=hôm nay{1} ¦ {2}")
    @CsvSource({"+1,+2,Hợp lệ - gọi truy vấn tìm phòng", "+0,+1,Hợp lệ - gọi truy vấn tìm phòng", "+3,+10,Hợp lệ - gọi truy vấn tìm phòng",
            "-1,+2,Lỗi: ngày nhận ở quá khứ", "+2,+2,Lỗi: ngày trả phải sau ngày nhận", "+5,+3,Lỗi: ngày trả phải sau ngày nhận"})
    void searchAvailable_validatesDates(int in, int out, String expected) {
        LocalDate checkIn = LocalDate.now().plusDays(in);
        LocalDate checkOut = LocalDate.now().plusDays(out);
        if (expected.startsWith("Hợp lệ")) {
            roomService.searchAvailableRooms(checkIn, checkOut, 2, null);
            verify(roomRepository).findAvailableRooms(checkIn, checkOut, null, 2);
        } else {
            assertThatThrownBy(() -> roomService.searchAvailableRooms(checkIn, checkOut, 2, null)).isInstanceOf(BusinessException.class);
            verify(roomRepository, never()).findAvailableRooms(any(), any(), any(), any());
        }
    }

    @TcSteps("Gọi searchAvailableRooms() thiếu ngày nhận/ngày trả")
    @ParameterizedTest(name = "Tìm phòng trống - thiếu {0} ¦ {0}=null ¦ Ném BusinessException \"Vui lòng chọn đầy đủ ngày\"")
    @ValueSource(strings = {"checkIn", "checkOut"})
    void searchAvailable_missingDate(String missing) {
        LocalDate in = missing.equals("checkIn") ? null : LocalDate.now().plusDays(1);
        LocalDate out = missing.equals("checkOut") ? null : LocalDate.now().plusDays(2);
        assertThatThrownBy(() -> roomService.searchAvailableRooms(in, out, null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đầy đủ ngày");
    }

    @TcSteps("Mock danh sách đơn của phòng, gọi findUnavailableDates() để tô xám lịch")
    @ParameterizedTest(name = "Ngày bị khóa trên lịch với đơn {0} đêm ¦ đơn từ ngày 10 đến ngày 10+{0} ¦ Khóa {0} ngày, KHÔNG khóa ngày trả phòng")
    @ValueSource(ints = {1, 2, 3, 7})
    void unavailableDates_excludeCheckoutDay(int nights) {
        LocalDate in = LocalDate.of(2026, 12, 10);
        Booking b = Booking.builder().checkInDate(in).checkOutDate(in.plusDays(nights)).checkInTime(LocalTime.NOON)
                .checkOutTime(LocalTime.NOON).status(BookingStatus.CONFIRMED).build();
        when(bookingRepository.findByRoomIdAndStatusIn(eq(10L), any())).thenReturn(List.of(b));
        List<LocalDate> dates = roomService.findUnavailableDates(10L);
        assertThat(dates).hasSize(nights).contains(in).doesNotContain(in.plusDays(nights));
    }

    @TcSteps("Mock phòng có/không có đơn đặt phòng liên quan, gọi RoomService.delete()")
    @ParameterizedTest(name = "Xóa phòng khi phòng {0} ¦ có lịch sử đặt phòng = {1} ¦ Bị chặn = {1}; xóa được thì xóa cả ảnh")
    @CsvSource({"chưa từng được đặt,false", "đã có đơn đặt phòng,true"})
    void deleteRoom_blockedWhenHasBookings(String label, boolean hasBooking) {
        Room room = Room.builder().id(10L).roomNumber("101").roomType(roomType).price(BigDecimal.ONE).status(RoomStatus.AVAILABLE).build();
        List<Booking> bookings = hasBooking ? List.of(Booking.builder().room(room).build()) : List.of();
        when(bookingRepository.findAll()).thenReturn(bookings);
        when(roomImageRepository.findByRoomIdOrderById(10L)).thenReturn(List.of(RoomImage.builder().imageUrl("/uploads/rooms/a.jpg").build()));
        if (hasBooking) {
            assertThatThrownBy(() -> roomService.delete(10L)).isInstanceOf(BusinessException.class).hasMessageContaining("lịch sử đặt phòng");
            verify(roomRepository, never()).deleteById(anyLong());
        } else {
            roomService.delete(10L);
            verify(fileStorageService).delete("/uploads/rooms/a.jpg");
            verify(roomRepository).deleteById(10L);
        }
    }

    @TcSteps("Gọi deleteImage()/replaceImage() với ảnh thuộc phòng khác hoặc không tồn tại")
    @ParameterizedTest(name = "Thao tác ảnh không hợp lệ - {0} ¦ {0} ¦ Ném BusinessException \"{1}\"")
    @CsvSource({"xóa ảnh thuộc phòng khác,không thuộc phòng", "thay ảnh thuộc phòng khác,không thuộc phòng",
            "xóa ảnh không tồn tại,Không tìm thấy ảnh", "thay ảnh nhưng không chọn file,chọn file ảnh"})
    void imageOperations_validated(String scenario, String message) {
        Room other = Room.builder().id(99L).build();
        when(roomImageRepository.findById(5L)).thenReturn(Optional.of(RoomImage.builder().id(5L).room(other).imageUrl("/u/a.jpg").build()));
        when(roomImageRepository.findById(6L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("f", "a.png", "image/png", new byte[]{1});
        Runnable call = switch (scenario) {
            case "xóa ảnh thuộc phòng khác" -> () -> roomService.deleteImage(10L, 5L);
            case "thay ảnh thuộc phòng khác" -> () -> roomService.replaceImage(10L, 5L, file);
            case "xóa ảnh không tồn tại" -> () -> roomService.deleteImage(10L, 6L);
            default -> () -> roomService.replaceImage(10L, 5L, new MockMultipartFile("f", "a.png", "image/png", new byte[0]));
        };
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).hasMessageContaining(message);
    }

    @TcSteps("Mock danh sách phòng theo trạng thái, gọi countByStatus() (dùng cho thẻ số liệu dashboard)")
    @ParameterizedTest(name = "Đếm số phòng ở trạng thái {0} ¦ 5 phòng, mỗi trạng thái 1 phòng + thêm 1 phòng AVAILABLE ¦ Kết quả = {1}")
    @CsvSource({"AVAILABLE,2", "BOOKED,1", "OCCUPIED,1", "MAINTENANCE,1", "NOT_READY,1"})
    void countByStatus(RoomStatus status, long expected) {
        List<Room> rooms = new ArrayList<>();
        for (RoomStatus s : RoomStatus.values()) {
            rooms.add(Room.builder().status(s).build());
        }
        rooms.add(Room.builder().status(RoomStatus.AVAILABLE).build());
        when(roomRepository.findAll()).thenReturn(rooms);
        assertThat(roomService.countByStatus(status)).isEqualTo(expected);
    }

    // ===================== Loại phòng =====================

    @TcSteps("Mock danh sách phòng, gọi RoomTypeService.delete(1)")
    @ParameterizedTest(name = "Xóa loại phòng khi còn {0} phòng thuộc loại đó ¦ số phòng thuộc loại = {0} ¦ Bị chặn = {1}")
    @CsvSource({"0,false", "1,true", "3,true"})
    void deleteRoomType_blockedWhenInUse(int rooms, boolean blocked) {
        List<Room> all = new ArrayList<>();
        for (int i = 0; i < rooms; i++) {
            all.add(Room.builder().roomType(roomType).build());
        }
        all.add(Room.builder().roomType(RoomType.builder().id(2L).build()).build());
        when(roomRepository.findAll()).thenReturn(all);
        if (blocked) {
            assertThatThrownBy(() -> roomTypeService.delete(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("vẫn còn phòng");
        } else {
            roomTypeService.delete(1L);
            verify(roomTypeRepository).deleteById(1L);
        }
    }

    @TcSteps("Gọi findById() với id không tồn tại trên từng service danh mục")
    @ParameterizedTest(name = "Tìm {0} theo id không tồn tại ¦ id=999 ¦ Ném BusinessException \"{1}\"")
    @CsvSource({"loại phòng,Không tìm thấy loại phòng", "phòng,Không tìm thấy phòng", "combo,Không tìm thấy combo", "mã giảm giá,Không tìm thấy mã giảm giá"})
    void findById_notFound(String entity, String message) {
        Runnable call = switch (entity) {
            case "loại phòng" -> () -> roomTypeService.findById(999L);
            case "phòng" -> () -> roomService.findById(999L);
            case "combo" -> () -> comboService.findById(999L);
            default -> () -> discountCodeService.findById(999L);
        };
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).hasMessageContaining(message);
    }

    // ===================== Combo =====================

    @TcSteps("Mock combo đang ở trạng thái active = A, gọi toggleActive() K lần")
    @ParameterizedTest(name = "Ẩn/Hiện combo: ban đầu active={0}, bấm {1} lần ¦ active={0}, toggle {1} lần ¦ active cuối = {2}")
    @CsvSource({"true,1,false", "false,1,true", "true,2,true", "false,2,false", "true,3,false"})
    void combo_toggle(boolean initial, int times, boolean expected) {
        Combo c = Combo.builder().id(5L).name("Lẩu").price(BigDecimal.valueOf(399000)).active(initial).build();
        when(comboRepository.findById(5L)).thenReturn(Optional.of(c));
        for (int i = 0; i < times; i++) {
            comboService.toggleActive(5L);
        }
        assertThat(c.isActive()).isEqualTo(expected);
    }

    @TcSteps("Gọi ComboService.update() có/không kèm ảnh mới")
    @ParameterizedTest(name = "Sửa combo {0} ¦ {0} ¦ Ảnh cũ bị xóa = {1}")
    @CsvSource({"kèm ảnh mới,true", "không đổi ảnh,false"})
    void combo_updateImage(String label, boolean withImage) {
        Combo c = Combo.builder().id(5L).name("Lẩu").price(BigDecimal.ONE).imageUrl("/uploads/combos/old.jpg").active(true).build();
        when(comboRepository.findById(5L)).thenReturn(Optional.of(c));
        com.hotel.dto.ComboRequest req = new com.hotel.dto.ComboRequest();
        req.setName("Lẩu Thái");
        req.setPrice(BigDecimal.valueOf(450000));
        MockMultipartFile img = withImage ? new MockMultipartFile("imageFile", "moi.png", "image/png", new byte[]{1}) : null;
        comboService.update(5L, req, img);
        assertThat(c.getName()).isEqualTo("Lẩu Thái");
        verify(fileStorageService, times(withImage ? 1 : 0)).delete("/uploads/combos/old.jpg");
    }

    // ===================== Mã giảm giá =====================

    private static DiscountCodeRequest codeRequest(String code) {
        DiscountCodeRequest r = new DiscountCodeRequest();
        r.setCode(code);
        r.setDiscountType(DiscountType.PERCENTAGE);
        r.setDiscountValue(BigDecimal.TEN);
        return r;
    }

    @TcSteps("Gọi DiscountCodeService.create() với mã khách nhập (chưa tồn tại)")
    @ParameterizedTest(name = "Tạo mã giảm giá - chuẩn hóa chữ hoa ¦ code=\"{0}\" ¦ Lưu thành \"{1}\", trạng thái đang áp dụng")
    @CsvSource({"welcome10,WELCOME10", "Vip20,VIP20", "NEWBIE2026,NEWBIE2026", "tet-2027,TET-2027"})
    void createCode_uppercased(String input, String stored) {
        when(discountCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiscountCode dc = discountCodeService.create(codeRequest(input));
        assertThat(dc.getCode()).isEqualTo(stored);
        assertThat(dc.isActive()).isTrue();
    }

    @TcSteps("Mock mã DUPTEST đã tồn tại, gọi create() với các biến thể chữ hoa/thường")
    @ParameterizedTest(name = "Tạo mã trùng (không phân biệt hoa thường) ¦ code=\"{0}\" ¦ Ném BusinessException \"đã tồn tại\"")
    @ValueSource(strings = {"DUPTEST", "duptest", "DupTest"})
    void createCode_duplicateCaseInsensitive(String input) {
        when(discountCodeRepository.existsByCode("DUPTEST")).thenReturn(true);
        assertThatThrownBy(() -> discountCodeService.create(codeRequest(input)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");
    }

    @TcSteps("Mock mã hiện tại là OLD, mã khác OTHER đã tồn tại, gọi update() đổi sang mã mới")
    @ParameterizedTest(name = "Sửa mã giảm giá thành \"{0}\" ¦ mã cũ OLD, OTHER đã tồn tại ¦ Bị từ chối = {1}")
    @CsvSource({"OLD,false", "old,false", "NEWCODE,false", "OTHER,true", "other,true"})
    void updateCode_duplicateCheck(String newCode, boolean rejected) {
        DiscountCode dc = DiscountCode.builder().id(3L).code("OLD").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).active(true).build();
        when(discountCodeRepository.findById(3L)).thenReturn(Optional.of(dc));
        when(discountCodeRepository.existsByCode("OTHER")).thenReturn(true);
        when(discountCodeRepository.existsByCode("OLD")).thenReturn(true);
        if (rejected) {
            assertThatThrownBy(() -> discountCodeService.update(3L, codeRequest(newCode))).isInstanceOf(BusinessException.class);
        } else {
            discountCodeService.update(3L, codeRequest(newCode));
            assertThat(dc.getCode()).isEqualTo(newCode.toUpperCase());
        }
    }

    @TcSteps("Mock mã đang hoạt động, gọi validateAndCalculateDiscount(code, loại khách, tạm tính)")
    @ParameterizedTest(name = "Kiểm tra mã {0} {1} cho khách {2} trên {3} VND ¦ type={0}, value={1}, customer={2}, subtotal={3} ¦ Số tiền giảm = {4}")
    @CsvSource({
            "PERCENTAGE,10,NEW,1000000,100000", "PERCENTAGE,25,REGULAR,800000,200000", "PERCENTAGE,100,VIP,450000,450000",
            "PERCENTAGE,5,NEW,1799000,89950", "FIXED_AMOUNT,100000,NEW,1000000,100000", "FIXED_AMOUNT,100000,VIP,80000,80000",
            "FIXED_AMOUNT,250000,REGULAR,250000,250000", "FIXED_AMOUNT,50000,NEW,0,0"})
    void validateAndCalculate(DiscountType type, String value, CustomerType customerType, String subtotal, String expected) {
        when(discountCodeRepository.findByCodeAndActiveTrue("KM")).thenReturn(Optional.of(DiscountCode.builder()
                .code("KM").discountType(type).discountValue(new BigDecimal(value)).active(true).build()));
        assertThat(discountCodeService.validateAndCalculateDiscount("KM", customerType, new BigDecimal(subtotal)))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @TcSteps("Mock mã dành riêng cho loại khách A, gọi validateAndCalculateDiscount() với loại khách B")
    @ParameterizedTest(name = "Kiểm tra mã dành cho {0} với khách {1} ¦ applicable={0}, customer={1} ¦ Hợp lệ = {2}")
    @CsvSource({"VIP,VIP,true", "VIP,NEW,false", "REGULAR,REGULAR,true", "REGULAR,VIP,false", "NEW,NEW,true", "NEW,REGULAR,false"})
    void validateAndCalculate_customerType(CustomerType applicable, CustomerType customer, boolean ok) {
        when(discountCodeRepository.findByCodeAndActiveTrue("KM")).thenReturn(Optional.of(DiscountCode.builder()
                .code("KM").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).applicableCustomerType(applicable).active(true).build()));
        if (ok) {
            assertThat(discountCodeService.validateAndCalculateDiscount("KM", customer, BigDecimal.valueOf(1000000))).isEqualByComparingTo("100000");
        } else {
            assertThatThrownBy(() -> discountCodeService.validateAndCalculateDiscount("KM", customer, BigDecimal.valueOf(1000000)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @TcSteps("Mock mã đang ở trạng thái active = A, gọi toggleActive()")
    @ParameterizedTest(name = "Bật/Tắt mã giảm giá đang active={0} ¦ active={0} ¦ Sau khi bấm active = {1}")
    @CsvSource({"true,false", "false,true"})
    void code_toggle(boolean initial, boolean expected) {
        DiscountCode dc = DiscountCode.builder().id(3L).code("X").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).active(initial).build();
        when(discountCodeRepository.findById(3L)).thenReturn(Optional.of(dc));
        discountCodeService.toggleActive(3L);
        assertThat(dc.isActive()).isEqualTo(expected);
    }
}
