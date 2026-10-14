package com.hotel.unit;

import com.hotel.dto.RegisterRequest;
import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.UserRepository;
import com.hotel.security.AdminPortalSuccessHandler;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.CustomerPortalSuccessHandler;
import com.hotel.service.CustomerManagementService;
import com.hotel.service.FileStorageService;
import com.hotel.service.UserService;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import com.hotel.util.PaginationUtil;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Tiện ích, bảo mật, khách hàng & tài khoản")
class SupportParamTest {

    @Mock private UserRepository userRepository;

    @TempDir Path tempDir;

    // ===================== Phân trang =====================

    @TcSteps("Gọi PaginationUtil.totalPages(tổng số bản ghi, 10)")
    @ParameterizedTest(name = "Tính tổng số trang với {0} bản ghi, 10 bản ghi/trang ¦ totalItems={0} ¦ totalPages = {1}")
    @CsvSource({"0,1", "1,1", "9,1", "10,1", "11,2", "20,2", "21,3", "99,10", "100,10", "101,11"})
    void totalPages(int items, int expected) {
        assertThat(PaginationUtil.totalPages(items, PaginationUtil.DEFAULT_PAGE_SIZE)).isEqualTo(expected);
    }

    @TcSteps("Tạo danh sách 25 phần tử (1..25), gọi PaginationUtil.slice(list, page, 10)")
    @ParameterizedTest(name = "Cắt trang {0} của danh sách 25 phần tử ¦ page={0} ¦ Trả về {1} phần tử, bắt đầu từ phần tử {2}")
    @CsvSource({"1,10,1", "2,10,11", "3,5,21", "0,10,1", "-5,10,1", "4,5,21", "99,5,21"})
    void slice(int page, int size, int first) {
        List<Integer> all = IntStream.rangeClosed(1, 25).boxed().toList();
        List<Integer> result = PaginationUtil.slice(all, page, 10);
        assertThat(result).hasSize(size);
        assertThat(result.get(0)).isEqualTo(first);
    }

    @TcSteps("Gọi PaginationUtil.slice() trên danh sách rỗng")
    @ParameterizedTest(name = "Cắt trang trên danh sách rỗng ¦ page={0} ¦ Trả về danh sách rỗng, không lỗi")
    @ValueSource(ints = {1, 2})
    void slice_empty(int page) {
        assertThat(PaginationUtil.slice(List.of(), page, 10)).isEmpty();
    }

    @TcSteps("Gọi PaginationUtil.pageNumbersToShow(trang hiện tại, tổng số trang); 0 nghĩa là dấu \"...\"")
    @ParameterizedTest(name = "Dãy số trang hiển thị - trang {0}/{1} ¦ current={0}, total={1} ¦ Hiển thị [{2}]")
    @CsvSource(delimiter = '|', value = {
            "1|1|1", "1|5|1,2,3,4,5", "3|7|1,2,3,4,5,6,7", "1|10|1,2,0,10", "2|10|1,2,3,0,10", "3|10|1,2,3,4,0,10",
            "5|10|1,0,4,5,6,0,10", "8|10|1,0,7,8,9,10", "10|10|1,0,9,10", "50|100|1,0,49,50,51,0,100"})
    void pageNumbers(int current, int total, String expected) {
        String actual = PaginationUtil.pageNumbersToShow(current, total).stream().map(String::valueOf).collect(Collectors.joining(","));
        assertThat(actual).isEqualTo(expected);
    }

    // ===================== Lưu file ảnh =====================

    private FileStorageService storage() {
        FileStorageService s = new FileStorageService();
        ReflectionTestUtils.setField(s, "baseUploadDir", tempDir.toString());
        return s;
    }

    @TcSteps("Tạo MockMultipartFile tên anh.<đuôi>, gọi FileStorageService.store(file, \"rooms\")")
    @ParameterizedTest(name = "Tải ảnh định dạng .{0} ¦ originalFilename=anh.{0} ¦ Chấp nhận = {1}; chấp nhận thì trả về /uploads/rooms/<uuid>.{0} và file tồn tại")
    @CsvSource({"jpg,true", "jpeg,true", "png,true", "webp,true", "JPG,true", "PNG,true", "Jpeg,true",
            "gif,false", "bmp,false", "pdf,false", "exe,false", "txt,false", "svg,false", "html,false"})
    void store_extensions(String ext, boolean ok) {
        FileStorageService s = storage();
        MockMultipartFile f = new MockMultipartFile("f", "anh." + ext, "application/octet-stream", new byte[]{1, 2, 3});
        if (ok) {
            String url = s.store(f, "rooms");
            assertThat(url).startsWith("/uploads/rooms/").endsWith("." + ext);
            assertThat(Files.exists(tempDir.resolve("rooms").resolve(url.substring("/uploads/rooms/".length())))).isTrue();
        } else {
            assertThatThrownBy(() -> s.store(f, "rooms")).isInstanceOf(BusinessException.class).hasMessageContaining("jpg, jpeg, png hoặc webp");
        }
    }

    @TcSteps("Gọi store() với file có tên không có phần mở rộng hoặc tên đánh lừa")
    @ParameterizedTest(name = "Tên file không hợp lệ ¦ originalFilename=\"{0}\" ¦ Ném BusinessException")
    @ValueSource(strings = {"anh", "anhjpg", "anh.jpg.exe", "virus.php"})
    void store_badNames(String name) {
        MockMultipartFile f = new MockMultipartFile("f", name, "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> storage().store(f, "rooms")).isInstanceOf(BusinessException.class);
    }

    @TcSteps("Gọi store() với file null hoặc rỗng (không chọn ảnh)")
    @ParameterizedTest(name = "Không chọn ảnh ¦ file {0} ¦ Trả về null, không lỗi")
    @ValueSource(strings = {"null", "rỗng (0 byte)"})
    void store_emptyReturnsNull(String kind) {
        MockMultipartFile f = kind.equals("null") ? null : new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[0]);
        assertThat(storage().store(f, "rooms")).isNull();
    }

    @TcSteps("Gọi FileStorageService.delete(url) với url theo bộ dữ liệu")
    @ParameterizedTest(name = "Xóa file ảnh ¦ imageUrl=[{0}] ¦ Không ném lỗi trong mọi trường hợp")
    @ValueSource(strings = {"", " ", "/uploads/rooms/khongton.jpg", "/uploads/combos/abc.png"})
    void delete_neverThrows(String url) {
        storage().delete(url);
        storage().delete(null);
    }

    // ===================== Nhãn tiếng Việt của enum =====================

    @TcSteps("Gọi BookingStatus.getVietnameseLabel() và getBadgeClass()")
    @ParameterizedTest(name = "Nhãn trạng thái đơn {0} ¦ BookingStatus.{0} ¦ Hiển thị \"{1}\", màu badge \"{2}\"")
    @CsvSource({"PENDING,Chờ xác nhận,bg-warning text-dark", "CONFIRMED,Đã xác nhận,bg-info text-dark", "CHECKED_IN,Đã nhận phòng,bg-success",
            "CHECKED_OUT,Đã trả phòng,bg-secondary", "CANCELLED,Đã hủy,bg-danger"})
    void bookingStatusLabels(BookingStatus s, String label, String badge) {
        assertThat(s.getVietnameseLabel()).isEqualTo(label);
        assertThat(s.getBadgeClass()).isEqualTo(badge);
    }

    @TcSteps("Gọi RoomStatus.getVietnameseLabel()")
    @ParameterizedTest(name = "Nhãn trạng thái phòng {0} ¦ RoomStatus.{0} ¦ Hiển thị \"{1}\"")
    @CsvSource({"AVAILABLE,Còn trống", "BOOKED,Đã được đặt", "OCCUPIED,Đang sử dụng", "MAINTENANCE,Đang bảo trì", "NOT_READY,Chưa sẵn sàng (đang dọn dẹp)"})
    void roomStatusLabels(RoomStatus s, String label) {
        assertThat(s.getVietnameseLabel()).isEqualTo(label);
    }

    @TcSteps("Gọi CustomerType/DiscountType.getVietnameseLabel()")
    @ParameterizedTest(name = "Nhãn {0} ¦ {0} ¦ Hiển thị \"{1}\"")
    @CsvSource({"CustomerType.NEW,Khách lần đầu", "CustomerType.REGULAR,Khách quen", "CustomerType.VIP,Khách VIP",
            "DiscountType.PERCENTAGE,Phần trăm", "DiscountType.FIXED_AMOUNT,Số tiền cố định"})
    void otherLabels(String constant, String label) {
        String[] p = constant.split("\\.");
        String actual = p[0].equals("CustomerType") ? CustomerType.valueOf(p[1]).getVietnameseLabel() : DiscountType.valueOf(p[1]).getVietnameseLabel();
        assertThat(actual).isEqualTo(label);
    }

    // ===================== Bảo mật =====================

    private static User user(UserRole role) {
        return User.builder().id(1L).fullName("U").email(role.name().toLowerCase() + "@hotel.com").password("$2a$hash").role(role).build();
    }

    @TcSteps("Tạo CustomUserDetails bọc User có vai trò R, đọc quyền và thông tin đăng nhập")
    @ParameterizedTest(name = "Quyền của tài khoản {0} ¦ role={0} ¦ authorities = [ROLE_{0}], username = email, tài khoản luôn ở trạng thái hoạt động")
    @EnumSource(UserRole.class)
    void customUserDetails(UserRole role) {
        CustomUserDetails d = new CustomUserDetails(user(role));
        assertThat(d.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_" + role.name());
        assertThat(d.getUsername()).isEqualTo(role.name().toLowerCase() + "@hotel.com");
        assertThat(d.isEnabled() && d.isAccountNonLocked() && d.isAccountNonExpired() && d.isCredentialsNonExpired()).isTrue();
    }

    @TcSteps("Giả lập đăng nhập thành công bằng tài khoản vai trò R tại cổng P, gọi onAuthenticationSuccess()")
    @ParameterizedTest(name = "Điều hướng sau đăng nhập - {1} tại cổng {0} ¦ portal={0}, role={1} ¦ Chuyển tới \"{2}\"")
    @CsvSource({"khách hàng,CUSTOMER,/customer/rooms", "khách hàng,STAFF,/login?error=wrong_portal", "khách hàng,ADMIN,/login?error=wrong_portal",
            "quản trị,CUSTOMER,/admin?error=wrong_portal", "quản trị,STAFF,/admin/dashboard", "quản trị,ADMIN,/admin/dashboard"})
    void successHandlers(String portal, UserRole role, String target) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        CustomUserDetails d = new CustomUserDetails(user(role));
        var auth = new UsernamePasswordAuthenticationToken(d, null, d.getAuthorities());
        if (portal.equals("khách hàng")) {
            new CustomerPortalSuccessHandler().onAuthenticationSuccess(req, res, auth);
        } else {
            new AdminPortalSuccessHandler().onAuthenticationSuccess(req, res, auth);
        }
        assertThat(res.getRedirectedUrl()).isEqualTo(target);
    }

    @TcSteps("Mock UserRepository.findByEmail, gọi CustomUserDetailsService.loadUserByUsername(email)")
    @ParameterizedTest(name = "Nạp tài khoản theo email ¦ email=\"{0}\", tồn tại = {1} ¦ Tồn tại thì trả về UserDetails, không thì ném UsernameNotFoundException")
    @CsvSource({"admin@hotel.com,true", "staff@hotel.com,true", "khach@gmail.com,true", "khongco@gmail.com,false", "'',false"})
    void loadUserByUsername(String email, boolean exists) {
        when(userRepository.findByEmail(email)).thenReturn(exists ? Optional.of(user(UserRole.CUSTOMER)) : Optional.empty());
        CustomUserDetailsService svc = new CustomUserDetailsService(userRepository);
        if (exists) {
            assertThat(svc.loadUserByUsername(email)).isInstanceOf(CustomUserDetails.class);
        } else {
            assertThatThrownBy(() -> svc.loadUserByUsername(email)).isInstanceOf(UsernameNotFoundException.class);
        }
    }

    // ===================== Quản lý khách hàng & tài khoản =====================

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @TcSteps("Mock tài khoản có vai trò R, nhân viên gọi updateCustomerType(id, loại mới)")
    @ParameterizedTest(name = "Đổi loại khách hàng cho tài khoản {0} sang {1} ¦ role={0}, type={1} ¦ Thành công = {2}")
    @CsvSource({"CUSTOMER,NEW,true", "CUSTOMER,REGULAR,true", "CUSTOMER,VIP,true", "STAFF,VIP,false", "ADMIN,REGULAR,false"})
    void updateCustomerType(UserRole role, CustomerType type, boolean ok) {
        User u = user(role);
        u.setCustomerType(CustomerType.NEW);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        CustomerManagementService svc = new CustomerManagementService(userRepository, encoder);
        if (ok) {
            svc.updateCustomerType(1L, type);
            assertThat(u.getCustomerType()).isEqualTo(type);
        } else {
            assertThatThrownBy(() -> svc.updateCustomerType(1L, type)).isInstanceOf(BusinessException.class).hasMessageContaining("CUSTOMER");
        }
    }

    @TcSteps("Mock tìm tài khoản theo SĐT (khách tại quầy) hoặc email (khách vãng lai online), gọi findOrCreate...")
    @ParameterizedTest(name = "Tìm/tạo tài khoản khách vãng lai - {0}, đã có tài khoản = {1} ¦ {0}, đã có = {1} ¦ Có rồi thì dùng lại, chưa có thì tạo mới vai trò CUSTOMER loại NEW")
    @CsvSource({"tại quầy theo SĐT,true", "tại quầy theo SĐT,false", "online theo email,true", "online theo email,false"})
    void findOrCreateGuest(String channel, boolean exists) {
        User existing = user(UserRole.CUSTOMER);
        when(userRepository.findByPhoneNumber("0909001122")).thenReturn(exists ? Optional.of(existing) : Optional.empty());
        when(userRepository.findByEmail("khach@gmail.com")).thenReturn(exists ? Optional.of(existing) : Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerManagementService svc = new CustomerManagementService(userRepository, encoder);
        User u = channel.startsWith("tại quầy")
                ? svc.findOrCreateWalkInCustomer("Khách Lẻ", "0909001122")
                : svc.findOrCreateGuestCustomer("Khách Lẻ", "khach@gmail.com", "0909001122");
        if (exists) {
            assertThat(u).isSameAs(existing);
            verify(userRepository, never()).save(any());
        } else {
            assertThat(u.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(u.getCustomerType()).isEqualTo(CustomerType.NEW);
            assertThat(u.getPassword()).startsWith("$2");
            if (channel.startsWith("tại quầy")) {
                assertThat(u.getEmail()).isEqualTo("0909001122@khachvanglai.local");
            }
        }
    }

    private static RegisterRequest reg(String pw, String confirm) {
        RegisterRequest r = new RegisterRequest();
        r.setFullName("Khách Mới");
        r.setEmail("moi@gmail.com");
        r.setPhoneNumber("0912345678");
        r.setPassword(pw);
        r.setConfirmPassword(confirm);
        return r;
    }

    @TcSteps("Mock existsByEmail, gọi UserService.registerCustomer()")
    @ParameterizedTest(name = "Đăng ký tài khoản - {0} ¦ password=\"{1}\", confirm=\"{2}\", email đã tồn tại = {3} ¦ Thành công = {4}")
    @CsvSource({"dữ liệu hợp lệ,matkhau123,matkhau123,false,true", "mật khẩu xác nhận khác,matkhau123,matkhau321,false,false",
            "khác chữ hoa/thường,MatKhau123,matkhau123,false,false", "email đã được đăng ký,matkhau123,matkhau123,true,false"})
    void registerCustomer(String label, String pw, String confirm, boolean emailExists, boolean ok) {
        when(userRepository.existsByEmail("moi@gmail.com")).thenReturn(emailExists);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        UserService svc = new UserService(userRepository, encoder);
        if (ok) {
            User u = svc.registerCustomer(reg(pw, confirm));
            assertThat(u.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(encoder.matches(pw, u.getPassword())).isTrue();
            assertThat(u.getPassword()).isNotEqualTo(pw);
        } else {
            assertThatThrownBy(() -> svc.registerCustomer(reg(pw, confirm))).isInstanceOf(BusinessException.class);
            verify(userRepository, never()).save(any());
        }
    }

    @TcSteps("Mã hóa mật khẩu bằng BCrypt rồi so khớp với chuỗi gốc và chuỗi sai")
    @ParameterizedTest(name = "Mã hóa mật khẩu BCrypt ¦ password=\"{0}\" ¦ Chuỗi lưu khác chuỗi gốc, khớp đúng mật khẩu, không khớp mật khẩu sai")
    @ValueSource(strings = {"admin123", "staff123", "customer123", "Mật_khẩu tiếng Việt 2026"})
    void bcrypt(String pw) {
        String hash = encoder.encode(pw);
        assertThat(hash).isNotEqualTo(pw).startsWith("$2");
        assertThat(encoder.matches(pw, hash)).isTrue();
        assertThat(encoder.matches(pw + "x", hash)).isFalse();
    }
}
