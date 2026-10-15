package com.hotel.service;

import com.hotel.util.Money;
import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Payment;
import com.hotel.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final Executor mailExecutor;
    private final String senderEmail;
    private final String senderPassword;

    // Tai khoan nhan tien coc (cung cau hinh voi QR thanh toan trong PaymentService)
    @Value("${app.payment.bank-id:MB}")
    private String bankId;

    @Value("${app.payment.account-no:0000000000}")
    private String accountNo;

    @Value("${app.payment.account-name:SONG XANH RESORT}")
    private String accountName;

    // Dia chi website de tao link xem don trong email (VD https://songxanhresort.vn). De trong thi khong gui link
    @Value("${app.base-url:}")
    private String baseUrl;

    // So Zalo cua resort (cung cau hinh nut "Nhan Zalo" tren web) - ghi vao email nhac lich
    @Value("${app.contact.zalo-phone:}")
    private String zaloPhone;

    private static final String DIRECTIONS_URL =
            "https://www.google.com/maps/dir/?api=1&destination=Bai+Chay,+Ha+Long,+Quang+Ninh";

    public EmailService(JavaMailSender mailSender,
                        @Qualifier("mailExecutor") Executor mailExecutor,
                        @Value("${spring.mail.username:}") String senderEmail,
                        @Value("${spring.mail.password:}") String senderPassword) {
        this.mailSender = mailSender;
        this.mailExecutor = mailExecutor;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
    }

    // Gui email xac nhan sau khi dat phong thanh cong. Neu gui that bai (VD: chua cau hinh dung mat khau ung dung)
    // thi CHI ghi log canh bao, KHONG lam hong luong dat phong - dat phong van thanh cong binh thuong.
    // Noi dung soan ngay (con trong giao dich, doc duoc phong/combo...), con viec gui thi doi giao dich commit xong
    // moi chay o luong rieng (xem deliverAfterCommit)
    public void sendBookingConfirmation(Booking booking) {
        // Khi tra phong khach nhan email hoa don (sendInvoice) thay cho email thong bao trang thai
        if (booking.getStatus() == BookingStatus.CHECKED_OUT) {
            return;
        }
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            log.warn("Khong gui email xac nhan cho booking #{} vi email nguoi nhan trong", booking.getId());
            return;
        }
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.error("Khong gui email xac nhan cho booking #{}: chua dat MAIL_USERNAME va MAIL_PASSWORD", booking.getId());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(booking.getGuestEmail());
        message.setSubject(buildSubject(booking));
        message.setText(buildBody(booking));
        Long bookingId = booking.getId();
        String to = booking.getGuestEmail();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui email xac nhan cho booking #{} toi {}", bookingId, to);
            } catch (Exception ex) {
                log.error("Gui email xac nhan that bai cho booking #{} toi {}", bookingId, to, ex);
            }
        });
    }

    // Bao khach resort da chuyen tra tien hoan coc (sau khi nhan vien bam "Da hoan tien")
    public void sendRefundCompleted(Booking booking) {
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()
                || senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Khong gui email hoan coc cho booking #{} (thieu email nguoi nhan hoac cau hinh mail)", booking.getId());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(booking.getGuestEmail());
        message.setSubject("Sóng Xanh Resort - Đã hoàn tiền cọc đơn #" + booking.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Sóng Xanh Resort đã chuyển hoàn ").append(money(booking.getRefundAmount()))
                .append(" VND tiền cọc của đơn đặt phòng #").append(booking.getId()).append(" (đã hủy).\n");
        if (booking.getRefundNote() != null) {
            sb.append("Ghi chú: ").append(booking.getRefundNote()).append("\n");
        }
        sb.append("\nTiền có thể mất 1-3 ngày làm việc để về tài khoản tùy ngân hàng. "
                + "Nếu chưa nhận được, vui lòng liên hệ hotline/Zalo của resort.\n\n");
        sb.append("Rất mong được đón tiếp bạn trong dịp khác!\nSóng Xanh Resort");
        message.setText(sb.toString());
        Long bookingId = booking.getId();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui email hoan coc cho booking #{}", bookingId);
            } catch (Exception ex) {
                log.error("Gui email hoan coc that bai cho booking #{}", bookingId, ex);
            }
        });
    }

    // Nhac lich truoc ngay nhan phong (GuestEmailScheduler goi). Tra ve true neu da dua vao hang doi gui -
    // false khi khong gui duoc (khong co email khach / chua cau hinh mail) de lan sau thu lai
    public boolean sendCheckInReminder(Booking booking) {
        if (!canEmail(booking)) {
            return false;
        }
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Sóng Xanh Resort rất mong được đón bạn! Nhắc bạn lịch nhận phòng sắp tới:\n\n");
        sb.append("Mã đơn: #").append(booking.getId()).append("\n");
        sb.append("Phòng: ").append(booking.getRoom().getRoomNumber())
                .append(" - ").append(booking.getRoom().getRoomType().getName()).append("\n");
        sb.append("Nhận phòng: ").append(booking.getCheckInTime()).append(" ngày ")
                .append(booking.getCheckInDate().format(dateFmt)).append("\n");
        sb.append("Trả phòng: ").append(booking.getCheckOutTime()).append(" ngày ")
                .append(booking.getCheckOutDate().format(dateFmt)).append("\n");
        if (booking.getNumberOfGuests() != null) {
            sb.append("Số khách: ").append(booking.getNumberOfGuests()).append("\n");
        }
        BigDecimal remaining = booking.getTotalAmount().subtract(booking.getDepositPaidAmount()).max(BigDecimal.ZERO);
        sb.append("Cần thanh toán khi trả phòng: ").append(money(remaining)).append(" VND")
                .append(booking.getDepositPaidAmount().signum() > 0 ? " (đã trừ tiền cọc)" : "")
                .append(", chưa gồm phụ phí phát sinh\n\n");
        sb.append("CHUẨN BỊ KHI ĐẾN\n");
        String declareLink = bookingLink(booking, "#khai-bao");
        if (declareLink != null) {
            sb.append("- Khai báo lưu trú online trước (đến nơi chỉ cần đối chiếu giấy tờ, nhận phòng nhanh hơn): ")
                    .append(declareLink).append("\n");
        }
        sb.append("- Mang CCCD/hộ chiếu của tất cả khách lưu trú để lễ tân đối chiếu.\n");
        sb.append("- Chỉ đường tới resort (Bãi Cháy, Hạ Long): ").append(DIRECTIONS_URL).append("\n");
        if (zaloPhone != null && !zaloPhone.isBlank()) {
            sb.append("- Cần đổi giờ hoặc hỗ trợ gấp, nhắn Zalo: ").append(zaloPhone).append("\n");
        }
        String link = bookingLink(booking, "#dich-vu");
        if (link != null) {
            sb.append("- Đặt thêm xe đưa đón sân bay hoặc tour lặn biển: ").append(link).append("\n");
        }
        sb.append("\nChúc bạn có chuyến đi vui vẻ!\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - Nhắc lịch nhận phòng đơn #" + booking.getId(), sb.toString(), "nhac lich");
    }

    // Khach tu doi ngay nhan/tra phong - gui lai thong tin don theo ngay moi (tien phong tinh lai)
    public boolean sendDatesChanged(Booking booking) {
        if (!canEmail(booking)) {
            return false;
        }
        String body = buildBody(booking, "Bạn vừa đổi ngày lưu trú cho đơn #" + booking.getId()
                + ". Tiền phòng đã được tính lại theo ngày mới. Thông tin đơn sau khi đổi:");
        return queue(booking, "Sóng Xanh Resort - Đã đổi ngày lưu trú đơn #" + booking.getId(), body, "doi ngay");
    }

    // The qua tang da thanh toan: gui ma cho nguoi nhan (kem loi nhan) va bao nguoi mua
    public boolean sendGiftVoucher(com.hotel.entity.GiftVoucher v) {
        if (!isConfigured()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - chua gui duoc the qua tang #{}", v.getId());
            return false;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(v.getRecipientName()).append(",\n\n");
        sb.append(v.getPurchaserName()).append(" vừa tặng bạn thẻ quà tặng Sóng Xanh Resort trị giá ")
                .append(money(v.getAmount())).append(" VND.\n");
        if (v.getMessage() != null) {
            sb.append("\nLời nhắn: \"").append(v.getMessage()).append("\"\n");
        }
        sb.append("\nMã thẻ: ").append(v.getCode()).append("\n");
        sb.append("Hạn dùng: ").append(v.getExpiresAt().format(fmt)).append("\n");
        sb.append("Cách dùng: khi đặt phòng trên website, nhập mã ở ô \"Thẻ quà tặng\" - tiền được trừ thẳng vào đơn, số dư còn lại dùng tiếp lần sau.\n");
        if (baseUrl != null && !baseUrl.isBlank()) {
            sb.append("Đặt phòng: ").append(baseUrl.replaceAll("/+$", "")).append("/rooms\n");
        }
        sb.append("\nHẹn gặp bạn bên vịnh Hạ Long!\nSóng Xanh Resort");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(v.getRecipientEmail());
        if (!v.getRecipientEmail().equalsIgnoreCase(v.getPurchaserEmail())) {
            message.setCc(v.getPurchaserEmail());
        }
        message.setSubject("Sóng Xanh Resort - Thẻ quà tặng " + money(v.getAmount()) + " VND từ " + v.getPurchaserName());
        message.setText(sb.toString());
        Long id = v.getId();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
            } catch (Exception ex) {
                log.error("Gui the qua tang #{} that bai", id, ex);
            }
        });
        return true;
    }

    // Bao khach de quen do (lien he le tan de nhan lai / gui buu dien)
    public boolean sendLostItemFound(com.hotel.entity.LostItem item) {
        Booking booking = item.getBooking();
        if (booking == null || !canEmail(booking)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Sau kỳ lưu trú (đơn #").append(booking.getId()).append(", phòng ").append(booking.getRoom().getRoomNumber())
                .append("), nhân viên Sóng Xanh Resort tìm thấy đồ có thể là của bạn:\n\n- ").append(item.getDescription()).append("\n\n");
        sb.append("Chúng tôi đang giữ cẩn thận. Bạn hãy trả lời email này");
        if (zaloPhone != null && !zaloPhone.isBlank()) {
            sb.append(" hoặc nhắn Zalo ").append(zaloPhone);
        }
        sb.append(" để hẹn lấy lại hoặc nhờ gửi bưu điện (phí gửi do bạn chi trả).\n\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - Bạn có để quên đồ? (đơn #" + booking.getId() + ")", sb.toString(), "do that lac");
    }

    // Nhac chuyen coc truoc khi het han (don se tu huy)
    public boolean sendDepositReminder(Booking booking) {
        if (!canEmail(booking)) {
            return false;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Đơn #").append(booking.getId()).append(" (phòng ").append(booking.getRoom().getRoomNumber())
                .append(", nhận phòng ").append(booking.getCheckInDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .append(") vẫn chưa nhận được tiền cọc.\n");
        sb.append("Hạn chuyển cọc: ").append(booking.getDepositDeadline().format(fmt))
                .append(" - quá hạn đơn sẽ tự hủy để nhường phòng cho khách khác.\n\n");
        sb.append("Số tiền cọc: ").append(money(booking.getDepositAmount())).append(" VND\n");
        sb.append("Chuyển khoản: ").append(bankId).append(" ").append(accountNo).append(" (").append(accountName).append(")\n");
        sb.append("Nội dung: HB").append(booking.getId()).append(" COC\n");
        String link = bookingLink(booking, "");
        if (link != null) {
            sb.append("\nThanh toán nhanh bằng mã QR hoặc VNPay tại: ").append(link).append("\n");
        }
        sb.append("\nNếu bạn đã chuyển khoản, vui lòng bỏ qua email này - lễ tân sẽ xác nhận sớm.\n\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - Nhắc chuyển cọc đơn #" + booking.getId(), sb.toString(), "nhac coc");
    }

    // Dat nhieu phong: 1 email tong hop cho ca nhom (cancelled = bao da huy)
    public boolean sendGroupBooking(com.hotel.entity.BookingGroup group, List<Booking> bookings, boolean cancelled) {
        if (bookings.isEmpty() || !canEmail(bookings.get(0))) {
            return false;
        }
        Booking first = bookings.get(0);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(group.getGuestName()).append(",\n\n");
        sb.append(cancelled ? "Đã hủy đặt phòng theo đoàn " : "Cảm ơn bạn đã đặt phòng theo đoàn tại Sóng Xanh Resort. Mã đoàn: ")
                .append(group.getCode()).append("\n");
        sb.append("Nhận phòng: ").append(first.getCheckInTime()).append(" ngày ").append(first.getCheckInDate().format(dateFmt)).append("\n");
        sb.append("Trả phòng: ").append(first.getCheckOutTime()).append(" ngày ").append(first.getCheckOutDate().format(dateFmt)).append("\n\n");
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal deposit = BigDecimal.ZERO;
        for (Booking b : bookings) {
            sb.append("- Đơn #").append(b.getId()).append(": phòng ").append(b.getRoom().getRoomNumber())
                    .append(" (").append(b.getRoom().getRoomType().getName()).append("), ")
                    .append(money(b.getTotalAmount())).append(" VND - ").append(b.getStatus().getVietnameseLabel()).append("\n");
            total = total.add(b.getTotalAmount());
            if (b.isAwaitingDeposit()) {
                deposit = deposit.add(b.getDepositAmount());
            }
        }
        sb.append("\nTổng tiền phòng: ").append(money(total)).append(" VND\n");
        if (!cancelled && deposit.signum() > 0) {
            sb.append("Tiền cọc cả đoàn: ").append(money(deposit)).append(" VND - chuyển khoản tới ").append(bankId).append(" ")
                    .append(accountNo).append(" (").append(accountName).append("), nội dung: HB ").append(group.getCode()).append(" COC\n");
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            sb.append("\nXem đoàn: ").append(baseUrl.replaceAll("/+$", "")).append("/customer/bookings/group/").append(group.getId())
                    .append("?token=").append(group.getAccessToken()).append("\n");
        }
        sb.append("\nSóng Xanh Resort");
        return queue(first, "Sóng Xanh Resort - " + (cancelled ? "Đã hủy" : "Xác nhận") + " đặt phòng đoàn " + group.getCode(),
                sb.toString(), cancelled ? "huy doan" : "dat doan");
    }

    // Le tan doi phong cho khach - gui lai thong tin don voi phong moi
    public boolean sendRoomMoved(Booking booking, String oldRoomNumber) {
        if (!canEmail(booking)) {
            return false;
        }
        String body = buildBody(booking, "Sóng Xanh Resort đã chuyển đơn #" + booking.getId() + " từ phòng " + oldRoomNumber
                + " sang phòng " + booking.getRoom().getRoomNumber() + " (" + booking.getRoom().getRoomType().getName() + ")."
                + (booking.getRoomMoveNote() != null ? " Lý do: " + booking.getRoomMoveNote() + "." : "")
                + " Thông tin đơn sau khi đổi:");
        return queue(booking, "Sóng Xanh Resort - Đổi phòng cho đơn #" + booking.getId(), body, "doi phong");
    }

    // Le tan duyet / tu choi yeu cau nhan phong som / tra phong muon
    public boolean sendStayTimeDecision(Booking booking, boolean approved) {
        if (!canEmail(booking)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        if (approved) {
            sb.append("Sóng Xanh Resort đã DUYỆT yêu cầu của bạn cho đơn #").append(booking.getId()).append(":\n");
            sb.append("- Nhận phòng: ").append(booking.getCheckInTime()).append(" ngày ")
                    .append(booking.getCheckInDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
            sb.append("- Trả phòng: ").append(booking.getCheckOutTime()).append(" ngày ")
                    .append(booking.getCheckOutDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
            if (booking.getTimeRequestFee() != null && booking.getTimeRequestFee().signum() > 0) {
                sb.append("- Phụ phí: ").append(money(booking.getTimeRequestFee())).append(" VND, thanh toán khi trả phòng\n");
            }
        } else {
            sb.append("Rất tiếc, Sóng Xanh Resort chưa thể đáp ứng yêu cầu nhận phòng sớm / trả phòng muộn cho đơn #")
                    .append(booking.getId()).append(".\n");
            if (booking.getTimeRequestNote() != null) {
                sb.append("Lý do: ").append(booking.getTimeRequestNote()).append("\n");
            }
            sb.append("Giờ nhận / trả phòng của bạn giữ nguyên như đã đặt.\n");
        }
        String link = bookingLink(booking, "#gio-linh-hoat");
        if (link != null) {
            sb.append("\nXem đơn: ").append(link).append("\n");
        }
        sb.append("\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - " + (approved ? "Đã duyệt" : "Phản hồi") + " yêu cầu đổi giờ đơn #" + booking.getId(),
                sb.toString(), approved ? "duyet doi gio" : "tu choi doi gio");
    }

    // Moi danh gia sau khi tra phong (GuestEmailScheduler goi). Tra ve true neu da dua vao hang doi gui
    public boolean sendReviewInvitation(Booking booking) {
        if (!canEmail(booking)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Cảm ơn bạn đã nghỉ tại Sóng Xanh Resort (đơn #").append(booking.getId())
                .append(", phòng ").append(booking.getRoom().getRoomNumber()).append(").\n");
        sb.append("Bạn thấy kỳ nghỉ vừa rồi thế nào? Chỉ mất 1 phút để chấm sao và để lại vài lời - ")
                .append("có thể kèm ảnh, video. Góp ý của bạn giúp resort phục vụ tốt hơn ")
                .append("và giúp những du khách khác chọn phòng.\n\n");
        String link = bookingLink(booking, "#danh-gia");
        if (link != null) {
            sb.append("Viết đánh giá: ").append(link).append("\n");
            sb.append("(Link dành riêng cho đơn của bạn, vui lòng không chia sẻ.)\n\n");
        } else {
            sb.append("Đăng nhập website, vào Lịch sử đặt phòng, mở đơn #").append(booking.getId())
                    .append(" để viết đánh giá.\n\n");
        }
        sb.append("Hẹn gặp lại bạn bên vịnh Hạ Long!\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - Bạn thấy kỳ nghỉ thế nào?", sb.toString(), "moi danh gia");
    }

    // Resort vua tra loi danh gia cua khach (lan dau) - gui kem noi dung tra loi va link xem
    public boolean sendReviewReply(com.hotel.entity.Review review) {
        Booking booking = review.getBooking();
        if (!canEmail(booking)) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append("Cảm ơn bạn đã dành thời gian đánh giá kỳ nghỉ tại Sóng Xanh Resort (đơn #")
                .append(booking.getId()).append("). Resort đã trả lời đánh giá của bạn:\n\n");
        sb.append("\"").append(review.getReply()).append("\"\n\n");
        String link = bookingLink(booking, "#danh-gia");
        if (link != null) {
            sb.append("Xem đánh giá: ").append(link).append("\n\n");
        }
        sb.append("Hẹn gặp lại bạn bên vịnh Hạ Long!\nSóng Xanh Resort");
        return queue(booking, "Sóng Xanh Resort - Đã trả lời đánh giá của bạn", sb.toString(), "tra loi danh gia");
    }

    // Tra loi tin lien he cua khach (form "Lien he" o trang gioi thieu). Kem lai cau hoi goc de khach doi chieu
    public boolean sendContactReply(com.hotel.entity.ContactMessage contact) {
        if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - khong gui tra loi lien he #{}", contact.getId());
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(contact.getEmail());
        message.setSubject("Sóng Xanh Resort - Trả lời yêu cầu tư vấn của bạn");
        message.setText("Xin chào " + contact.getFullName() + ",\n\n"
                + "Cảm ơn bạn đã liên hệ Sóng Xanh Resort. Chúng tôi xin trả lời như sau:\n\n"
                + contact.getReply() + "\n\n"
                + "----------\nCâu hỏi của bạn:\n" + contact.getMessage() + "\n----------\n\n"
                + "Cần hỗ trợ thêm, bạn cứ trả lời email này"
                + (zaloPhone == null || zaloPhone.isBlank() ? "" : " hoặc nhắn Zalo " + zaloPhone) + ".\n\n"
                + "Trân trọng,\nSóng Xanh Resort");
        Long id = contact.getId();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui tra loi lien he #{}", id);
            } catch (Exception ex) {
                log.error("Gui tra loi lien he #{} that bai", id, ex);
            }
        });
        return true;
    }

    public boolean isConfigured() {
        return senderEmail != null && !senderEmail.isBlank() && senderPassword != null && !senderPassword.isBlank();
    }

    // Ma xac thuc 2 buoc khi dang nhap cong quan tri
    public boolean sendAdminOtp(String to, String fullName, String code, long validMinutes) {
        if (!isConfigured() || to == null || to.isBlank()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(to);
        message.setSubject("Sóng Xanh Resort - Mã đăng nhập quản trị: " + code);
        message.setText("Xin chào " + (fullName != null ? fullName : "") + ",\n\n"
                + "Mã xác thực đăng nhập cổng quản trị của bạn là: " + code + "\n"
                + "Mã có hiệu lực " + validMinutes + " phút và chỉ dùng được 1 lần.\n\n"
                + "Nếu bạn không vừa đăng nhập, hãy đổi mật khẩu ngay và báo quản trị viên.\n\n"
                + "Sóng Xanh Resort");
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
            } catch (Exception ex) {
                log.error("Gui ma dang nhap quan tri that bai", ex);
            }
        });
        return true;
    }

    // "Bao toi khi phong trong lai": phong khach theo doi da trong dung khoang ngay -> gui link dat ngay
    public boolean sendRoomAvailable(com.hotel.entity.RoomAlert alert) {
        if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - chua gui duoc email bao phong trong #{}", alert.getId());
            return false;
        }
        boolean en = "en".equals(alert.getLanguage());
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        // Gui tu tac vu dinh ky (khong co ngon ngu cua request) -> chon ten theo ngon ngu luu trong yeu cau
        com.hotel.entity.RoomType type = alert.getRoom().getRoomType();
        String room = alert.getRoom().getRoomNumber() + " - "
                + (en && type.getNameEn() != null && !type.getNameEn().isBlank() ? type.getNameEn() : type.getName());
        String dates = alert.getCheckIn().format(fmt) + " - " + alert.getCheckOut().format(fmt);
        String link = null;
        if (baseUrl != null && !baseUrl.isBlank()) {
            link = baseUrl.replaceAll("/+$", "") + "/customer/rooms/" + alert.getRoom().getId()
                    + "?checkIn=" + alert.getCheckIn() + "&checkOut=" + alert.getCheckOut()
                    + (alert.getGuests() != null ? "&guests=" + alert.getGuests() : "");
        }
        StringBuilder sb = new StringBuilder();
        if (en) {
            sb.append("Hello,\n\nGood news: room ").append(room).append(" is now available for ").append(dates).append(".\n");
            sb.append("Rooms go fast - book soon to secure it.\n\n");
            if (link != null) {
                sb.append("Book now: ").append(link).append("\n\n");
            }
            sb.append("You received this email because you asked to be notified. We will not email you again about this request.\n\n");
            sb.append("Song Xanh Resort");
        } else {
            sb.append("Xin chào,\n\nTin vui: phòng ").append(room).append(" đã trống lại cho ngày ").append(dates).append(".\n");
            sb.append("Phòng có thể được người khác đặt bất cứ lúc nào - bạn nên đặt sớm để giữ phòng.\n\n");
            if (link != null) {
                sb.append("Đặt ngay: ").append(link).append("\n\n");
            }
            sb.append("Bạn nhận email này vì đã bấm \"Báo tôi khi phòng trống lại\". Chúng tôi chỉ gửi 1 lần cho yêu cầu này.\n\n");
            sb.append("Sóng Xanh Resort");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(alert.getEmail());
        message.setSubject(en ? "Song Xanh Resort - The room you wanted is available" : "Sóng Xanh Resort - Phòng bạn chờ đã trống");
        message.setText(sb.toString());
        Long id = alert.getId();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui email bao phong trong #{}", id);
            } catch (Exception ex) {
                log.error("Gui email bao phong trong #{} that bai", id, ex);
            }
        });
        return true;
    }

    private boolean canEmail(Booking booking) {
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()
                || booking.getGuestEmail().endsWith("@khachvanglai.local")) {
            return false;
        }
        if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - khong gui email tu dong cho booking #{}", booking.getId());
            return false;
        }
        return true;
    }

    private boolean queue(Booking booking, String subject, String text, String kind) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(booking.getGuestEmail());
        message.setSubject(subject);
        message.setText(text);
        Long bookingId = booking.getId();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui email {} cho booking #{}", kind, bookingId);
            } catch (Exception ex) {
                log.error("Gui email {} that bai cho booking #{}", kind, bookingId, ex);
            }
        });
        return true;
    }

    // Link xem don cho khach (kem ma truy cap) - null neu chua cau hinh app.base-url
    private String bookingLink(Booking booking, String anchor) {
        if (baseUrl == null || baseUrl.isBlank() || booking.getAccessToken() == null) {
            return null;
        }
        return baseUrl.replaceAll("/+$", "") + "/customer/bookings/" + booking.getId()
                + "?token=" + booking.getAccessToken() + anchor;
    }

    // Dang trong giao dich: chi gui khi giao dich COMMIT thanh cong (rollback -> khong gui email cho don khong ton tai),
    // va gui sau khi da nha khoa phong (SMTP cham khong bat khach khac dat cung phong phai cho).
    // Khong co giao dich: gui luon (van o luong rieng)
    private void deliverAfterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mailExecutor.execute(send);
                }
            });
        } else {
            mailExecutor.execute(send);
        }
    }

    // Gui lien ket dat lai mat khau. Neu chua cau hinh mail thi ghi lien ket ra log de van thu nghiem duoc.
    public void sendPasswordReset(User user, String resetLink) {
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - lien ket dat lai mat khau cho {}: {}", user.getEmail(), resetLink);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
            message.setTo(user.getEmail());
            message.setSubject("Sóng Xanh Resort - Đặt lại mật khẩu");
            message.setText("Xin chào " + user.getFullName() + ",\n\n"
                    + "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.\n"
                    + "Nhấn vào liên kết dưới đây để tạo mật khẩu mới (có hiệu lực trong "
                    + UserService.RESET_TOKEN_VALID_MINUTES + " phút):\n\n"
                    + resetLink + "\n\n"
                    + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu hiện tại vẫn được giữ nguyên.\n\n"
                    + "Trân trọng,\nSóng Xanh Resort");
            mailSender.send(message);
            log.info("Da gui email dat lai mat khau toi {}", user.getEmail());
        } catch (Exception ex) {
            log.error("Gui email dat lai mat khau that bai toi {}", user.getEmail(), ex);
        }
    }

    // Link kich hoat tai khoan vua dang ky (xac thuc email). Chua cau hinh mail thi ghi link ra log de van thu duoc
    public void sendEmailVerification(User user, String link) {
        sendAccountMail(user, link, "Sóng Xanh Resort - Kích hoạt tài khoản",
                "Cảm ơn bạn đã đăng ký tài khoản tại Sóng Xanh Resort.\n"
                        + "Nhấn vào liên kết dưới đây để kích hoạt tài khoản (có hiệu lực trong "
                        + UserService.VERIFY_TOKEN_VALID_HOURS + " giờ):",
                "Nếu bạn không đăng ký, hãy bỏ qua email này - tài khoản sẽ không được kích hoạt.");
    }

    // Email da co tai khoan tu tao khi dat phong (chua co mat khau) ma co nguoi dang ky lai bang email nay:
    // gui link DAT MAT KHAU toi chinh email do - mat khau chi do chu email dat
    public void sendAccountSetup(User user, String link) {
        sendAccountMail(user, link, "Sóng Xanh Resort - Đặt mật khẩu cho tài khoản",
                "Email này đã được dùng để đặt phòng tại Sóng Xanh Resort, nên đã có sẵn tài khoản kèm các đơn của bạn.\n"
                        + "Nhấn vào liên kết dưới đây để đặt mật khẩu và bắt đầu dùng tài khoản (có hiệu lực trong "
                        + UserService.RESET_TOKEN_VALID_MINUTES + " phút):",
                "Nếu bạn không yêu cầu, hãy bỏ qua email này. Tài khoản của bạn vẫn an toàn.");
    }

    private void sendAccountMail(User user, String link, String subject, String intro, String outro) {
        if (senderEmail == null || senderEmail.isBlank() || senderPassword == null || senderPassword.isBlank()) {
            log.warn("Chua dat MAIL_USERNAME va MAIL_PASSWORD - lien ket cho {} ({}): {}", user.getEmail(), subject, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Sóng Xanh Resort <" + senderEmail + ">");
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText("Xin chào " + user.getFullName() + ",\n\n" + intro + "\n\n" + link + "\n\n" + outro
                + "\n\nTrân trọng,\nSóng Xanh Resort");
        String to = user.getEmail();
        deliverAfterCommit(() -> {
            try {
                mailSender.send(message);
                log.info("Da gui email '{}' toi {}", subject, to);
            } catch (Exception ex) {
                log.error("Gui email '{}' that bai toi {}", subject, to, ex);
            }
        });
    }

    // Gui hoa don thanh toan (HTML) sau khi check-out. Tra ve true neu gui thanh cong.
    // Gui that bai chi ghi log, khong lam hong luong check-out.
    public boolean sendInvoice(Booking booking, Payment payment, List<BookingCharge> charges) {
        return sendInvoice(booking, payment, charges, null);
    }

    // pdf != null: dinh kem hoa don PDF (HoaDon-<so hoa don>.pdf) de khach luu / in
    public boolean sendInvoice(Booking booking, Payment payment, List<BookingCharge> charges, byte[] pdf) {
        if (booking.getGuestEmail() == null || booking.getGuestEmail().isBlank()) {
            log.warn("Khong gui hoa don cho booking #{} vi email nguoi nhan trong", booking.getId());
            return false;
        }
        if (senderEmail == null || senderEmail.isBlank()
                || senderPassword == null || senderPassword.isBlank()) {
            log.error("Khong gui hoa don cho booking #{}: chua dat MAIL_USERNAME va MAIL_PASSWORD", booking.getId());
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, pdf != null, "UTF-8");
            helper.setFrom(senderEmail, "Sóng Xanh Resort");
            helper.setTo(booking.getGuestEmail());
            helper.setSubject("Sóng Xanh Resort - Hóa đơn " + payment.getInvoiceNo() + " (đơn #" + booking.getId() + ")");
            helper.setText(buildInvoiceHtml(booking, payment, charges), true);
            if (pdf != null) {
                helper.addAttachment(InvoicePdfService.fileName(payment.getInvoiceNo()),
                        new org.springframework.core.io.ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(message);
            log.info("Da gui hoa don {} toi {}", payment.getInvoiceNo(), booking.getGuestEmail());
            return true;
        } catch (Exception ex) {
            log.error("Gui hoa don that bai cho booking #{} toi {}", booking.getId(), booking.getGuestEmail(), ex);
            return false;
        }
    }

    // Email HTML dung inline style (Gmail bo qua the <style>), cung tong trang + xanh baby voi website
    private String buildInvoiceHtml(Booking booking, Payment payment, List<BookingCharge> charges) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        BigDecimal roomAmount = booking.getEffectiveRoomAmount();
        DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String paidAt = payment.getPaymentDate() != null
                ? payment.getPaymentDate().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")) : "";

        StringBuilder rows = new StringBuilder();
        rows.append(invoiceRow("Tiền phòng " + booking.getRoom().getRoomNumber() + " (" + booking.getRoom().getRoomType().getName() + ")",
                nights + " đêm", money(roomAmount)));
        if (booking.getCombo() != null) {
            rows.append(invoiceRow("Combo: " + booking.getCombo().getName(), "1", money(booking.getCombo().getPrice())));
        }
        if (booking.getDiscountCode() != null && booking.getDiscountAmount() != null
                && booking.getDiscountAmount().signum() > 0) {
            rows.append(invoiceRow("Giảm giá (mã " + booking.getDiscountCode().getCode() + ")", "",
                    "-" + money(booking.getDiscountAmount())));
        }
        if (booking.getPromotionAmountOrZero().signum() > 0) {
            rows.append(invoiceRow("Khuyến mãi: " + booking.getPromotionName() + " (-" + booking.getPromotionPercent() + "%)", "",
                    "-" + money(booking.getPromotionAmountOrZero())));
        }
        if (booking.getGiftVoucherAmountOrZero().signum() > 0) {
            rows.append(invoiceRow("Thẻ quà tặng " + booking.getGiftVoucherCode(), "", "-" + money(booking.getGiftVoucherAmountOrZero())));
        }
        if (booking.getPointsUsed() != null && booking.getPointsUsed() > 0) {
            rows.append(invoiceRow("Trừ điểm thưởng (" + booking.getPointsUsed() + " điểm)", "",
                    "-" + money(booking.getPointsDiscountAmount())));
        }
        for (BookingCharge charge : charges) {
            rows.append(invoiceRow("Phụ phí: " + charge.getLabel(), "1", money(charge.getAmount())));
        }
        if (booking.getDepositPaidAmount().signum() > 0) {
            rows.append(invoiceRow("Đã đặt cọc", "", "-" + money(booking.getDepositPaidAmount())));
        }

        return """
                <div style="margin:0;padding:24px;background:#f3faff;font-family:Arial,Helvetica,sans-serif;color:#14365a">
                  <div style="max-width:620px;margin:0 auto;background:#ffffff;border:1.5px solid #14365a;border-radius:14px">
                    <div style="padding:22px 26px;background:#9fd8f5;border-bottom:1.5px solid #14365a;border-radius:12px 12px 0 0">
                      <div style="font-size:12px;letter-spacing:2px;font-weight:bold">SÓNG XANH RESORT</div>
                      <div style="font-size:24px;font-weight:bold;margin-top:4px">Hóa đơn thanh toán</div>
                      <div style="margin-top:6px">Số: <b>%s</b> &middot; Ngày: %s</div>
                    </div>
                    <div style="padding:22px 26px">
                      <p style="margin:0 0 14px">Xin chào <b>%s</b>,</p>
                      <p style="margin:0 0 18px">Cảm ơn bạn đã lưu trú tại Sóng Xanh Resort. Dưới đây là hóa đơn cho đơn đặt phòng
                        <b>#%d</b> (nhận phòng %s, trả phòng %s).</p>
                      <table style="width:100%%;border-collapse:collapse;font-size:14px">
                        <tr style="background:#e3f4fd">
                          <th style="padding:10px 12px;text-align:left">Nội dung</th>
                          <th style="padding:10px 12px;text-align:right">Đơn giá</th>
                          <th style="padding:10px 12px;text-align:right">Thành tiền</th>
                        </tr>
                        %s
                        <tr>
                          <td colspan="2" style="padding:14px 12px;text-align:right;font-weight:bold">Thanh toán khi trả phòng</td>
                          <td style="padding:14px 12px;text-align:right;font-weight:bold;font-size:18px">%s VND</td>
                        </tr>
                      </table>
                      <p style="margin:18px 0 0">Phương thức: <b>%s</b> &middot; Trạng thái: <b style="color:#1d8a4e">%s</b></p>
                      <p style="margin:22px 0 0">Hẹn gặp lại bạn trong kỳ nghỉ tới!<br>Trân trọng,<br><b>Sóng Xanh Resort</b></p>
                    </div>
                  </div>
                </div>
                """.formatted(
                esc(payment.getInvoiceNo()), paidAt,
                esc(booking.getGuestName()),
                booking.getId(), booking.getCheckInDate().format(date), booking.getCheckOutDate().format(date),
                rows,
                money(payment.getAmount()),
                esc(payment.getPaymentMethod().getVietnameseLabel()), esc(payment.getStatus().getVietnameseLabel()));
    }

    private String invoiceRow(String name, String unit, String amount) {
        String cell = "padding:10px 12px;border-bottom:1px dashed #b9dcf0;";
        return "<tr><td style=\"" + cell + "\">" + esc(name) + "</td>"
                + "<td style=\"" + cell + "text-align:right\">" + esc(unit) + "</td>"
                + "<td style=\"" + cell + "text-align:right\">" + esc(amount) + "</td></tr>";
    }

    // Dinh dang tien kieu Viet Nam: 1.250.000
    private String money(BigDecimal value) {
        return Money.format(value);
    }

    private String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private String buildSubject(Booking booking) {
        return switch (booking.getStatus()) {
            case PENDING -> "Sóng Xanh Resort - Da nhan yeu cau dat phong #" + booking.getId();
            case CONFIRMED -> "Sóng Xanh Resort - Don dat phong #" + booking.getId() + " da duoc xac nhan";
            case CANCELLED -> "Sóng Xanh Resort - Don dat phong #" + booking.getId() + " da bi huy";
            case CHECKED_IN -> "Sóng Xanh Resort - Ban da check-in booking #" + booking.getId();
            case CHECKED_OUT -> "Sóng Xanh Resort - Ban da check-out booking #" + booking.getId();
        };
    }

    // Huong dan chuyen coc (don moi dat) / bao da nhan coc / bao huy vi qua han coc
    private void appendDepositInfo(StringBuilder sb, Booking booking) {
        if (!booking.isDepositRequired()) {
            return;
        }
        DateTimeFormatter deadlineFmt = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
        if (booking.isAwaitingDeposit()) {
            sb.append("ĐẶT CỌC ĐỂ GIỮ PHÒNG\n");
            sb.append("Số tiền cọc: ").append(money(booking.getDepositAmount())).append(" VND\n");
            sb.append("Ngân hàng: ").append(bankId).append(" - STK: ").append(accountNo)
                    .append(" - Chủ TK: ").append(accountName).append("\n");
            sb.append("Nội dung chuyển khoản: HB").append(booking.getId()).append(" COC\n");
            if (booking.getDepositDeadline() != null) {
                sb.append("Hạn chuyển cọc: ").append(booking.getDepositDeadline().format(deadlineFmt))
                        .append(". Quá hạn chưa nhận được cọc, đơn sẽ tự động hủy.\n");
            }
            sb.append("Phần còn lại thanh toán khi trả phòng.\n\n");
        } else if (booking.getStatus() == BookingStatus.CANCELLED) {
            if (booking.isDepositPaid() && booking.getRefundPercent() != null) {
                sb.append("Bạn đã đặt cọc ").append(money(booking.getDepositAmount())).append(" VND. ");
                if (booking.getRefundAmount() != null && booking.getRefundAmount().signum() > 0) {
                    sb.append("Theo chính sách hủy, bạn được hoàn ").append(booking.getRefundPercent()).append("% tiền cọc: ")
                            .append(money(booking.getRefundAmount()))
                            .append(" VND. Resort sẽ chuyển khoản lại vào tài khoản bạn đã dùng để đặt cọc"
                                    + " (cần đổi tài khoản nhận, vui lòng liên hệ hotline/Zalo).\n\n");
                } else {
                    sb.append("Do hủy sát ngày nhận phòng, theo chính sách hủy tiền cọc không được hoàn lại.\n\n");
                }
            } else if (booking.isDepositPaid()) {
                sb.append("Bạn đã đặt cọc ").append(money(booking.getDepositAmount()))
                        .append(" VND. Resort sẽ liên hệ với bạn về việc hoàn cọc.\n\n");
            } else if (booking.getDepositDeadline() != null && java.time.LocalDateTime.now().isAfter(booking.getDepositDeadline())) {
                sb.append("Đơn đã bị hủy do chưa nhận được tiền cọc trước hạn. Bạn có thể đặt lại trên website.\n\n");
            }
        } else if (booking.isDepositPaid()) {
            sb.append("Đã nhận tiền cọc: ").append(money(booking.getDepositAmount()))
                    .append(" VND. Phần còn lại thanh toán khi trả phòng.\n\n");
        }
    }

    private String buildBody(Booking booking) {
        return buildBody(booking, "Cảm ơn bạn đã đặt phòng tại Sóng Xanh Resort. Thông tin đơn đặt phòng:");
    }

    private String buildBody(Booking booking, String intro) {
        StringBuilder sb = new StringBuilder();
        sb.append("Xin chào ").append(booking.getGuestName()).append(",\n\n");
        sb.append(intro).append("\n\n");
        sb.append("Mã đơn: #").append(booking.getId()).append("\n");
        sb.append("Phòng: ").append(booking.getRoom().getRoomNumber())
                .append(" - ").append(booking.getRoom().getRoomType().getName()).append("\n");
        sb.append("Ngày nhận phòng: ").append(booking.getCheckInDate())
                .append(" lúc ").append(booking.getCheckInTime()).append("\n");
        sb.append("Ngày trả phòng: ").append(booking.getCheckOutDate())
                .append(" lúc ").append(booking.getCheckOutTime()).append("\n");
        if (booking.getCombo() != null) {
            sb.append("Combo: ").append(booking.getCombo().getName()).append("\n");
        }
        sb.append("Tổng tiền: ").append(money(booking.getTotalAmount())).append(" VND\n\n");
        sb.append("Trạng thái đơn: ").append(booking.getStatus().getVietnameseLabel()).append("\n\n");
        appendDepositInfo(sb, booking);
        if (baseUrl != null && !baseUrl.isBlank() && booking.getAccessToken() != null) {
            sb.append("Xem chi tiết đơn: ").append(baseUrl.replaceAll("/+$", ""))
                    .append("/customer/bookings/").append(booking.getId())
                    .append("?token=").append(booking.getAccessToken()).append("\n");
            sb.append("(Vui lòng không chia sẻ đường link này.)\n\n");
        }
        sb.append("Trân trọng,\nSóng Xanh Resort");
        return sb.toString();
    }
}
