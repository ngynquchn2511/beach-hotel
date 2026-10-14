package com.hotel.system;

import com.hotel.entity.*;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@TcSuite(level = "ST", module = "Hệ thống: giao diện, xác thực, phân quyền, chatbot (HTTP thật)")
class SystemParamST extends StBase {

    // ===================== Nội dung trang công khai =====================

    @TcSteps("Khởi động ứng dụng thật, trình duyệt (HttpClient) mở trang chưa đăng nhập, đọc HTML trả về")
    @ParameterizedTest(name = "Trang {0} hiển thị \"{1}\" ¦ GET {0} (chưa đăng nhập) ¦ HTTP 200, HTML chứa \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "/|Tìm phòng, xem thông tin và đặt phòng trực tuyến", "/|Xem danh sách phòng", "/|Hỏi Sóng", "/|Đăng nhập", "/|Đăng ký",
            "/rooms|Danh sách phòng", "/rooms|Tìm theo giá", "/rooms|Loại phòng", "/rooms|Từ 1.300.000 VND",
            "/login|Chào mừng trở lại", "/login|Đăng ký ngay", "/login|name=\"username\"", "/login|name=\"password\"",
            "/register|name=\"fullName\"", "/register|name=\"phoneNumber\"", "/register|name=\"confirmPassword\"",
            "/admin|Cổng quản trị", "/admin|Email công việc", "/admin|Quay lại đăng nhập khách hàng",
            "/customer/rooms|Tìm phòng", "/customer/rooms|Ngày nhận phòng", "/customer/rooms|Số khách", "/customer/rooms|Loại phòng"})
    void publicPageContent(String path, String text) {
        Res r = new Browser().get(path);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(text);
    }

    @TcSteps("Mở trang và đọc header Content-Type của phản hồi")
    @ParameterizedTest(name = "Trang {0} trả về HTML mã hóa UTF-8 ¦ GET {0} ¦ Content-Type: text/html;charset=UTF-8, tiếng Việt hiển thị đúng")
    @ValueSource(strings = {"/", "/rooms", "/login", "/register", "/admin", "/customer/rooms"})
    void utf8(String path) {
        Res r = new Browser().get(path);
        assertThat(r.raw().headers().firstValue("Content-Type").orElse("")).containsIgnoringCase("text/html").containsIgnoringCase("UTF-8");
        assertThat(r.body()).doesNotContain("Ã");
    }

    @TcSteps("Mở trang và đọc các header bảo mật mặc định của Spring Security")
    @ParameterizedTest(name = "Header bảo mật {1} trên trang {0} ¦ GET {0} ¦ Có header {1}: {2}")
    @CsvSource({"/login,X-Content-Type-Options,nosniff", "/login,X-Frame-Options,DENY", "/admin,X-Content-Type-Options,nosniff",
            "/admin,X-Frame-Options,DENY", "/customer/rooms,Cache-Control,no-cache"})
    void securityHeaders(String path, String header, String value) {
        Res r = new Browser().get(path);
        assertThat(r.raw().headers().firstValue(header).orElse("")).containsIgnoringCase(value);
    }

    // ===================== Đăng nhập / đăng xuất thật =====================

    @TcSteps("Tạo tài khoản, mở trang đăng nhập lấy CSRF, gửi form đăng nhập thật qua HTTP, đọc header Location")
    @ParameterizedTest(name = "Đăng nhập cổng {0} bằng tài khoản {1}, mật khẩu {2} ¦ POST {3} ¦ Chuyển hướng tới \"{4}\"")
    @CsvSource({"khách hàng,CUSTOMER,đúng,/login,/customer/rooms", "khách hàng,CUSTOMER,sai,/login,/login?error=true",
            "khách hàng,STAFF,đúng,/login,/login?error=wrong_portal", "khách hàng,ADMIN,đúng,/login,/login?error=wrong_portal",
            "quản trị,STAFF,đúng,/admin/login,/admin/dashboard", "quản trị,ADMIN,đúng,/admin/login,/admin/dashboard",
            "quản trị,ADMIN,sai,/admin/login,/admin?error=true", "quản trị,CUSTOMER,đúng,/admin/login,/admin?error=wrong_portal"})
    void login(String portal, UserRole role, String pw, String endpoint, String target) {
        User u = createUser(role, CustomerType.NEW, "matkhau123");
        Browser b = new Browser();
        b.get(endpoint.equals("/login") ? "/login" : "/admin");
        Res r = b.post(endpoint, form("username", u.getEmail(), "password", pw.equals("đúng") ? "matkhau123" : "saimatkhau"));
        assertThat(r.status()).isEqualTo(302);
        assertThat(r.location()).isEqualTo(target);
    }

    @TcSteps("Gửi form đăng nhập với email không tồn tại trong hệ thống")
    @ParameterizedTest(name = "Đăng nhập bằng email không tồn tại ở cổng {0} ¦ POST {0} email=khongton@x.vn ¦ Chuyển hướng tới \"{1}\"")
    @CsvSource({"/login,/login?error=true", "/admin/login,/admin?error=true"})
    void loginUnknownEmail(String endpoint, String target) {
        Browser b = new Browser();
        b.get("/login");
        assertThat(b.post(endpoint, form("username", "khongton@x.vn", "password", "abc123")).location()).isEqualTo(target);
    }

    @TcSteps("Đăng nhập thật, sau đó gửi POST đăng xuất, rồi truy cập lại trang cần đăng nhập")
    @ParameterizedTest(name = "Đăng xuất {0} ¦ POST {1} ¦ Chuyển tới \"{2}\"; truy cập lại {3} bị đưa về trang đăng nhập")
    @CsvSource({"khách hàng,/logout,/login?logout=true,/customer/bookings", "nhân viên,/admin/logout,/admin?logout=true,/admin/dashboard"})
    void logout(String who, String endpoint, String target, String protectedPage) {
        Browser b = new Browser();
        if (who.equals("khách hàng")) {
            b.loginCustomer(createUser(UserRole.CUSTOMER, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        } else {
            b.loginAdmin(createUser(UserRole.STAFF, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        }
        assertThat(b.get(protectedPage).status()).isEqualTo(200);
        b.get(who.equals("khách hàng") ? "/" : "/admin/dashboard");
        assertThat(b.post(endpoint, form()).location()).isEqualTo(target);
        Res again = b.get(protectedPage);
        assertThat(again.status()).isEqualTo(302);
        assertThat(again.location()).contains(who.equals("khách hàng") ? "/login" : "/admin");
    }

    @TcSteps("Đăng nhập bằng tài khoản mặc định do DataInitializer tạo khi khởi động")
    @ParameterizedTest(name = "Tài khoản mặc định {0} đăng nhập được ¦ {0} / {1} ¦ Vào được /admin/dashboard")
    @CsvSource({"admin@hotel.com,admin123", "staff@hotel.com,staff123"})
    void defaultAccounts(String email, String pw) {
        Browser b = new Browser();
        b.get("/admin");
        assertThat(b.post("/admin/login", form("username", email, "password", pw)).location()).isEqualTo("/admin/dashboard");
        assertThat(b.get("/admin/dashboard").body()).contains("Tổng quan hệ thống");
    }

    // ===================== Đăng ký =====================

    @TcSteps("Mở /register lấy CSRF, gửi form đăng ký thật, thử đăng nhập (chưa kích hoạt), bấm link kích hoạt trong email, đăng nhập lại")
    @ParameterizedTest(name = "Đăng ký tài khoản - {0} ¦ phone=\"{1}\", password=\"{2}\", confirm=\"{3}\" ¦ Thành công = {4}")
    @CsvSource({"dữ liệu hợp lệ,0912000001,matkhau123,matkhau123,true", "mật khẩu xác nhận khác,0912000002,matkhau123,khac123,false",
            "mật khẩu 5 ký tự,0912000003,abcde,abcde,false", "SĐT 9 số,091200000,matkhau123,matkhau123,false",
            "SĐT có chữ cái,09120000ab,matkhau123,matkhau123,false", "SĐT đầu +84,+84912000004,matkhau123,matkhau123,false"})
    void register(String label, String phone, String pw, String confirm, boolean ok) {
        Browser b = new Browser();
        b.get("/register");
        String email = "dk" + seq() + "@gmail.com";
        Res r = b.post("/register", form("fullName", "Người Đăng Ký", "email", email, "phoneNumber", phone, "password", pw, "confirmPassword", confirm));
        if (ok) {
            assertThat(r.location()).isEqualTo("/login?registered=true");
            // Chua bam link kich hoat -> chua dang nhap duoc
            Browser b2 = new Browser();
            b2.get("/login");
            assertThat(b2.post("/login", form("username", email, "password", pw)).location()).isEqualTo("/login?unverified=true");
            // Bam link kich hoat (ma trong email) -> dang nhap duoc
            String token = userRepository.findByEmail(email).orElseThrow().getEmailVerifyToken();
            assertThat(new Browser().get("/verify-email?token=" + token).location()).isEqualTo("/login?verified=true");
            Browser b3 = new Browser();
            b3.get("/login");
            assertThat(b3.post("/login", form("username", email, "password", pw)).location()).isEqualTo("/customer/rooms");
        } else {
            assertThat(r.status()).isEqualTo(200);
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }
    }

    @TcSteps("Đăng ký 2 lần cùng một email qua form thật (lần 1 đã bấm link kích hoạt)")
    @ParameterizedTest(name = "Đăng ký trùng email (viết {0}) ¦ email lần 2 viết {0} ¦ Lần 2 bị từ chối, chỉ có 1 tài khoản")
    @ValueSource(strings = {"giống hệt"})
    void registerDuplicate(String variant) {
        String email = "trung" + seq() + "@gmail.com";
        for (int i = 0; i < 2; i++) {
            Browser b = new Browser();
            b.get("/register");
            Res r = b.post("/register", form("fullName", "Trùng", "email", email, "phoneNumber", "0913000000", "password", "matkhau123",
                    "confirmPassword", "matkhau123"));
            if (i == 0) {
                String token = userRepository.findByEmail(email).orElseThrow().getEmailVerifyToken();
                new Browser().get("/verify-email?token=" + token);
            }
            if (i == 1) {
                assertThat(r.status()).isEqualTo(200);
                assertThat(r.body()).contains("đã được đăng ký");
            }
        }
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)).count()).isEqualTo(1);
    }

    // ===================== Phiên làm việc theo vai trò =====================

    @TcSteps("Đăng nhập thật vào cổng quản trị bằng vai trò R, mở trang quản trị, đọc HTML")
    @ParameterizedTest(name = "{1} mở trang {0} ¦ phiên đăng nhập {1}, GET {0} ¦ HTTP 200, hiển thị \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "/admin/dashboard|STAFF|Tổng quan hệ thống", "/admin/bookings|STAFF|Quản lý đặt phòng", "/admin/rooms|STAFF|Danh sách phòng",
            "/admin/room-types|STAFF|Danh sách loại phòng", "/admin/combos|STAFF|Danh sách combo", "/admin/discount-codes|STAFF|Mã giảm giá",
            "/admin/customers|STAFF|Danh sách khách hàng", "/admin/bookings/walk-in/new|STAFF|Đặt phòng tại quầy",
            "/admin/dashboard|ADMIN|Đăng nhập thành công với quyền Quản trị viên", "/admin/dashboard|STAFF|Đăng nhập thành công với quyền Nhân viên",
            "/admin/rooms/new|ADMIN|Thêm phòng", "/admin/room-types/new|ADMIN|Thêm loại phòng", "/admin/combos/new|ADMIN|Thêm combo",
            "/admin/discount-codes/new|ADMIN|Thêm mã giảm giá"})
    void adminPages(String path, UserRole role, String text) {
        Browser b = new Browser();
        b.loginAdmin(createUser(role, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        Res r = b.get(path);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(text);
    }

    @TcSteps("Đăng nhập thật bằng vai trò R, mở trang không thuộc quyền của vai trò đó")
    @ParameterizedTest(name = "{1} mở trang {0} ¦ phiên đăng nhập {1}, GET {0} ¦ HTTP 403 Forbidden")
    @CsvSource({"/admin/staff-accounts,STAFF", "/admin/staff-accounts/new,STAFF", "/admin/audit-logs,STAFF",
            "/admin/dashboard,CUSTOMER", "/admin/bookings,CUSTOMER", "/admin/rooms,CUSTOMER", "/customer/bookings,STAFF"})
    void forbiddenPages(String path, UserRole role) {
        Browser b = new Browser();
        User u = createUser(role, CustomerType.NEW, "matkhau123");
        if (role == UserRole.CUSTOMER) {
            b.loginCustomer(u.getEmail(), "matkhau123");
        } else {
            b.loginAdmin(u.getEmail(), "matkhau123");
        }
        assertThat(b.get(path).status()).isEqualTo(403);
    }

    // ===================== Điều hướng nhất quán =====================

    @TcSteps("Đăng nhập nhân viên, mở trang quản trị, kiểm tra liên kết logo/menu trên header")
    @ParameterizedTest(name = "Header trang {0} giữ nhân viên trong khu quản trị ¦ phiên STAFF, GET {0} ¦ Logo trỏ /admin/dashboard, \"Danh sách phòng\" trỏ /admin/rooms")
    @ValueSource(strings = {"/admin/dashboard", "/admin/bookings", "/admin/bookings/walk-in/new", "/admin/rooms", "/admin/combos", "/admin/customers"})
    void adminHeaderLinks(String path) {
        Browser b = new Browser();
        b.loginAdmin(createUser(UserRole.STAFF, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        String html = b.get(path).body();
        String header = html.substring(html.indexOf("<header"), html.indexOf("</header>"));
        assertThat(header).contains("href=\"/admin/dashboard\"").contains("href=\"/admin/rooms\"")
                .doesNotContain("href=\"/\"").doesNotContain("href=\"/rooms\"");
    }

    @TcSteps("Đăng nhập nhân viên, mở trang quản trị, kiểm tra TẤT CẢ liên kết trên trang (header + footer)")
    @ParameterizedTest(name = "Trang {0} không có liên kết nào dẫn sang giao diện khách hàng ¦ phiên STAFF, GET {0} ¦ Không có href=\"/\" hoặc href=\"/rooms\" ở bất kỳ đâu")
    @ValueSource(strings = {"/admin/dashboard", "/admin/bookings", "/admin/bookings/walk-in/new", "/admin/rooms"})
    void adminNoCustomerLinks(String path) {
        Browser b = new Browser();
        b.loginAdmin(createUser(UserRole.STAFF, CustomerType.NEW, "matkhau123").getEmail(), "matkhau123");
        String html = b.get(path).body();
        assertThat(html).doesNotContain("href=\"/\"").doesNotContain("href=\"/rooms\"");
    }

    @TcSteps("Mở trang phía khách hàng, kiểm tra liên kết logo trên header")
    @ParameterizedTest(name = "Header trang khách {0} ¦ GET {0} (chưa đăng nhập) ¦ Logo trỏ về trang chủ \"/\", có link Đăng nhập/Đăng ký")
    @ValueSource(strings = {"/", "/rooms", "/customer/rooms"})
    void customerHeaderLinks(String path) {
        String html = new Browser().get(path).body();
        String header = html.substring(html.indexOf("<header"), html.indexOf("</header>"));
        assertThat(header).contains("href=\"/\"").contains("href=\"/login\"").contains("href=\"/register\"");
    }

    // ===================== Xử lý lỗi =====================

    @TcSteps("Mở trang chi tiết với id không tồn tại qua HTTP thật")
    @ParameterizedTest(name = "Mở {0} với id không tồn tại ¦ GET {1} ¦ HTTP 404, trang lỗi thân thiện (không lộ stack trace)")
    @CsvSource({"phòng,/customer/rooms/999999", "đơn đặt phòng,/customer/bookings/999999"})
    void friendlyNotFound(String what, String path) {
        Res r = new Browser().get(path);
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body()).doesNotContain("Exception").doesNotContain("at com.hotel");
    }

    // ===================== Chatbot qua HTTP =====================

    @TcSteps("Mở trang chủ lấy CSRF token của widget chatbot, gửi POST /api/chatbot/ask (JSON) như trình duyệt")
    @ParameterizedTest(name = "Chatbot trên server thật trả lời ¦ câu hỏi: \"{0}\" ¦ HTTP 200, reply chứa \"{1}\"")
    @CsvSource(delimiter = '|', value = {"Resort ở đâu?|Bãi Cháy", "Số điện thoại liên hệ|0338932368", "email resort|songxanhresort@gmail.com",
            "Có cần đặt cọc không|đặt cọc", "Thanh toán thế nào|Chuyển khoản", "Mấy giờ trả phòng|Giờ trả phòng",
            "Có ban công nhìn biển không|Ban công hướng vịnh", "xin chào|Mình là **Sóng**", "Giá phòng bao nhiêu|Giá phòng", "câu hỏi linh tinh|Sóng chưa hiểu lắm"})
    void chatbot(String q, String expected) {
        Browser b = new Browser();
        String token = b.chatbotToken("/");
        Res r = b.postJson("/api/chatbot/ask", "{\"message\":\"" + q + "\"}", token);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().toLowerCase()).contains(expected.toLowerCase());
    }

    @TcSteps("Gửi POST /api/chatbot/ask không kèm CSRF token")
    @ParameterizedTest(name = "Chatbot chặn request thiếu CSRF ¦ POST /api/chatbot/ask không có X-CSRF-TOKEN, câu hỏi \"{0}\" ¦ Request bị Spring Security chặn, không trả về câu trả lời (không phải HTTP 200)")
    @ValueSource(strings = {"xin chào", "Giá phòng bao nhiêu"})
    void chatbotNeedsCsrf(String q) {
        Browser b = new Browser();
        b.get("/");
        Res r = b.postJson("/api/chatbot/ask", "{\"message\":\"" + q + "\"}", null);
        assertThat(r.status()).isNotEqualTo(200);
        assertThat(r.body()).doesNotContain("\"reply\"");
    }

    // ===================== Tìm phòng trên server thật =====================

    @TcSteps("Tạo phòng mới, mở /customer/rooms với khoảng ngày theo bộ dữ liệu, tìm số phòng trong HTML")
    @ParameterizedTest(name = "Tìm phòng trên giao diện - {0} ¦ checkIn=+{1}, checkOut=+{2} ¦ {3}")
    @CsvSource({"ngày hợp lệ,1,3,Hiển thị phòng vừa tạo", "ngày nhận ở quá khứ,-2,1,Hiển thị thông báo lỗi quá khứ",
            "ngày trả trước ngày nhận,4,2,Hiển thị thông báo lỗi ngày trả"})
    void searchUi(String label, int in, int out, String expected) {
        Room room = createRoom(createType("Loại ST " + seq(), 250000, 2), 250000);
        Res r = new Browser().get("/customer/rooms?checkIn=" + LocalDate.now().plusDays(in) + "&checkOut=" + LocalDate.now().plusDays(out));
        assertThat(r.status()).isEqualTo(200);
        if (expected.startsWith("Hiển thị phòng")) {
            assertThat(r.body()).contains(room.getRoomNumber());
        } else if (expected.contains("quá khứ")) {
            assertThat(r.body()).contains("không được ở trong quá khứ");
        } else {
            assertThat(r.body()).contains("Ngày trả phòng phải lớn hơn ngày nhận phòng");
        }
    }
}
