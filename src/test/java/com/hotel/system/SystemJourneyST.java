package com.hotel.system;

import com.hotel.entity.*;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Kich ban dau-cuoi chay tuan tu tren CUNG mot du lieu: khach vang lai dat phong -> nhan vien xu ly het vong doi don.
@TcSuite(level = "ST", module = "Kịch bản đầu-cuối: đặt phòng → xác nhận → check-in → check-out")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemJourneyST extends StBase {

    private Room room;
    private Combo combo;
    private LocalDate in;
    private LocalDate out;
    private Long bookingId;
    private String guestBookingUrl;
    private final Browser guest = new Browser();
    private final Browser staff = new Browser();
    private final Browser customerBrowser = new Browser();
    private User customer;
    private Long customerBookingId;

    @BeforeAll
    void arrange() {
        room = createRoom(createType("Phòng Hành Trình " + seq(), 350000, 2), 350000);
        combo = comboRepository.save(Combo.builder().name("Lẩu Hành Trình").price(BigDecimal.valueOf(399000)).active(true).build());
        discountCodeRepository.save(DiscountCode.builder().code("HANHTRINH10").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).active(true).build());
        in = LocalDate.now().plusDays(20);
        out = in.plusDays(2);
        customer = createUser(UserRole.CUSTOMER, CustomerType.NEW, "matkhau123");
    }

    private String roomUrl() {
        return "/customer/rooms/" + room.getId() + "?checkIn=" + in + "&checkOut=" + out;
    }

    @Test
    @Order(1)
    @TcSteps("Khách vãng lai mở /customer/rooms, nhập ngày nhận/trả, bấm Tìm phòng")
    @DisplayName("Khách vãng lai tìm thấy phòng trống ¦ checkIn=+20, checkOut=+22, chưa đăng nhập ¦ Kết quả tìm kiếm có phòng của kịch bản")
    void s01_search() {
        Res r = guest.get("/customer/rooms?checkIn=" + in + "&checkOut=" + out);
        assertThat(r.body()).contains(room.getRoomNumber());
    }

    @Test
    @Order(2)
    @TcSteps("Bấm Xem chi tiết phòng, chọn khoảng ngày và combo Lẩu")
    @DisplayName("Trang chi tiết phòng tính đúng tổng tiền ¦ 2 đêm × 350.000 + combo 399.000 ¦ Hiển thị \"Tổng tiền: 1099000VND\" và \"Phòng còn trống\"")
    void s02_detail() {
        Res r = guest.get(roomUrl() + "&comboId=" + combo.getId());
        assertThat(r.body()).contains("1099000VND").contains("Phòng còn trống trong khoảng ngày này");
    }

    @Test
    @Order(3)
    @TcSteps("Bấm Đặt phòng ngay → mở trang xác nhận đặt phòng")
    @DisplayName("Trang xác nhận đặt phòng cho khách vãng lai ¦ GET /customer/bookings/new (chưa đăng nhập) ¦ Hiển thị form thông tin người nhận, ghi chú không cần đăng nhập, ô mã giảm giá")
    void s03_confirmPage() {
        Res r = guest.get("/customer/bookings/new?roomId=" + room.getId() + "&checkIn=" + in + "&checkOut=" + out + "&comboId=" + combo.getId());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("Thông tin người nhận phòng").contains("Không cần đăng nhập").contains("Mã giảm giá (nếu có)");
    }

    @Test
    @Order(4)
    @TcSteps("Nhập mã HANHTRINH10 và bấm Kiểm tra mã (AJAX)")
    @DisplayName("Kiểm tra mã giảm giá trên trang xác nhận ¦ code=HANHTRINH10, tạm tính 1.099.000 ¦ JSON valid=true, giảm 109900")
    void s04_checkDiscount() {
        Res r = guest.get("/customer/bookings/check-discount?code=HANHTRINH10&roomId=" + room.getId() + "&checkIn=" + in + "&checkOut=" + out
                + "&comboId=" + combo.getId());
        assertThat(r.body()).contains("\"valid\":true").contains("109900");
    }

    @Test
    @Order(5)
    @TcSteps("Điền họ tên, email, SĐT, giờ nhận/trả, mã giảm giá rồi bấm Xác nhận đặt phòng")
    @DisplayName("Khách vãng lai đặt phòng thành công ¦ POST /customer/bookings/new đủ thông tin + mã HANHTRINH10 ¦ Chuyển tới trang đơn, trạng thái \"Chờ xác nhận\", tổng 989100")
    void s05_book() {
        Res r = guest.post("/customer/bookings/new", form("roomId", room.getId().toString(), "checkIn", in.toString(), "checkOut", out.toString(),
                "comboId", combo.getId().toString(), "guestName", "Khách Hành Trình", "guestEmail", "hanhtrinh" + seq() + "@gmail.com",
                "guestPhone", "0966000111", "checkInTime", "14:00", "checkOutTime", "12:00", "discountCode", "HANHTRINH10"));
        assertThat(r.status()).isEqualTo(302);
        assertThat(r.location()).startsWith("/customer/bookings/");
        // Link tra ve co kem ma truy cap: /customer/bookings/<id>?token=<32 ky tu>
        assertThat(r.location()).matches("/customer/bookings/\\d+\\?token=[0-9a-f]{32}");
        guestBookingUrl = r.location();
        bookingId = Long.valueOf(r.location().substring("/customer/bookings/".length(), r.location().indexOf('?')));
        Res page = guest.get(r.location());
        assertThat(page.body()).contains("Chờ xác nhận").contains("989100");
    }

    @Test
    @Order(6)
    @TcSteps("Khách khác thử đặt cùng phòng, cùng khoảng ngày")
    @DisplayName("Chống đặt trùng phòng trên server thật ¦ khách thứ 2 đặt cùng phòng +20 → +22 ¦ Ở lại trang xác nhận, báo phòng đang có đơn chờ xác nhận và giờ phòng trống trở lại")
    void s06_doubleBooking() {
        Browser other = new Browser();
        other.get("/customer/rooms");
        Res r = other.post("/customer/bookings/new", form("roomId", room.getId().toString(), "checkIn", in.toString(), "checkOut", out.toString(),
                "guestName", "Khách Thứ Hai", "guestEmail", "thu2" + seq() + "@gmail.com", "guestPhone", "0966000222",
                "checkInTime", "14:00", "checkOutTime", "12:00"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("Phòng đã có khách trong khoảng ngày bạn chọn").contains("đơn chờ xác nhận")
                .contains("Phòng trống trở lại từ 12:00");
    }

    @Test
    @Order(7)
    @TcSteps("Nhân viên đăng nhập cổng quản trị, mở Quản lý đặt phòng")
    @DisplayName("Nhân viên thấy đơn mới trong danh sách ¦ đăng nhập staff, GET /admin/bookings ¦ Có đơn của kịch bản kèm nhãn \"Đơn mới\"")
    void s07_staffSeesNew() {
        staff.loginAdmin(createUser(UserRole.STAFF, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        Res r = staff.get("/admin/bookings");
        assertThat(r.body()).contains("#" + bookingId).contains("Đơn mới");
    }

    @Test
    @Order(8)
    @TcSteps("Nhân viên mở chi tiết đơn và bấm Xác nhận đơn")
    @DisplayName("Nhân viên xác nhận đơn ¦ POST /admin/bookings/<id>/confirm ¦ Trang chi tiết hiển thị \"Đã xác nhận\" và nút Check-in")
    void s08_confirm() {
        staff.get("/admin/bookings/" + bookingId);
        Res r = staff.follow(staff.post("/admin/bookings/" + bookingId + "/confirm", form()));
        assertThat(r.body()).contains("Đã xác nhận").contains("Check-in");
    }

    @Test
    @Order(9)
    @TcSteps("Khách vãng lai mở lại link đơn của mình")
    @DisplayName("Khách xem đơn sau khi được xác nhận ¦ GET /customer/bookings/<id> ¦ Trạng thái \"Đã xác nhận\"")
    void s09_guestSeesConfirmed() {
        assertThat(guest.get(guestBookingUrl).body()).contains("Đã xác nhận");
    }

    @Test
    @Order(10)
    @TcSteps("Nhân viên khai báo lưu trú (nhập CCCD của khách), bấm Check-in, sau đó mở danh sách phòng")
    @DisplayName("Check-in cho khách ¦ POST /admin/bookings/<id>/guests rồi /check-in ¦ Đơn \"Đã nhận phòng\", phòng chuyển \"Đang sử dụng\"")
    void s10_checkIn() {
        staff.post("/admin/bookings/" + bookingId + "/guests", form("fullName", "Nguyễn Văn An", "dateOfBirth", "1995-05-20",
                "gender", "MALE", "idType", "CCCD", "idNumber", "001095012345", "nationality", "Việt Nam"));
        Res r = staff.follow(staff.post("/admin/bookings/" + bookingId + "/check-in", form()));
        assertThat(r.body()).contains("Đã nhận phòng").contains("Check-out");
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
    }

    @Test
    @Order(11)
    @TcSteps("Khách vãng lai mở lại trang đơn sau khi đã nhận phòng")
    @DisplayName("Khách không còn sửa/hủy được đơn đã check-in ¦ GET /customer/bookings/<id> ¦ Không còn nút \"Hủy đặt phòng\"")
    void s11_guestCannotCancelAfterCheckIn() {
        assertThat(guest.get(guestBookingUrl).body()).doesNotContain("Hủy đặt phòng");
    }

    @Test
    @Order(12)
    @TcSteps("Nhân viên bấm Check-out")
    @DisplayName("Check-out cho khách ¦ POST /admin/bookings/<id>/check-out ¦ Đơn \"Đã trả phòng\", phòng chuyển \"Chưa sẵn sàng (đang dọn dẹp)\"")
    void s12_checkOut() {
        Res r = staff.follow(staff.post("/admin/bookings/" + bookingId + "/check-out", form()));
        assertThat(r.body()).contains("Đã trả phòng");
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.NOT_READY);
    }

    @Test
    @Order(13)
    @TcSteps("Nhân viên mở Quản lý phòng")
    @DisplayName("Phòng chờ dọn dẹp có nút xác nhận ¦ GET /admin/rooms ¦ Hiển thị nút \"Xác nhận sẵn sàng\"")
    void s13_roomListShowsMarkReady() {
        assertThat(staff.get("/admin/rooms").body()).contains("Xác nhận sẵn sàng");
    }

    @Test
    @Order(14)
    @TcSteps("Nhân viên bấm Xác nhận sẵn sàng cho phòng")
    @DisplayName("Xác nhận phòng dọn xong ¦ POST /admin/rooms/<id>/mark-ready ¦ Phòng về \"Còn trống\"")
    void s14_markReady() {
        staff.post("/admin/rooms/" + room.getId() + "/mark-ready", form());
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    @Order(15)
    @TcSteps("Nhân viên mở Dashboard với khoảng ngày hôm nay")
    @DisplayName("Dashboard ghi nhận doanh thu đơn vừa hoàn tất ¦ GET /admin/dashboard?fromDate=hôm nay&toDate=hôm nay ¦ Dữ liệu biểu đồ doanh thu có khoản 989.100 của đơn vừa trả phòng")
    void s15_dashboard() {
        Res r = staff.get("/admin/dashboard?fromDate=" + LocalDate.now() + "&toDate=" + LocalDate.now());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains("Doanh thu theo loại phòng").contains("989100");
    }

    @Test
    @Order(16)
    @TcSteps("Khách hàng đăng nhập, đặt phòng 1 đêm, mở Lịch sử đặt phòng")
    @DisplayName("Khách đã đăng nhập đặt phòng và thấy trong lịch sử ¦ đăng nhập customer, POST /customer/bookings/new ¦ Lịch sử có đơn mới, thông tin người nhận điền sẵn từ tài khoản")
    void s16_customerBooks() {
        customerBrowser.loginCustomer(customer.getEmail(), "matkhau123");
        Res form = customerBrowser.get("/customer/bookings/new?roomId=" + room.getId() + "&checkIn=" + in.plusDays(5) + "&checkOut=" + in.plusDays(6));
        assertThat(form.body()).contains(customer.getEmail());
        Res r = customerBrowser.post("/customer/bookings/new", form("roomId", room.getId().toString(), "checkIn", in.plusDays(5).toString(),
                "checkOut", in.plusDays(6).toString(), "guestName", customer.getFullName(), "guestEmail", customer.getEmail(),
                "guestPhone", customer.getPhoneNumber(), "checkInTime", "14:00", "checkOutTime", "12:00"));
        customerBookingId = Long.valueOf(r.location().substring("/customer/bookings/".length(), r.location().indexOf('?')));
        assertThat(customerBrowser.get("/customer/bookings").body()).contains("#" + customerBookingId);
    }

    @Test
    @Order(17)
    @TcSteps("Khách mở đơn, chọn giờ nhận 15:00, giờ trả 11:00, bấm Lưu")
    @DisplayName("Khách tự đổi giờ nhận/trả phòng ¦ POST /customer/bookings/<id>/edit-time 15:00/11:00 ¦ Đơn lưu giờ mới 15:00/11:00; nhân viên thấy nhãn \"Khách đổi giờ nhận phòng\"")
    void s17_editTime() {
        customerBrowser.get("/customer/bookings/" + customerBookingId);
        Res r = customerBrowser.follow(customerBrowser.post("/customer/bookings/" + customerBookingId + "/edit-time",
                form("checkInTime", "15:00", "checkOutTime", "11:00")));
        assertThat(r.status()).isEqualTo(200);
        Booking b = bookingRepository.findById(customerBookingId).orElseThrow();
        assertThat(b.getCheckInTime()).isEqualTo(java.time.LocalTime.of(15, 0));
        assertThat(b.getCheckOutTime()).isEqualTo(java.time.LocalTime.of(11, 0));
        assertThat(staff.get("/admin/bookings").body()).contains("Khách đổi giờ nhận phòng");
    }

    @Test
    @Order(18)
    @TcSteps("Khách chọn combo Lẩu trong mục Đổi/hủy combo, bấm Cập nhật combo")
    @DisplayName("Khách tự thêm combo cho đơn ¦ POST /customer/bookings/<id>/edit-combo comboId=Lẩu ¦ Tổng tiền mới 749000 (350.000 + 399.000)")
    void s18_editCombo() {
        Res r = customerBrowser.follow(customerBrowser.post("/customer/bookings/" + customerBookingId + "/edit-combo",
                form("comboId", combo.getId().toString())));
        assertThat(r.body()).contains("749000");
    }

    @Test
    @Order(19)
    @TcSteps("Khách bấm Hủy đặt phòng và xác nhận")
    @DisplayName("Khách tự hủy đơn ¦ POST /customer/bookings/<id>/cancel ¦ Đơn hiển thị \"Đã hủy\"")
    void s19_cancel() {
        Res r = customerBrowser.follow(customerBrowser.post("/customer/bookings/" + customerBookingId + "/cancel", form()));
        assertThat(r.body()).contains("Đã hủy");
    }

    @Test
    @Order(20)
    @TcSteps("Nhân viên mở form Đặt phòng tại quầy, tìm phòng trống, nhập thông tin khách vãng lai, xác nhận")
    @DisplayName("Đặt phòng tại quầy trên server thật ¦ POST /admin/bookings/walk-in/new ¦ Về danh sách đặt phòng, đơn mới ở trạng thái \"Đã xác nhận\"")
    void s20_walkIn() {
        String phone = String.format("03%08d", seq());
        staff.get("/admin/bookings/walk-in/new?checkIn=" + in.plusDays(10) + "&checkOut=" + in.plusDays(11));
        Res r = staff.post("/admin/bookings/walk-in/new", form("guestName", "Khách Tại Quầy", "guestEmail", "quay" + seq() + "@gmail.com",
                "guestPhone", phone, "roomId", room.getId().toString(), "checkIn", in.plusDays(10).toString(),
                "checkOut", in.plusDays(11).toString(), "checkInTime", "14:00", "checkOutTime", "12:00"));
        assertThat(r.location()).isEqualTo("/admin/bookings");
        Booking b = bookingRepository.findAll().stream().filter(x -> phone.equals(x.getGuestPhone())).findFirst().orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @Order(21)
    @TcSteps("Nhân viên mở Quản lý khách hàng sau khi đặt tại quầy")
    @DisplayName("Tài khoản khách tại quầy được tạo tự động ¦ GET /admin/customers ¦ Danh sách có \"Khách Tại Quầy\" với email @khachvanglai.local")
    void s21_walkInAccount() {
        assertThat(staff.get("/admin/customers").body()).contains("Khách Tại Quầy").contains("@khachvanglai.local");
    }

    @Test
    @Order(22)
    @TcSteps("Quản trị viên tạo mã giảm giá mới qua form, khách kiểm tra mã; sau đó quản trị viên tắt mã, khách kiểm tra lại")
    @DisplayName("Vòng đời mã giảm giá trên server thật ¦ tạo mã → kiểm tra → tắt mã → kiểm tra lại ¦ Lần 1 hợp lệ, lần 2 báo \"đã ngừng áp dụng\"")
    void s22_discountLifecycle() {
        Browser admin = new Browser();
        admin.loginAdmin("admin@hotel.com", "admin123");
        String code = "ST" + seq();
        admin.get("/admin/discount-codes/new");
        assertThat(admin.post("/admin/discount-codes/new", form("code", code, "discountType", "PERCENTAGE", "discountValue", "15")).location())
                .isEqualTo("/admin/discount-codes");
        String check = "/customer/bookings/check-discount?code=" + code + "&roomId=" + room.getId() + "&checkIn=" + in.plusDays(30)
                + "&checkOut=" + in.plusDays(31);
        assertThat(new Browser().get(check).body()).contains("\"valid\":true");
        Long id = discountCodeRepository.findByCodeAndActiveTrue(code).orElseThrow().getId();
        admin.get("/admin/discount-codes");
        admin.post("/admin/discount-codes/" + id + "/toggle-active", form());
        assertThat(new Browser().get(check).body()).contains("\"valid\":false").contains("ngừng áp dụng");
    }

    @Test
    @Order(23)
    @TcSteps("Quản trị viên ẩn combo Lẩu Hành Trình, khách mở lại trang chi tiết phòng")
    @DisplayName("Combo bị ẩn biến mất phía khách ¦ POST /admin/combos/<id>/toggle-active rồi GET trang chi tiết phòng ¦ Không còn \"Lẩu Hành Trình\" trong danh sách chọn combo")
    void s23_hideCombo() {
        Browser admin = new Browser();
        admin.loginAdmin("admin@hotel.com", "admin123");
        admin.get("/admin/combos");
        admin.post("/admin/combos/" + combo.getId() + "/toggle-active", form());
        assertThat(new Browser().get("/customer/rooms/" + room.getId()).body()).doesNotContain("Lẩu Hành Trình");
    }
}
