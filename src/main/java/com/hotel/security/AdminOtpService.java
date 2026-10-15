package com.hotel.security;

import com.hotel.entity.User;
import com.hotel.service.EmailService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

// Xac thuc 2 buoc cho cong quan tri: dung mat khau xong -> gui ma 6 so qua email -> nhap dung moi vao duoc /admin/**.
// Trang thai luu trong session (ma chi luu dang bam SHA-256). Het han 5 phut, sai toi da 5 lan, gui lai sau 60 giay.
// Chua cau hinh mail thi bo qua buoc nay (ghi canh bao) de khong khoa admin ngoai he thong.
@Service
public class AdminOtpService {

    private static final Logger log = LoggerFactory.getLogger(AdminOtpService.class);

    public static final String PENDING = "ADMIN_OTP_PENDING";
    private static final String HASH = "ADMIN_OTP_HASH";
    private static final String EXPIRES = "ADMIN_OTP_EXPIRES";
    private static final String ATTEMPTS = "ADMIN_OTP_ATTEMPTS";
    private static final String SENT_AT = "ADMIN_OTP_SENT_AT";
    private static final String EMAIL = "ADMIN_OTP_EMAIL";

    public static final Duration VALIDITY = Duration.ofMinutes(5);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;

    public enum Result { OK, WRONG, EXPIRED, TOO_MANY, NOT_PENDING }

    private final EmailService emailService;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    public AdminOtpService(EmailService emailService, @Value("${app.security.admin-otp:true}") boolean enabled) {
        this.emailService = emailService;
        this.enabled = enabled;
    }

    public boolean isRequired() {
        if (!enabled) {
            return false;
        }
        if (!emailService.isConfigured()) {
            log.warn("Bat xac thuc 2 buoc cho cong quan tri nhung chua cau hinh MAIL_USERNAME / MAIL_PASSWORD - bo qua buoc OTP");
            return false;
        }
        return true;
    }

    public static boolean isPending(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(PENDING));
    }

    // Sau khi dung mat khau: tao ma moi, gui email, danh dau phien dang cho OTP
    public void start(HttpSession session, User user) {
        session.setAttribute(PENDING, Boolean.TRUE);
        session.setAttribute(EMAIL, user.getEmail());
        send(session, user.getEmail(), user.getFullName());
    }

    // Gui lai ma (moi lan gui la ma moi). false = chua het thoi gian cho
    public boolean resend(HttpSession session, String fullName) {
        Instant sentAt = (Instant) session.getAttribute(SENT_AT);
        if (sentAt != null && Instant.now().isBefore(sentAt.plus(RESEND_COOLDOWN))) {
            return false;
        }
        send(session, (String) session.getAttribute(EMAIL), fullName);
        return true;
    }

    public Result verify(HttpSession session, String code) {
        if (!isPending(session)) {
            return Result.NOT_PENDING;
        }
        int attempts = session.getAttribute(ATTEMPTS) instanceof Integer n ? n : 0;
        if (attempts >= MAX_ATTEMPTS) {
            return Result.TOO_MANY;
        }
        Instant expires = (Instant) session.getAttribute(EXPIRES);
        if (expires == null || Instant.now().isAfter(expires)) {
            return Result.EXPIRED;
        }
        String clean = code == null ? "" : code.replaceAll("\\s", "");
        String expected = (String) session.getAttribute(HASH);
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                sha256(clean).getBytes(StandardCharsets.US_ASCII))) {
            session.setAttribute(ATTEMPTS, attempts + 1);
            return attempts + 1 >= MAX_ATTEMPTS ? Result.TOO_MANY : Result.WRONG;
        }
        clear(session);
        return Result.OK;
    }

    public static String maskedEmail(HttpSession session) {
        String email = session == null ? null : (String) session.getAttribute(EMAIL);
        if (email == null || !email.contains("@")) {
            return "";
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    private void send(HttpSession session, String email, String fullName) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(HASH, sha256(code));
        session.setAttribute(EXPIRES, Instant.now().plus(VALIDITY));
        session.setAttribute(SENT_AT, Instant.now());
        session.setAttribute(ATTEMPTS, 0);
        emailService.sendAdminOtp(email, fullName, code, VALIDITY.toMinutes());
    }

    private static void clear(HttpSession session) {
        for (String key : new String[]{PENDING, HASH, EXPIRES, ATTEMPTS, SENT_AT, EMAIL}) {
            session.removeAttribute(key);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
