# Sóng Xanh Resort - Website quản lý khách sạn du lịch biển

Đề tài 71 - Bài tập lớn học phần Đồ án chuyên ngành (Nhóm 17, Trường ĐH Công nghệ Đông Á).

Website cho một resort biển ở Hạ Long, gồm hai phần: **trang khách** để tìm và đặt phòng, đặt cọc, đặt dịch vụ, khai báo lưu trú, đánh giá; và **cổng quản trị** cho lễ tân, buồng phòng, kế toán và quản trị viên. Cổng quản trị có báo cáo lãi lỗ và trợ lý phân tích AI.

## Công nghệ

| Thành phần | Dùng |
|---|---|
| Ngôn ngữ / framework | Java 17, Spring Boot 3.3 (Web, Security, Data JPA, Mail, Validation) |
| Giao diện | Thymeleaf, Bootstrap 5, biểu đồ SVG vẽ phía server |
| Cơ sở dữ liệu | MariaDB / MySQL (máy dev dùng XAMPP), H2 cho kiểm thử |
| Thanh toán | VNPay (sandbox), chuyển khoản QR VietQR |
| AI | Claude API qua Java SDK chính thức `com.anthropic:anthropic-java` |
| Khác | Apache POI (Excel), OpenHTMLtoPDF (hóa đơn PDF), lịch iCal (.ics) cho kênh OTA |

## Chạy trên máy dev

Yêu cầu: JDK 17, Maven 3.9, XAMPP (MySQL chạy ở cổng 3306, tài khoản `root` không mật khẩu). Có thể chạy thẳng từ IntelliJ (lớp `HotelBookingApplication`).

```bash
# Lần đầu: DB beach_hotel tự tạo, bảng tự sinh (ddl-auto=update), có sẵn 60 phòng mẫu
mvn spring-boot:run
```

Mở <http://localhost:8080> (trang khách) và <http://localhost:8080/admin> (cổng quản trị).

| Tài khoản tạo sẵn | Email | Mật khẩu |
|---|---|---|
| Quản trị viên | `admin@hotel.com` | `admin123` |
| Lễ tân | `staff@hotel.com` | `staff123` |

### Dữ liệu demo (khoảng 16 tháng vận hành)

```bash
DEMO_DATA=true mvn spring-boot:run     # chỉ cần chạy 1 lần, khoảng 2 phút
```

Bộ sinh dữ liệu tạo khoảng 8.000 đơn đặt phòng từ 01/06/2025 đến nay, cộng các đơn đặt trước cho 4 tháng tới. Số đơn tăng giảm theo mùa (hè, Tết, các dịp lễ, cuối tuần). Kèm theo là khách Việt và khách nước ngoài, cọc, hủy, hoàn cọc, đoàn công ty, dịch vụ, khai báo lưu trú, đánh giá, tin nhắn, buồng phòng, sự cố, chi phí hằng tháng và kế hoạch năm. Email của khách và nhân viên demo đều có đuôi `@example.com`, nên hệ thống không gửi thư tới người thật. Tất cả dùng chung mật khẩu `Songxanh@2026`:

| Vai trò | Email |
|---|---|
| Lễ tân | `letan.thuhuong@example.com` |
| Buồng phòng | `buongphong.hanh@example.com` |
| Kế toán | `ketoan.bichngoc@example.com` |

Đăng nhập cổng quản trị có bước OTP gửi qua email. Mã OTP của tài khoản demo không tới được hộp thư nào, nên muốn dùng các tài khoản này trên máy dev thì chạy kèm `ADMIN_OTP=false`.

### Kiểm thử

```bash
mvn test             # hơn 1.900 test: unit, integration (MockMvc + H2), đồng thời, hệ thống
```

## Biến môi trường

| Biến | Ý nghĩa |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Chạy cấu hình production (`application-prod.properties`) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Kết nối CSDL (production) |
| `DDL_AUTO` | Mặc định `validate` ở production: schema phải được tạo trước bằng các file trong `sql/` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `STAFF_EMAIL`, `STAFF_PASSWORD` | Tài khoản tạo lần đầu (production bắt buộc) |
| `ADMIN_OTP` | Bật / tắt OTP khi đăng nhập cổng quản trị (mặc định bật) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Tài khoản SMTP Gmail gửi email (OTP, xác nhận, nhắc lịch, báo cáo) |
| `APP_BASE_URL` | Địa chỉ website, dùng cho link trong email |
| `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` | Thông tin merchant VNPay. Để trống thì ẩn nút thanh toán VNPay |
| `PAYMENT_BANK_ID`, `PAYMENT_ACCOUNT_NO`, `PAYMENT_ACCOUNT_NAME` | Tài khoản nhận chuyển khoản (mã QR) |
| `ANTHROPIC_API_KEY` | Khóa API Claude cho trang Phân tích & AI. Để trống thì chỉ có nhận định tự động |
| `AI_MODEL` | Mặc định `claude-haiku-5-5` (rẻ nhất); `claude-opus-5-5` cho phân tích sâu hơn |
| `AI_ENABLED`, `AI_FALLBACKS` | Tắt hẳn AI / chế độ dự phòng phía server (`auto`) |
| `DAILY_REPORT_ENABLED`, `DAILY_REPORT_TO`, `MONTHLY_REPORT_ENABLED` | Email báo cáo cuối ngày (23h) và báo cáo tháng (8h ngày 1) |
| `GUEST_EMAILS_ENABLED` | Email nhắc lịch nhận phòng và mời đánh giá |
| `UPLOAD_DIR`, `PAGE_IMAGES_DIR` | Thư mục lưu ảnh tải lên |
| `DEMO_DATA` | `true` để sinh dữ liệu demo (chỉ dùng trên máy dev) |

## Triển khai production

1. Tạo CSDL, rồi chạy lần lượt các file trong `sql/` theo thứ tự ngày. Mỗi file chạy lại nhiều lần vẫn an toàn.
2. Đặt các biến môi trường ở bảng trên với `SPRING_PROFILES_ACTIVE=prod`.
3. `mvn -DskipTests package`, rồi chạy `java -jar target/beach-hotel-1.0.0.jar`.
4. Khai báo IPN URL `https://<tên-miền>/payment/vnpay/ipn` trong trang merchant VNPay.

## Cấu trúc mã nguồn

```
src/main/java/com/hotel/
  config/        cấu hình, bộ hẹn giờ, dữ liệu mẫu (config/demo = dữ liệu demo)
  controller/    controller trang khách + cổng quản trị (Admin*Controller)
  service/       nghiệp vụ; service/ai = trợ lý phân tích Claude
  repository/    Spring Data JPA
  entity/        thực thể JPA (hơn 40 bảng)
  security/      đăng nhập, phân quyền, OTP
src/main/resources/templates/   giao diện Thymeleaf (admin/ = cổng quản trị)
sql/             migration cho production (idempotent)
docs/            báo cáo, sơ đồ use case / ERD / kiến trúc (draw.io)
TIEN-DO.md       nhật ký tiến độ: đã làm gì, lưu ý triển khai, việc tiếp theo
```

## Tài liệu

- `docs/BaoCao-DoAn.docx`: báo cáo đồ án (tác nhân, use case, thiết kế, kiểm thử, kịch bản demo)
- `docs/use-case/*.drawio`, `docs/thiet-ke/*.drawio`: sơ đồ, mở bằng <https://app.diagrams.net> hoặc draw.io Desktop
