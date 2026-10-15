# Tiến độ dự án Sóng Xanh Resort (beach-hotel)

> File bàn giao giữa các phiên làm việc với Claude. Mở phiên mới, bảo Claude: **"đọc file TIEN-DO.md"**.
> Cập nhật lần cuối: 16/10/2026, sau commit `31b43c2`, 1814 test xanh.

## 1. Dự án

- Spring Boot 3.3.4 + Thymeleaf + Spring Security + JPA (Hibernate 6.5). Website đặt phòng resort ở Hạ Long, song ngữ Việt/Anh.
- DB dev: MariaDB (XAMPP), DB `beach_hotel`, user `root` không mật khẩu, `ddl-auto=update`. Test dùng H2 (MySQL mode).
- Môi trường thật dùng `DDL_AUTO=validate`, nên **mọi thay đổi cấu trúc DB phải có file migration** trong `sql/`.
- Repo: https://github.com/ngynquchn2511/beach-hotel, nhánh `main`. Commit và push bằng tài khoản của chủ dự án `ngynquchn2511`; không đổi danh tính git.
- Bản app chạy trong IntelliJ (cổng 8080) cần khởi động lại sau mỗi lần cập nhật code.

## 2. Cách làm việc đã thống nhất

1. Chủ dự án hỏi "giờ cần thêm chức năng gì", Claude đề xuất danh sách. Chủ dự án trả lời "làm hết theo thứ tự".
2. Làm từng chức năng một, **không hỏi lại**: code → viết test → chạy **toàn bộ** `mvn test` → commit → **push ngay sau mỗi chức năng**.
3. Commit message viết tiếng Việt **không dấu**, kết thúc bằng dòng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
4. Đổi cấu trúc DB thì:
   - thêm file `sql/2026-10-16_<ten>.sql` chạy lại nhiều lần vẫn an toàn (`CREATE TABLE IF NOT EXISTS`, thủ tục `hb_add_column`, `ALTER ... MODIFY` cho cột ENUM);
   - kiểm chứng trên DB nháp `beach_hotel_migtest`: dump cấu trúc DB dev → chạy các file sql 2 lần → chạy app ở cổng 8082 với `ddl-auto=validate` → thấy "Started" thì dừng app và xóa DB nháp.
5. Cột enum của Hibernate là ENUM trong MySQL. Thêm giá trị enum mới (vai trò, loại phụ phí...) thì **phải** `ALTER ... MODIFY` trong migration và áp luôn vào DB dev.
6. Mẹo kỹ thuật:
   - Sửa file lớn bằng script Python lưu ở thư mục tạm, không dùng heredoc của bash.
   - Phụ thuộc mới của service lớn (BookingService, controller lớn) thì inject qua setter `@Autowired` và kiểm tra null.
   - Danh sách nào cũng phân trang bằng `Pager` + `fragments/pagination`.

## 3. Đã làm (theo thứ tự gần nhất trước)

### Đợt 3 (16/10/2026)
| Commit | Chức năng |
|---|---|
| `31b43c2` | Tổng quan: so sánh tiền đã thu / công suất / ADR / RevPAR / số đơn với kỳ trước và cùng kỳ năm trước |
| `c939ea6` | Đặt spa (4 gói cố định trong `SpaTreatment`) và bàn nhà hàng trên trang đơn; lễ tân xác nhận ở `/admin/facility-reservations`; xác nhận spa thì phụ phí SPA được cộng vào đơn |
| `088576f` | Giá thành viên: % giảm theo hạng Khách mới / Khách quen / VIP, đặt ở `/admin/pricing` (**mặc định 0 = tắt**) |
| `3b87d96` | Cọc cả đoàn qua VNPay (`vnp_TxnRef` = `G<id>_...`); hoàn cọc 1 phòng của đoàn dùng loại giao dịch 03 |
| `6f33ffe` | Vai trò HOUSEKEEPER (Buồng phòng) và ACCOUNTANT (Kế toán); trang `/admin/home` chuyển hướng theo vai trò |
| `4f8ea8a` | Sổ quỹ ca và bàn giao ca `/admin/shifts` |
| `5e2b169` | Hóa đơn công ty (VAT): mã số thuế trên đơn, trang kế toán `/admin/company-invoices`, xuất Excel |

### Đợt 2
`a310878` Sổ đồ thất lạc · `a9a9b5f` Thẻ quà tặng · `45101d7` Dự báo công suất · `ca0a8ea` Nhắc chuyển cọc · `8551202` Đặt nhiều phòng (đoàn) · `e7b1ac8` Báo hỏng / bảo trì phòng · `1513a6a` Đổi phòng

### Trước đó (tóm tắt)
- **Đặt phòng và thanh toán:** đặt phòng online, đặt tại quầy, khách tự đổi ngày; đặt cọc chuyển khoản QR, đặt cọc và hoàn cọc qua VNPay sandbox; chính sách hoàn cọc; khuyến mãi tự động; mã giảm giá; điểm thưởng.
- **Dịch vụ thêm:** nhận phòng sớm / trả phòng muộn; gọi dịch vụ lên phòng; tour lặn; đưa đón sân bay; combo.
- **Lễ tân và vận hành:** khai báo lưu trú online; bảng dọn phòng; lịch phòng; đồng bộ iCal; hóa đơn PDF; nhật ký thao tác; tài khoản nhân viên; OTP cho cổng quản trị.
- **Phía khách:** đánh giá kèm ảnh/video và phản hồi; phòng yêu thích; so sánh phòng; báo khi phòng trống lại; hộp thư liên hệ; song ngữ và giá USD tham khảo; 50 phòng mẫu; phân trang mọi danh sách.

## 4. Lưu ý khi deploy lên server

- Chạy **tất cả** các file trong `sql/` theo thứ tự tên file (`2026-10-10_*`, `2026-10-15_*`, rồi `2026-10-16_*`) **trước khi** chạy bản mới.
- Biến môi trường:
  - VNPay: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`. Chưa thử với sandbox thật, chỉ mới có test tự động.
  - Mail: OTP quản trị tự bật khi đã cấu hình mail; đặt `ADMIN_OTP=false` để tắt.
- Tài khoản admin `ngqhoan214@gmail.com` mới chỉ có trên DB dev. Server cần tạo lại tài khoản này.

## 5. Hạn chế đã biết

- Đơn đoàn chưa dùng được mã giảm giá, điểm thưởng, thẻ quà tặng hay giá thành viên.
- Gói spa và giá spa cố định trong code; admin chưa sửa được trên giao diện.
- Sức chứa spa (3 lượt / khung giờ) chưa tính việc gói 90 phút chồng sang khung giờ kế tiếp.
- Công suất các kỳ cũ được tính theo số phòng hiện tại.

## 6. Gợi ý việc làm tiếp (chưa làm)

1. Trang quản lý gói spa (thêm, sửa giá, ẩn gói), giống trang thực đơn gọi lên phòng.
2. Cho đơn đoàn dùng mã giảm giá, điểm thưởng và giá thành viên.
3. Báo cáo cho kế toán: xuất Excel sổ quỹ theo tháng, đối soát VNPay theo ngày.
4. Thông báo tức thời cho lễ tân (đơn mới, lịch spa mới, yêu cầu gọi lên phòng) bằng SSE/WebSocket thay vì tải lại trang.
5. Trang tự phục vụ của khách: lịch sử điểm thưởng, tải hóa đơn PDF, quản lý thẻ quà tặng đã mua.
6. Đánh giá sau khi dùng spa / nhà hàng.
7. Thử VNPay sandbox thật trước khi go-live: cọc đơn lẻ, cọc đoàn, mua thẻ quà tặng, hoàn cọc.
