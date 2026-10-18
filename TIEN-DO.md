# Tiến độ dự án Sóng Xanh Resort (beach-hotel)

> File bàn giao giữa các phiên làm việc với Claude. Mở phiên mới, bảo Claude: **"đọc file TIEN-DO.md"**.
> Cập nhật lần cuối: 18/10/2026, sau commit `ca45ae8`, 1870 test xanh.

## 1. Dự án

- Spring Boot 3.3.4 + Thymeleaf + Spring Security + JPA (Hibernate 6.5). Website đặt phòng resort ở Hạ Long, song ngữ Việt/Anh.
- DB dev: MariaDB (XAMPP), DB `beach_hotel`, user `root` không mật khẩu, `ddl-auto=update`. Test dùng H2 (MySQL mode).
- Môi trường thật dùng `DDL_AUTO=validate`, nên **mọi thay đổi cấu trúc DB phải có file migration** trong `sql/`.
- Repo: https://github.com/ngynquchn2511/beach-hotel, nhánh `main`. Commit và push bằng tài khoản của chủ dự án `ngynquchn2511`; không đổi danh tính git.
- Bản app chạy trong IntelliJ (cổng 8080) cần khởi động lại sau mỗi lần cập nhật code.
- Máy không có `mvn` trong PATH: dùng `C:\Users\Dell\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`.
- MariaDB của XAMPP có thể đang tắt: bật bằng `C:\xampp\mysql\bin\mysqld.exe --defaults-file=C:\xampp\mysql\bin\my.ini --standalone`.

## 2. Cách làm việc đã thống nhất

1. Chủ dự án hỏi "giờ cần thêm chức năng gì", Claude đề xuất danh sách. Chủ dự án trả lời "làm hết theo thứ tự".
   Chủ dự án đã nói rõ: **cứ tự làm, không cần hỏi, thêm chức năng cần thiết** theo đánh giá chuyên môn.
2. Làm từng chức năng một, **không hỏi lại**: code → viết test → chạy **toàn bộ** `mvn test` → commit → **push ngay sau mỗi chức năng**.
3. Commit message viết tiếng Việt **không dấu**, kết thúc bằng dòng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
4. Đổi cấu trúc DB thì:
   - thêm file `sql/<ngày>_<ten>.sql` chạy lại nhiều lần vẫn an toàn (`CREATE TABLE IF NOT EXISTS`, thủ tục `hb_add_column`, `ALTER ... MODIFY` cho cột ENUM);
   - kiểm chứng trên DB nháp `beach_hotel_migtest`: dump cấu trúc DB dev → chạy file sql 2 lần → chạy app ở cổng 8082 với `ddl-auto=validate` → thấy "Started" thì dừng app và xóa DB nháp → áp file sql vào DB dev.
     Claude viết sẵn các bước này thành script PowerShell `migcheck.ps1 -SqlFile <file>` trong thư mục tạm của phiên (mất khi đổi phiên, viết lại theo các bước trên).
5. Cột enum của Hibernate là ENUM trong MySQL. Thêm giá trị enum mới (vai trò, loại phụ phí...) thì **phải** `ALTER ... MODIFY` trong migration và áp luôn vào DB dev.
6. Mẹo kỹ thuật:
   - Sửa file lớn bằng script Python lưu ở thư mục tạm (Write tool tạo script, hàm `sub(path, old, new)` báo lỗi nếu không thấy chuỗi), không dùng heredoc của bash.
   - Phụ thuộc mới của service lớn (BookingService, controller lớn) thì inject qua setter `@Autowired` và kiểm tra null.
   - Danh sách nào cũng phân trang bằng `Pager` + `fragments/pagination :: pager(${pager})`.
   - Test IT (`ItFixtures`) chạy trong 1 giao dịch không commit: service ném lỗi thì DB trong test **không** rollback; thông báo lễ tân (gửi sau commit) phải tự gọi `afterCommit` (xem `StaffNotificationIT`).
   - MockMvc đọc JSON không charset là ISO-8859-1: dùng `getContentAsString(StandardCharsets.UTF_8)`.

## 3. Đã làm (theo thứ tự gần nhất trước)

### Đợt 5 (18/10/2026)
| Commit | Chức năng |
|---|---|
| `ca45ae8` | Trang `/services`: mục Spa & nhà hàng (gói đang bán, số sao, góp ý tiêu biểu từ 4 sao, tên khách rút gọn "Lan N.") |
| `2430006` | Đối soát VNPay đọc thẳng file gốc cổng merchant: tự nhận cột mã GD / số tiền / trạng thái theo tiêu đề, bỏ dòng thất bại, `vnp_Amount` chia 100 |
| `ea6a128` | Thời lượng spa chốt vào từng lịch (`duration_minutes`), sửa gói không đổi lịch đã đặt |
| `cf2b506` | Phân công dọn phòng (giao phòng cho nhân viên buồng, lọc "Phòng của tôi"), bước kiểm phòng bật/tắt trên `/admin/housekeeping`; thông báo tức thời theo nhóm: `Audience.FRONT_DESK` / `HOUSEKEEPING` + người nhận riêng |
| `e3757ef` | Báo cáo cuối ngày `/admin/reports/daily` (xem / in; ADMIN gửi email ngay), tự gửi 23h30 (`DailyReportScheduler`) |
| `a4168c6` | Giá không hoàn tiền: giảm % (cài ở `/admin/pricing`), trả trước 100% qua cọc, hủy không hoàn, không tự đổi ngày |
| `d01ed85` | Luật bán phòng (`stay_rules`): số đêm tối thiểu theo ngày nhận, đóng bán theo ngày / loại phòng, đặt trước tối đa N ngày; áp cho online / đoàn / khách đổi ngày, không chặn đặt tại quầy |
| `dbce957` | Khách không đến (no-show): đơn đóng như hủy (status CANCELLED + cờ `noShow`), giữ cọc, trả phòng; cảnh báo ở đơn sau của khách; nhắc lễ tân 23h |

### Đợt 4 (18/10/2026)
| Commit | Chức năng |
|---|---|
| `93d6326` | Đánh giá spa / nhà hàng sau khi dùng (1–5 sao + góp ý, 1 lần, đến 7 ngày sau trả phòng); điểm trung bình theo gói spa ở trang admin và trong ô chọn gói của khách; ≤ 2 sao báo ngay cho lễ tân |
| `198adc9` | Khách tự phục vụ `/customer/account/points`, `/invoices`, `/gift-vouchers`: toàn bộ lịch sử điểm + điểm dự kiến, hóa đơn đã thanh toán kèm trạng thái VAT, thẻ quà tặng đã mua / được tặng (số dư, đơn đã dùng, gửi lại email cho người nhận) |
| `73c6f78` | Thông báo tức thời cho lễ tân qua SSE `/admin/notifications/stream` (`StaffNotificationService`, `static/js/staff-notify.js`): đơn mới, đoàn mới, cọc VNPay về, nhận sớm / trả muộn, spa / đặt bàn, gọi lên phòng, tour / đưa đón, liên hệ. Gửi sau commit, giữ 50 cái gần nhất để nhận bù khi kết nối lại |
| `2690666` | Báo cáo kế toán (ADMIN + ACCOUNTANT): `/admin/accounting/ledger` sổ quỹ tháng + Excel 3 sheet; `/admin/accounting/vnpay` đối soát VNPay theo ngày với file sao kê .csv / .xlsx (cột 1 mã GD, cột 2 số tiền) |
| `3edcc89` | Đơn đoàn dùng mã giảm giá (mã % áp mọi phòng, mã tiền cố định trừ 1 lần), thẻ quà tặng (trừ dần), điểm thưởng (chia dần, mỗi phòng tối đa 50%) và giá thành viên |
| `1a286da` | Gói spa vào bảng `spa_packages`, admin quản lý ở `/admin/spa-packages` (thêm, sửa giá / thời lượng, tạm ngừng); kiểm tra giường trống theo thời lượng gói (gói 90 phút chiếm cả khung giờ sau) |

### Đợt 3 (16/10/2026)
`31b43c2` So sánh kỳ trước / cùng kỳ năm trước · `c939ea6` Spa và bàn nhà hàng · `088576f` Giá thành viên · `3b87d96` Cọc đoàn qua VNPay · `6f33ffe` Vai trò Buồng phòng / Kế toán · `4f8ea8a` Sổ quỹ ca · `5e2b169` Hóa đơn công ty (VAT)

### Đợt 2
`a310878` Sổ đồ thất lạc · `a9a9b5f` Thẻ quà tặng · `45101d7` Dự báo công suất · `ca0a8ea` Nhắc chuyển cọc · `8551202` Đặt nhiều phòng (đoàn) · `e7b1ac8` Báo hỏng / bảo trì phòng · `1513a6a` Đổi phòng

### Trước đó (tóm tắt)
- **Đặt phòng và thanh toán:** đặt phòng online, đặt tại quầy, khách tự đổi ngày; đặt cọc chuyển khoản QR, đặt cọc và hoàn cọc qua VNPay sandbox; chính sách hoàn cọc; khuyến mãi tự động; mã giảm giá; điểm thưởng.
- **Dịch vụ thêm:** nhận phòng sớm / trả phòng muộn; gọi dịch vụ lên phòng; tour lặn; đưa đón sân bay; combo.
- **Lễ tân và vận hành:** khai báo lưu trú online; bảng dọn phòng; lịch phòng; đồng bộ iCal; hóa đơn PDF; nhật ký thao tác; tài khoản nhân viên; OTP cho cổng quản trị.
- **Phía khách:** đánh giá kèm ảnh/video và phản hồi; phòng yêu thích; so sánh phòng; báo khi phòng trống lại; hộp thư liên hệ; song ngữ và giá USD tham khảo; 50 phòng mẫu; phân trang mọi danh sách.

## 4. Lưu ý khi deploy lên server

- Chạy **tất cả** các file trong `sql/` theo thứ tự tên file (`2026-10-10_*`, `2026-10-15_*`, `2026-10-16_*`, rồi `2026-10-18_*`) **trước khi** chạy bản mới.
  - `2026-10-18_goi-spa.sql` vừa tạo bảng vừa **chuyển dữ liệu** lịch spa cũ (cột `treatment`) sang `spa_package_id`; cột `treatment` cũ được giữ lại, app không đọc nữa.
  - `2026-10-18_thoi-luong-spa-theo-lich.sql` phải chạy **sau** `2026-10-18_goi-spa.sql` (thứ tự tên file đã đúng) vì điền thời lượng từ bảng gói.
- Biến môi trường:
  - VNPay: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`. Chưa thử với sandbox thật (xem mục 7).
  - Mail: OTP quản trị tự bật khi đã cấu hình mail; đặt `ADMIN_OTP=false` để tắt.
  - Báo cáo cuối ngày: `DAILY_REPORT_TO` (email cách nhau dấu phẩy; bỏ trống = gửi các tài khoản ADMIN), `DAILY_REPORT_ENABLED=false` để tắt.
- Tài khoản admin `ngqhoan214@gmail.com` mới chỉ có trên DB dev. Server cần tạo lại tài khoản này.
- Thông báo tức thời dùng SSE: nếu đặt sau Nginx thì cho đường dẫn `/admin/notifications/stream` tắt buffer (`proxy_buffering off;`, `proxy_read_timeout 1h;`). Nhiều instance app thì mỗi instance chỉ báo cho lễ tân đang nối vào nó.

## 5. Hạn chế đã biết

- Đơn đoàn: mã giảm giá số tiền cố định chỉ trừ vào phòng đầu tiên (tối đa bằng tiền phòng đó).
- Công suất các kỳ cũ được tính theo số phòng hiện tại.
- Đối soát VNPay nhận cột theo các tiêu đề thường gặp; chưa thử với file thật của cổng merchant (chưa có tài khoản). Nếu không nhận được, sửa danh sách tiêu đề trong `AccountingService.refScore` / `detectColumns`.
- Giá không hoàn tiền chưa áp cho đơn đoàn.
- No-show không tự động: lễ tân bấm "Khách không đến" (hệ thống chỉ nhắc lúc 23h).
- Gửi lại email thẻ quà tặng giới hạn 1 lần / 10 phút / thẻ, lưu trong bộ nhớ (khởi động lại app thì đếm lại).

## 6. Gợi ý việc làm tiếp (chưa làm)

1. Thử VNPay sandbox thật theo checklist mục 7 (cần chủ dự án đăng ký tài khoản sandbox), kèm thử đối soát với file sao kê thật.
2. Giá không hoàn tiền cho đơn đoàn.
3. Báo cáo no-show / hủy theo kênh và theo tháng (tỷ lệ, tiền cọc giữ lại).
4. Lịch sử kiểm phòng (ai kiểm, bao nhiêu lần chưa đạt) để đánh giá nhân viên buồng.

## 7. Checklist thử VNPay sandbox (chưa chạy - cần tài khoản)

1. Đăng ký merchant test tại https://sandbox.vnpayment.vn/devreg/ → nhận **TMN code** và **Hash secret** qua email.
2. Cho VNPay gọi được máy: chạy app rồi mở tunnel (VD `ngrok http 8080`), khai báo IPN URL trong trang merchant sandbox: `https://<tên-tunnel>/payment/vnpay/ipn`.
3. Đặt biến môi trường `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` rồi khởi động lại app; nút "Thanh toán qua VNPay" hiện ra ở trang đơn khi đặt cọc > 0 (`/admin/pricing`).
4. Thẻ test: dùng thẻ ATM nội địa (ngân hàng NCB) mà trang sandbox VNPay công bố cho merchant test (số thẻ, tên chủ thẻ, ngày phát hành, OTP lấy đúng trên trang tài liệu sandbox).
5. Các ca cần thử và kết quả mong đợi:
   - **Cọc đơn lẻ** (`/customer/bookings/{id}/vnpay`): sau khi trả tiền, đơn chuyển "Đã xác nhận", có mã GD; lễ tân nhận thông báo "Đã nhận cọc VNPay".
   - **Cọc đoàn** (`/customer/bookings/group/{id}/vnpay`): 1 giao dịch, mọi phòng của đoàn đều được ghi đã cọc với cùng mã GD.
   - **Mua thẻ quà tặng** (`/gift-vouchers/{id}/vnpay`): thẻ chuyển ACTIVE, người nhận nhận email mã thẻ.
   - **Hủy thanh toán** ngay trên trang VNPay: đơn vẫn "Chờ cọc", không ghi nhận tiền.
   - **IPN gọi lặp** (bấm "gửi lại IPN" trên trang merchant): không ghi nhận 2 lần.
   - **Hoàn cọc** (nút "Hoàn qua VNPay" ở trang đơn admin, đơn đã hủy và đủ điều kiện hoàn): có mã GD hoàn; giao dịch hiện trong `/admin/accounting/vnpay` của ngày hoàn.
   - **Đối soát:** tải sao kê ngày đó từ trang merchant, giữ 2 cột (mã GD VNPay, số tiền), tải lên `/admin/accounting/vnpay` → mọi dòng "Khớp".
