# Tiến độ dự án Sóng Xanh Resort (beach-hotel)

> File bàn giao giữa các phiên làm việc với Claude. Mở phiên mới, bảo Claude: **"đọc file TIEN-DO.md"**.
> Cập nhật lần cuối: 20/10/2026, sau đợt 12 (chuyển 28 sơ đồ chương 2 sang đen trắng, soát lại nét; sắp xếp lại chương 3 / 4 của báo cáo; thêm Hình 3.173 lịch .ics mở trong Outlook; vẽ lại biểu đồ chương 2 theo báo cáo tham khảo `docs/bieudothamkhao.pdf`).

## 1. Dự án

- Spring Boot 3.3.4 + Thymeleaf + Spring Security + JPA (Hibernate 6.5). Website đặt phòng resort ở Hạ Long, song ngữ Việt/Anh.
- DB dev: MariaDB (XAMPP), DB `beach_hotel`, user `root` không mật khẩu, `ddl-auto=update`. Test dùng H2 (MySQL mode).
- Môi trường thật dùng `DDL_AUTO=validate`, nên **mọi thay đổi cấu trúc DB phải có file migration** trong `sql/`.
- Repo: https://github.com/ngynquchn2511/beach-hotel, nhánh `main`. Commit và push bằng tài khoản của chủ dự án `ngynquchn2511`; không đổi danh tính git.
- Bản app chạy trong IntelliJ (cổng 8080) cần khởi động lại sau mỗi lần cập nhật code.
- Máy không có `mvn` trong PATH: dùng `C:\Users\Dell\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`.
- **Sandbox của Claude chặn mạng**: tải thư viện Maven mới hoặc `git push` phải chạy lệnh với sandbox tắt. Thư viện đã tải nằm trong `~/.m2`, `mvn -o test` chạy offline được.
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

### Đợt 14 (21/10/2026) - Use case phân rã có Đăng nhập, làm lại slide
| Commit | Chức năng |
|---|---|
| (xem `git log`) | **9 use case phân rã vẽ lại theo kiểu sơ đồ tổng quát `uc0`** (đen trắng, nhãn `<<include>>`, có UC «Đăng nhập» được include từ mọi UC của người dùng, tác nhân «Người dùng» nối tới Đăng nhập và các tác nhân người kế thừa Người dùng (đường kế thừa đi vòng ngoài khung) như `uc0`; UC của Bộ hẹn giờ / VNPay / OTA không cần đăng nhập) trong `docs/use-case/phan-ra/` (`.drawio` + `.png`, kèm `uc0-tong-quat.png` xuất từ bản `uc0` chủ dự án sửa). Bản cũ `docs/use-case/uc1 ... uc9` và hình trong báo cáo **chưa thay**. Sinh bằng script Python `ucgen.py` trong thư mục tạm của phiên (bố cục cột A = UC chính, cột B = UC phụ / UC của tác nhân bên phải, Đăng nhập ở hành lang giữa). **Slide** (artifact claude.ai «Khảo sát hệ thống Sóng Xanh Resort», https://claude.ai/artifact/QZVKLhzufpr5APoKQqimBt) làm lại ít chữ, 18 slide: gộp 6 slide phiếu hỏi thành 1, gộp quan sát + tài liệu, bỏ slide đối chiếu yêu cầu; thêm BFD, use case tổng quát và 5 phân rã (UC1 đặt phòng, UC2 cọc – hủy – hoàn, UC3 nhận – trả phòng, UC8 chăm sóc khách, UC7 lãi lỗ – AI), không mô tả; nội dung chi tiết chuyển vào ghi chú người nói |

### Đợt 13 (21/10/2026) - Báo cáo: đánh số trang liền mạch
| Commit | Chức năng |
|---|---|
| (xem `git log`) | Bỏ số La Mã ở phần đầu và bỏ đánh lại từ 1 ở Mở đầu: cả báo cáo đánh số Ả Rập liên tục, bìa là trang 1 nhưng không hiện số, các trang từ 2 đến 338 hiện số (Mở đầu trang 20). Sửa bằng Word COM (`pn.ps1` trong thư mục tạm của phiên: section 1 trang đầu khác, footer trang đầu rỗng; section 2, 3 không bắt đầu lại), cập nhật mục lục / danh mục, xuất lại PDF. Sơ đồ use case do chủ dự án tự sửa |

### Đợt 12 (20/10/2026) - Báo cáo: sắp xếp lại chương 3 / 4 theo HD.T01
| Commit | Chức năng |
|---|---|
| (xem `git log`) | **28 sơ đồ chương 2 còn màu xanh / vàng chuyển hết sang đen trắng** (Hình 2.2, 2.9 - 2.28, 2.49, 2.50, 2.136 - 2.140: use case, hoạt động, lớp, trạng thái, thành phần, triển khai, ERD chân quạ), xuất lại bằng draw.io Desktop 32.5.5 bản portable (tải vào thư mục tạm của phiên), chụp từng hình soát nét rồi sửa: đường nối từ tác nhân đi ra ở cạnh thân (không cắt tên tác nhân), hộp cùng hàng trong biểu đồ hoạt động cao bằng nhau (mũi tên ngang không gãy bậc), bố trí lại hoạt động «hủy đơn – hoàn cọc» (hộp chuyển khoản trả khách sang làn Lễ tân) và «đặt dịch vụ» (thêm nút kết thúc cho nhánh từ chối), dời nhãn đè nét ở UC1, UC2, trạng thái phòng / dịch vụ / sự cố, biểu đồ thành phần; ERD 1 - 4 xếp lại bảng và vẽ đường vuông góc theo tọa độ cố định (không xuyên bảng). UC1 - UC5 bỏ tiêu đề và nhãn hệ thống như bản trong Word (ảnh mới `docs/hinh/uc1 ... uc5`). Thay ảnh thẳng trong docx (giữ chiều rộng khung, chiều cao theo tỉ lệ ảnh mới), Word cập nhật mục lục, báo cáo 338 trang. Script: `recolor.py`, `fix.py`, `relayout*.py`, `erd_layout.py`, `export.py`, `patch_docx.py`, `word_update.ps1` trong thư mục tạm của phiên |
| (xem `git log`) | **Biểu đồ chương 2 theo kiểu báo cáo tham khảo** (`docs/bieudothamkhao.pdf`, chỉ lấy phần biểu đồ), tất cả đen trắng: BFD (Hình 2.1) gốc nằm trên thanh ngang ở giữa, nhóm 1-3 phía trên, 4-6 phía dưới; **mục 2.3 mới** «Biểu đồ use case phân rã theo nhóm chức năng» (6 hình `docs/use-case/ucn1 ... ucn6`), mục 2.3 cũ thành **2.4 «Đặc tả các use case chính»**, các mục sau lùi 1 số (2.12 là CSDL); **mục 2.8 tuần tự** thay 5 hình cũ bằng 20 hình kiểu Boundary – Control – Entity (Form – ĐK – DS) có khung alt (`docs/thiet-ke/sq01 ... sq20`, đã xóa `sq-*` cũ); **2.12.1** thêm 2 ERD ký hiệu Chen (`erd-chen-1`, `erd-chen-2`) trước 5 ERD chân quạ. Chương 2 có 140 hình, báo cáo 339 trang. Đối chiếu mã nguồn khi vẽ: đăng ký kích hoạt bằng link email (OTP chỉ khi nhân viên vào cổng quản trị), hoàn cọc do lễ tân bấm, trả phòng không bắt buộc mở ca. Script sinh hình: Python (`bw.py`, `bfd.py`, `uc.py`, `seq.py`, `chen.py`, `integrate.py`) trong thư mục tạm của phiên |
| (xem `git log`) | **Hình 2.1 BFD vẽ lại theo 6 nhóm nghiệp vụ** (thay cho 5 nhánh theo vai trò): 1 Đặt phòng và lưu trú, 2 Dịch vụ khách sạn, 3 Buồng phòng, 4 Tài chính – kế toán, 5 Khách hàng, 6 Quản trị hệ thống; 54 chức năng con đánh số 1.1 ... 6.8, bố cục 2 hàng × 3 nhóm để vừa trang dọc. `docs/thiet-ke/phan-cap-chuc-nang.drawio` sinh bằng script Python trong thư mục tạm của phiên; sửa đoạn mô tả 2.1 và câu giới thiệu BFD ở chương 1 |
| (xem `git log`) | Thêm **Hình 3.173 «Mở tệp .ics: đơn đặt phòng hiện trong ứng dụng lịch»** (ảnh chủ dự án chụp Outlook, khung đỏ ①② → `docs/hinh/ket-qua/q18b-ical-outlook.png`) sau Hình 3.172; hình sau đánh lại thành 3.174 - 3.175, tổng 175 ảnh ở mục 3.5; báo cáo 315 trang |
| (xem `git log`) | 174 ảnh chụp chức năng chuyển từ 4.1 sang mục mới **3.5 «Kết quả chạy chương trình theo vai trò người dùng»** (3.5.1 - 3.5.6, Hình 3.1 - 3.174); chương 4 chỉ còn văn xuôi (HD.T01: kết luận không có hình, bảng): 4.1 viết lại từ «Đánh giá chung», 4.2 thêm hạn chế biểu mẫu chỉ kiểm tra ở trình duyệt, hạn chế về Claude API viết lại (đã nạp credit: chỉ còn phụ thuộc dịch vụ ngoài, chi phí token, chưa được ban quản lý thật đánh giá). Sửa tham chiếu (Mở đầu, 2.10 → mục 3.5, «sáu nhóm người dùng»). Trình bày: phần đầu (mục lục, danh mục) đánh số i, ii...; từ Mở đầu đánh số 1; bảng lặp dòng tiêu đề khi sang trang và không ngắt dòng; hình giữ cùng trang với chú thích; tài liệu tham khảo / phụ lục 2 căn trái. Báo cáo 314 trang (17 trang phần đầu + 297). Script: Python sửa XML (`move_ch4.py`) + PowerShell điều khiển Word (`finish.ps1`) trong thư mục tạm của phiên |

### Đợt 11 (20/10/2026) - Báo cáo: kiểm thử toàn bộ chức năng bằng tài khoản giả
| Commit | Chức năng |
|---|---|
| (xem `git log`) | Mục 4.1 viết lại: 174 ảnh chụp thao tác thật (29 ảnh thao tác sai) trong `docs/hinh/ket-qua/`, khung đỏ đánh số quanh vùng thao tác + «Các bước thực hiện» theo số. Kịch bản chạy trên bản sao CSDL `beach_hotel_shots` (app cổng 8082, tắt email/OTP, riêng 4 ảnh OTP chụp khi bật), khách giả Trần Minh Khoa `khoa.demo@example.com` / `Demo@2026` đặt phòng rồi lễ tân, buồng phòng, kế toán, quản trị xử lý nối tiếp; ngày lấy theo «hôm nay» khi chạy. Script Python điều khiển Chrome headless qua DevTools (thư mục tạm của phiên, tự kiểm tra viền đỏ nằm trong ảnh). Ảnh cũ `docs/hinh/chuc-nang/` bỏ. Báo cáo 312 trang. Sửa lỗi tìm được khi test: nút «Ghi nhận đơn» ở `/admin/ota` không mở được form (trang không nạp Bootstrap JS) → thêm script nhỏ trong `admin/ota/index.html`. Còn tồn: bỏ qua kiểm tra của trình duyệt mà gửi form đặt tại quầy không chọn phòng thì ra trang lỗi 400 thô |

### Đợt 10 (19/10/2026) - Báo cáo: phác thảo toàn bộ giao diện
| Commit | Chức năng |
|---|---|
| (xem `git log`) | Mục 2.10 báo cáo viết lại: 83 phác thảo đen trắng kiểu draw.io (`docs/thiet-ke/giao-dien/gd01 ... gd83.drawio`, ảnh `docs/hinh/giao-dien/`) chia 7 nhóm (quy ước + bố cục chung, khách vãng lai, khách hàng, lễ tân, buồng phòng, kế toán, quản trị viên); mỗi hình có số chú thích ①②③ và phần mô tả «Mục đích» + «Các thành phần chính» theo đúng số. Sửa thẳng vào file Word (giữ các chỉnh sửa tay của chủ dự án), đánh số lại hình chương 2 (Hình 2.30 - 2.112 là phác thảo, ERD thành 2.113 - 2.117), báo cáo 243 trang. Script sinh hình viết bằng Python trong thư mục tạm của phiên (lớp `WF` trên nền `dio.py`), xuất PNG bằng draw.io Desktop bản portable |

### Đợt 9 (19/10/2026) - Đồng bộ Booking.com / Airbnb
| Commit | Chức năng |
|---|---|
| `dfb8e93` | Đồng bộ 2 chiều qua iCal `/admin/ota` (`OtaSyncService`, `OtaSyncScheduler` 15 phút, tắt bằng `OTA_SYNC_ENABLED=false`). Chiều vào: link lịch của kênh cho từng phòng (`ota_feeds`, chỉ ADMIN thêm / xóa, chỉ https, chặn địa chỉ nội bộ) → mỗi lượt đặt thành `ota_blocks` khóa phòng ở tìm phòng, đặt phòng, đổi ngày, lịch ngày trống, lịch phòng của lễ tân; kênh xóa lượt đặt → mở bán lại; trùng đơn website → cảnh báo tức thời. Lễ tân «Ghi nhận đơn» → đơn kênh `OTA` (CONFIRMED, giá theo kênh) để check-in / thu tiền; kênh hủy lượt đã ghi nhận → giữ đơn, báo lễ tân. Chiều ra: link xuất riêng cho từng kênh (`/ical/rooms/{id}.ics?key=..&channel=BOOKING|AIRBNB`) gồm đơn website + lượt đặt kênh còn lại, không lặp lượt của chính kênh. Migration `sql/2026-10-19_dong-bo-ota.sql` (2 bảng + thêm `OTA` vào enum `bookings.channel`). Chưa thử với tài khoản Booking.com / Airbnb thật |

### Đợt 8 (19/10/2026) - Sửa lỗi kỹ thuật, tài liệu
| Commit | Chức năng |
|---|---|
| `ca591bf` | Báo cáo đồ án toàn bộ khổ dọc (132 trang): phân cấp chức năng vẽ lại dạng dọc, ERD tách 5 hình (`erd-1` ... `erd-5`), mục 2.4 / 2.6 / 2.7 mỗi loại 5 biểu đồ (hoạt động, trạng thái, tuần tự), mục 2.10 có 10 phác thảo giao diện đen trắng (docs/thiet-ke/giao-dien), mục 4.1 "Kết quả đạt được" có ảnh chụp + mô tả 67 màn hình theo 5 vai trò (`docs/hinh/chuc-nang`, script chụp tự động bằng Chrome headless). Thêm tài khoản quản trị demo vào `DemoDataSeeder` |
| `b3934e4` | Dự báo thêm doanh thu dịch vụ: chi tiêu dịch vụ / phòng-đêm (cùng kỳ năm trước, chưa có thì trung bình 3 tháng gần nhất) × số phòng-đêm dự báo; trang dự báo hiện tổng = phòng + dịch vụ, so với kế hoạch theo tổng; gửi kèm cho AI |
| `5d58ecc` | Khách gửi ảnh kèm tin nhắn theo đơn (`booking_messages.image_url`, `FileStorageService.storeMessageImage`: JPG/PNG/WEBP ≤ 5MB, kiểm loại nội dung), chỉ ảnh cũng gửi được; lễ tân xem ảnh ở trang đơn, hộp thư đánh dấu tin có ảnh. Migration `sql/2026-10-19_anh-tin-nhan.sql` |
| `192bdac` | Mẫu trả lời nhanh cho lễ tân: admin quản lý `/admin/quick-replies` (biến `{ten_khach}`, `{ma_don}`, `{so_phong}`, `{ngay_den}`, `{ngay_di}`, `{gio_nhan}`, `{gio_tra}`), lễ tân chọn ở khung tin nhắn của đơn. Mẫu mặc định tự tạo khi bảng trống; mẫu wifi để tạm ngừng chờ điền thông tin thật. Migration `sql/2026-10-19_mau-tra-loi-nhanh.sql` |
| `331855f` | `README.md` (cài đặt, biến môi trường, tài khoản demo, triển khai). Báo cáo `docs/BaoCao-DoAn.docx` + `.pdf` (72 trang) theo mẫu bìa BTL `BM.T04` và hướng dẫn trình bày `HD.T01` trong `D:\DoAnChuyenNganh`: mở đầu, 4 chương theo dàn ý mẫu (actor, use case UC1-UC8 kèm đặc tả, BFD, hoạt động, lớp, trạng thái, tuần tự, thành phần, triển khai, 11 ảnh giao diện thật, ERD 3 hình, cấu trúc 12 bảng chính, cài đặt, kiểm thử), phụ lục, tài liệu tham khảo. Sơ đồ draw.io trong `docs/use-case` (thêm uc0, uc6-uc8) và `docs/thiet-ke`, ảnh xuất trong `docs/hinh`. Đặc tả UC1-UC5 chép từ `BaoCao-UseCase.docx` cũ. Mục lục / danh mục là trường tự động (Word: References > Update Table) |
| `9d573a1` | Báo cáo chỉ nạp dữ liệu trong kỳ (`BookingRepository.findForPeriod`, `BookingChargeRepository.findCreatedBetween`, join fetch phòng + hạng phòng) thay cho `findAll`: lãi lỗ 13 tháng, dự báo, hiệu quả loại phòng, báo cáo kênh / dịch vụ; khóa dòng mã giảm giá khi tạo đơn (`DiscountCodeRepository.findActiveForUpdate`, test `ConcurrentDiscountIT`: không khóa thì 6/6 người cùng dùng lượt cuối); bỏ `TestDataSeeder` cũ |

### Đợt 7 (19/10/2026) - Lãi lỗ và trợ lý phân tích AI
| Commit | Chức năng |
|---|---|
| `16b909e` | Dữ liệu demo như thật (`config/demo/DemoDataSeeder`, bật 1 lần bằng `DEMO_DATA=true`, đã chạy trên DB dev): ~16 tháng từ 01/06/2025 + đơn đặt trước 4 tháng tới, ~8.000 đơn theo mùa vụ / lễ / cuối tuần, ~6.400 khách (tên Việt theo tỷ lệ họ, CCCD đúng mã tỉnh, SĐT đúng đầu số; ~12% khách nước ngoài, đánh giá bằng tiếng Anh / Hàn / Trung / Nhật / Pháp / Đức), khách quay lại lên hạng, tích / đổi điểm, cọc / hủy / hoàn cọc theo chính sách, không đến, 5 đoàn công ty, mã giảm giá, dịch vụ (đưa đón, tour, spa, nhà hàng, gọi đồ, minibar, giặt ủi), khai báo lưu trú, đánh giá + phản hồi, tin nhắn, dọn / kiểm phòng, sự cố, đồ thất lạc, liên hệ, thẻ quà tặng, chi phí vận hành hằng tháng, kế hoạch 2026. Hôm nay còn việc thật để thao tác (khách đến / đi, spa chờ xác nhận, gọi đồ chưa giao, tin chưa đọc, phòng chưa dọn, phòng đang sửa). Email đều `@example.com/.net/.org` (không tới người thật). Tài khoản nhân viên demo mật khẩu `Songxanh@2026`: lễ tân `letan.thuhuong@example.com`, buồng phòng `buongphong.hanh@example.com`, kế toán `ketoan.bichngoc@example.com`, quản trị viên `quanly.vanthanh@example.com` (máy dev cần `ADMIN_OTP=false` vì OTP gửi tới email demo). Khách demo cùng mật khẩu |
| `965712e` | Hiệu quả theo loại phòng trên trang lãi lỗ (`RoomTypePerformanceService`): công suất, ADR, RevPAR, dịch vụ kèm, đóng góp sau chi phí biến đổi / phòng-đêm sẵn có; gửi kèm cho AI |
| `e509dee` | Dự báo doanh thu phòng 3 tháng `/admin/accounting/revenue-forecast` (`RevenueForecastService`, pickup: trên sổ + lượng đặt thêm cùng kỳ × tốc độ bán, giới hạn ×0,5-×2), độ tin cậy, so với kế hoạch; gửi kèm cho AI khi phân tích tháng hiện tại |
| `059d3be` | Báo cáo quản trị tháng gửi email 8h ngày 1 (`MonthlyReportScheduler`, tắt bằng `MONTHLY_REPORT_ENABLED=false`) cho cùng người nhận báo cáo cuối ngày; nút gửi ngay trên trang Phân tích |
| `3279122` | Kế hoạch năm `/admin/accounting/budget` (doanh thu, trần chi phí, công suất, ADR theo tháng); trang lãi lỗ hiện % đạt; nhận định "thiếu X VND ≈ N phòng-đêm"; gửi kèm cho AI |
| `1a1a14d` | Phân tích quản trị `/admin/accounting/insights`: nhận định tự động theo quy tắc (`InsightService`, ngưỡng là hằng số đầu file) + trợ lý AI (`service/ai/`): `ClaudeAiGateway` gọi Claude qua Java SDK `com.anthropic:anthropic-java:2.34.0`, model mặc định `claude-haiku-5-5` (rẻ nhất, chủ dự án chọn; đổi bằng `AI_MODEL`), structured output `AiAnalysis` (tóm tắt, điểm sáng, rủi ro, khuyến nghị, dự báo); hỏi đáp số liệu; lưu `ai_reports` kèm token và chi phí ước tính. Số liệu gửi AI chỉ là số tổng hợp, không có tên / email / SĐT khách |
| `c8fcb85` | Chi phí vận hành `/admin/accounting/expenses` (1 lần / hàng tháng, dừng từ tháng nào), giá vốn dịch vụ %, phí cổng thanh toán %, chi phí mỗi phòng-đêm; lãi lỗ `/admin/accounting/pnl` (dồn tích: phòng theo đêm, dịch vụ theo ngày phát sinh, cọc giữ lại theo ngày hủy; không tính tiền bán thẻ quà tặng), GOP, biên, GOPPAR, CPOR, so tháng trước / cùng kỳ, biểu đồ SVG 12 tháng, Excel |

### Đợt 6 (19/10/2026)
| Commit | Chức năng |
|---|---|
| `61ce31d` | Lịch sử dọn / kiểm phòng (`housekeeping_logs`), thống kê theo nhân viên `/admin/housekeeping/stats` (phòng dọn xong, phút TB, tỷ lệ kiểm chưa đạt) |
| `fa10a65` | Giá không hoàn tiền cho đơn đoàn |
| `a637b9c` | Nhắn tin khách ↔ lễ tân theo đơn (`booking_messages`): khách nhắn trên trang đơn, lễ tân trả lời kèm email, hộp `/admin/messages` + badge chưa đọc. Sửa: các trang admin mới của đợt 4-5 giờ đã có trong `AdminSidebarModelAdvice` (badge sidebar hiện đúng) |
| `61e6da1` | Yêu cầu đặc biệt (`GuestRequestTag` + ghi thêm) khi đặt / sửa trên trang đơn; ghi chú sở thích khách (`users.staff_notes`); hiện ở trang đơn admin, "Hôm nay", bảng dọn phòng |
| `5f90be7` | Báo cáo hủy & không đến `/admin/reports/cancellations`: theo tháng / kênh, hủy sát ngày, cọc giữ lại, khách hủy nhiều lần, Excel |
| `1cff672` | Bán nâng hạng phòng (`upgrade_offers`): lời mời lên loại cao hơn còn trống, ưu đãi % trên chênh lệch, khách nhận trên trang đơn → đổi phòng + cộng tiền; tự gửi 2 ngày trước khi đến; `/admin/upgrades` |
| `11d906a` | Mã giảm giá có giới hạn: thời hạn, tổng số lượt (đoàn tính 1), đơn tối thiểu, trần giảm cho mã %; thống kê từng mã; chatbot chỉ giới thiệu mã còn dùng được |

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

- Chạy **tất cả** các file trong `sql/` theo thứ tự tên file (`2026-10-10_*`, `2026-10-15_*`, `2026-10-16_*`, `2026-10-18_*`, rồi `2026-10-19_*`) **trước khi** chạy bản mới.
  - `2026-10-18_goi-spa.sql` vừa tạo bảng vừa **chuyển dữ liệu** lịch spa cũ (cột `treatment`) sang `spa_package_id`; cột `treatment` cũ được giữ lại, app không đọc nữa.
  - `2026-10-18_thoi-luong-spa-theo-lich.sql` phải chạy **sau** `2026-10-18_goi-spa.sql` (thứ tự tên file đã đúng) vì điền thời lượng từ bảng gói.
- Biến môi trường:
  - VNPay: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`. Chưa thử với sandbox thật (xem mục 7).
  - Mail: OTP quản trị tự bật khi đã cấu hình mail; đặt `ADMIN_OTP=false` để tắt.
  - AI: `ANTHROPIC_API_KEY` (tạo ở console.anthropic.com) để bật phân tích AI; `AI_MODEL` (mặc định `claude-haiku-5-5` - 0,10 / 0,50 USD mỗi triệu token vào / ra; đổi sang `claude-opus-5-5` nếu cần phân tích sâu hơn), `AI_ENABLED=false` để tắt hẳn, `AI_FALLBACKS` (`auto` mặc định: chỉ gửi `fallbacks` với model có hỗ trợ - Haiku thì không). Máy dev để khóa trong file `secrets.properties` ở thư mục gốc (gitignore, nạp qua `spring.config.import`; khóa hết hạn 18/11/2026) - KHÔNG đặt biến môi trường `ANTHROPIC_API_KEY` cho cả Windows vì Claude Code sẽ dùng khóa trả phí thay cho gói claude.ai. Tài khoản Anthropic **đã nạp credit** (chủ dự án báo 20/10), phân tích AI chạy thật; hết credit thì API trả 400 "credit balance is too low" và trang hiện thông báo nạp tiền.
  - Báo cáo cuối ngày: `DAILY_REPORT_TO` (email cách nhau dấu phẩy; bỏ trống = gửi các tài khoản ADMIN), `DAILY_REPORT_ENABLED=false` để tắt.
- Tài khoản admin `ngqhoan214@gmail.com` mới chỉ có trên DB dev. Server cần tạo lại tài khoản này.
- Thông báo tức thời dùng SSE: nếu đặt sau Nginx thì cho đường dẫn `/admin/notifications/stream` tắt buffer (`proxy_buffering off;`, `proxy_read_timeout 1h;`). Nhiều instance app thì mỗi instance chỉ báo cho lễ tân đang nối vào nó.

## 5. Hạn chế đã biết

- Đơn đoàn: mã giảm giá số tiền cố định chỉ trừ vào phòng đầu tiên (tối đa bằng tiền phòng đó).
- Công suất các kỳ cũ được tính theo số phòng hiện tại.
- Đối soát VNPay nhận cột theo các tiêu đề thường gặp; chưa thử với file thật của cổng merchant (chưa có tài khoản). Nếu không nhận được, sửa danh sách tiêu đề trong `AccountingService.refScore` / `detectColumns`.
- Giới hạn số lượt mã giảm giá không khóa dòng: 2 khách bấm đặt đúng cùng lúc ở lượt cuối có thể cùng được nhận.
- Nâng hạng chỉ mời loại phòng có giá gốc (`basePrice`) cao hơn; giá mời là chênh lệch theo bảng giá các đêm của đơn.
- Lịch sử dọn phòng chỉ có từ khi triển khai đợt 6 (lượt dọn trước đó không có số liệu).
- Lãi lỗ chỉ đúng khi kế toán nhập đủ chi phí; công suất các tháng cũ tính theo số phòng hiện tại. Mỗi lần bấm phân tích / hỏi AI với Haiku 5.5 tốn khoảng 0,001-0,005 USD.
- No-show không tự động: lễ tân bấm "Khách không đến" (hệ thống chỉ nhắc lúc 23h).
- Gửi lại email thẻ quà tặng giới hạn 1 lần / 10 phút / thẻ, lưu trong bộ nhớ (khởi động lại app thì đếm lại).

## 6. Gợi ý việc làm tiếp (chưa làm)

1. Nạp credit cho tài khoản Anthropic (khóa `ANTHROPIC_API_KEY` đã đặt trên máy dev), chạy thử phân tích AI một tháng có số liệu; chỉnh prompt (`AiAdvisorService.ANALYST_PROMPT`) theo phản hồi của chủ resort.
2. Thử VNPay sandbox thật theo checklist mục 7 (cần chủ dự án đăng ký tài khoản sandbox), kèm thử đối soát với file sao kê thật.
3. Điền thông tin wifi thật vào mẫu trả lời nhanh rồi bật mẫu.
4. Gợi ý giá theo nhu cầu (tăng giá khi tốc độ bán cao, khuyến mãi khi thấp) dựa trên dự báo.
5. Thử đồng bộ lịch với chỗ ở thật trên Booking.com / Airbnb (cần tài khoản chủ nhà); iCal chỉ cập nhật theo chu kỳ của kênh (Booking.com ~ vài giờ) nên vẫn có khoảng hở bán trùng - muốn tức thời phải dùng channel manager / API đối tác.

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
