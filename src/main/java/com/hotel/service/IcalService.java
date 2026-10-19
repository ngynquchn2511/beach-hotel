package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.PricingSettings;
import com.hotel.entity.Room;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.PricingSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

// Xuat lich dat phong ra file iCal (.ics, RFC 5545) de dong bo voi Google Calendar / Booking.com / Airbnb.
// Moi phong 1 link + 1 link tat ca phong, kem ma bi mat (cac app lich khong dang nhap duoc).
// Chi ghi so phong + ma don + trang thai, KHONG ghi ten / SDT khach (link co the bi chia se).
@Service
public class IcalService {

    // Lay don tu 30 ngay truoc toi 2 nam toi
    private static final int PAST_DAYS = 30;
    private static final int FUTURE_DAYS = 730;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final PricingSettingsRepository settingsRepository;
    private final BookingRepository bookingRepository;
    private final PricingService pricingService;

    public IcalService(PricingSettingsRepository settingsRepository, BookingRepository bookingRepository,
                       PricingService pricingService) {
        this.settingsRepository = settingsRepository;
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
    }

    // Ma hien tai, chua co thi tao moi
    @Transactional
    public String token() {
        PricingSettings settings = pricingService.getSettings();
        if (settings.getIcalToken() == null) {
            settings.setIcalToken(newToken());
            settingsRepository.save(settings);
        }
        return settings.getIcalToken();
    }

    // Doi ma: cac link cu het hieu luc ngay (dung khi lo link)
    @Transactional
    public String rotateToken() {
        PricingSettings settings = pricingService.getSettings();
        settings.setIcalToken(newToken());
        settingsRepository.save(settings);
        return settings.getIcalToken();
    }

    public boolean isValid(String key) {
        String current = pricingService.getSettings().getIcalToken();
        return current != null && key != null
                && MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }

    private com.hotel.repository.OtaBlockRepository otaBlockRepository;

    @org.springframework.beans.factory.annotation.Autowired
    void setOtaBlockRepository(com.hotel.repository.OtaBlockRepository otaBlockRepository) {
        this.otaBlockRepository = otaBlockRepository;
    }

    @Transactional(readOnly = true)
    public String calendar(Long roomId, String calendarName) {
        return calendar(roomId, calendarName, null);
    }

    // roomId = null: tat ca phong. forChannel: link danh cho kenh nao (VD Booking.com) -> khong xuat lai cac luot dat
    // den tu chinh kenh do (kenh tu biet), chi xuat don website + luot dat cua kenh khac (VD Airbnb) de kenh khoa ngay
    @Transactional(readOnly = true)
    public String calendar(Long roomId, String calendarName, com.hotel.entity.OtaFeed.Channel forChannel) {
        LocalDate today = LocalDate.now();
        java.util.Map<Long, com.hotel.entity.OtaFeed.Channel> bookingChannel = new java.util.HashMap<>();
        List<com.hotel.entity.OtaBlock> blocks = List.of();
        if (otaBlockRepository != null) {
            otaBlockRepository.findLinked().forEach(o -> bookingChannel.put(o.getBooking().getId(), o.getFeed().getChannel()));
            blocks = otaBlockRepository.findBlockingBetween(today.minusDays(PAST_DAYS), today.plusDays(FUTURE_DAYS)).stream()
                    .filter(o -> roomId == null || o.getRoom().getId().equals(roomId))
                    .filter(o -> forChannel == null || o.getFeed().getChannel() != forChannel)
                    .toList();
        }
        List<Booking> bookings = bookingRepository.findForCalendar(today.minusDays(PAST_DAYS), today.plusDays(FUTURE_DAYS))
                .stream()
                .filter(b -> roomId == null || b.getRoom().getId().equals(roomId))
                .filter(b -> forChannel == null || bookingChannel.get(b.getId()) != forChannel)
                .toList();
        String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(STAMP);
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Song Xanh Resort//Lich phong//VI");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + escape(calendarName));
        line(ics, "X-WR-TIMEZONE:Asia/Ho_Chi_Minh");
        for (Booking b : bookings) {
            Room room = b.getRoom();
            boolean firm = b.getStatus() != BookingStatus.PENDING;
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:booking-" + b.getId() + "@songxanhresort");
            line(ics, "DTSTAMP:" + stamp);
            // Ngay ca ngay: DTEND la ngay tra phong (khong tinh) -> dem cuoi la dem truoc ngay tra
            line(ics, "DTSTART;VALUE=DATE:" + b.getCheckInDate().format(DATE));
            line(ics, "DTEND;VALUE=DATE:" + b.getCheckOutDate().format(DATE));
            line(ics, "SUMMARY:" + escape("Phòng " + room.getRoomNumber() + " · Đã đặt (#" + b.getId() + ")"
                    + (firm ? "" : " - chờ xác nhận")));
            line(ics, "DESCRIPTION:" + escape("Đơn #" + b.getId() + " · nhận " + b.getCheckInTime()
                    + " · trả " + b.getCheckOutTime() + " · " + b.getStatus().name()));
            line(ics, "STATUS:" + (firm ? "CONFIRMED" : "TENTATIVE"));
            line(ics, "TRANSP:OPAQUE");
            line(ics, "END:VEVENT");
        }
        for (com.hotel.entity.OtaBlock o : blocks) {
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:ota-" + o.getId() + "@songxanhresort");
            line(ics, "DTSTAMP:" + stamp);
            line(ics, "DTSTART;VALUE=DATE:" + o.getStartDate().format(DATE));
            line(ics, "DTEND;VALUE=DATE:" + o.getEndDate().format(DATE));
            line(ics, "SUMMARY:" + escape("Phòng " + o.getRoom().getRoomNumber() + " · Đã đặt qua " + o.getFeed().getChannel().getLabel()));
            line(ics, "STATUS:CONFIRMED");
            line(ics, "TRANSP:OPAQUE");
            line(ics, "END:VEVENT");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // RFC 5545: escape \ ; , va xuong dong
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    // RFC 5545: moi dong ket thuc CRLF, dai qua 75 byte thi gap dong (dong sau bat dau bang 1 dau cach),
    // khong cat giua ky tu UTF-8 nhieu byte
    static void line(StringBuilder out, String content) {
        int bytes = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 75) {
                out.append("\r\n ");
                bytes = 1;
            }
            out.appendCodePoint(cp);
            bytes += size;
            i += Character.charCount(cp);
        }
        out.append("\r\n");
    }
}
