package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.OtaBlock;
import com.hotel.entity.OtaFeed;
import com.hotel.entity.Room;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.OtaBlockRepository;
import com.hotel.repository.OtaFeedRepository;
import com.hotel.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

// Dong bo 2 chieu voi kenh OTA qua iCal:
//  - Chieu vao (file nay): doc link lich iCal ma Booking.com / Airbnb cap cho tung phong, moi dot khach dat tren kenh
//    thanh 1 OtaBlock khoa phong tren website (tim phong, dat phong, doi ngay deu khong cho dat trung).
//  - Chieu ra (IcalService): website xuat lich phong da ban (ca don website lan dot cua kenh khac) de kenh khoa ngay.
// Kenh chi cho biet ngay bi chiem (khong co ten khach / gia) -> le tan "ghi nhan" thanh don khi khach toi de check-in.
@Service
public class OtaSyncService {

    private static final Logger log = LoggerFactory.getLogger(OtaSyncService.class);
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final OtaFeedRepository feedRepository;
    private final OtaBlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final StaffNotificationService notifier;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    // Tai noi dung link (test thay bang ham tra chuoi co dinh)
    private Function<String, String> fetcher = this::download;

    public OtaSyncService(OtaFeedRepository feedRepository, OtaBlockRepository blockRepository, RoomRepository roomRepository,
                          BookingRepository bookingRepository, StaffNotificationService notifier) {
        this.feedRepository = feedRepository;
        this.blockRepository = blockRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.notifier = notifier;
    }

    void setFetcher(Function<String, String> fetcher) {
        this.fetcher = fetcher;
    }

    // ===================== Doc file iCal =====================

    public record Event(String uid, LocalDate start, LocalDate end, String summary) {
    }

    // RFC 5545: bo gap dong (dong sau bat dau bang dau cach / tab), lay UID, DTSTART, DTEND, SUMMARY trong moi VEVENT.
    // Ngay dang VALUE=DATE (20261110) hoac ngay gio (20261110T140000Z) -> lay phan ngay. Thieu DTEND = 1 dem
    public static List<Event> parse(String ics) {
        List<Event> out = new ArrayList<>();
        if (ics == null) {
            return out;
        }
        String unfolded = ics.replace("\r\n", "\n").replace("\r", "\n").replaceAll("\n[ \t]", "");
        String uid = null, summary = null;
        LocalDate start = null, end = null;
        boolean inEvent = false;
        for (String raw : unfolded.split("\n")) {
            String line = raw.strip();
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) {
                inEvent = true;
                uid = summary = null;
                start = end = null;
                continue;
            }
            if (line.equalsIgnoreCase("END:VEVENT")) {
                inEvent = false;
                if (start != null) {
                    LocalDate e = end != null ? end : start.plusDays(1);
                    if (e.isAfter(start)) {
                        String id = uid != null ? uid : "no-uid-" + start + "-" + e;
                        out.add(new Event(id.length() > 255 ? id.substring(0, 255) : id, start, e, summary));
                    }
                }
                continue;
            }
            if (!inEvent) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).toUpperCase(Locale.ROOT);
            String value = line.substring(colon + 1);
            int semi = name.indexOf(';');
            String key = semi < 0 ? name : name.substring(0, semi);
            switch (key) {
                case "UID" -> uid = value.strip();
                case "SUMMARY" -> summary = unescape(value).strip();
                case "DTSTART" -> start = date(value);
                case "DTEND" -> end = date(value);
                default -> {
                }
            }
        }
        return out;
    }

    private static LocalDate date(String value) {
        String v = value.strip();
        if (v.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(v.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String unescape(String v) {
        return v.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    // ===================== Quan ly link =====================

    public List<OtaFeed> feeds() {
        return feedRepository.findAllWithRoom();
    }

    // Chi nhan link https toi may chu cong khai (khong cho tro vao mang noi bo / chinh may chu)
    static String validateUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (u.startsWith("webcal://")) {
            u = "https://" + u.substring("webcal://".length());
        }
        URI uri;
        try {
            uri = URI.create(u);
        } catch (Exception ex) {
            throw new BusinessException("Link lịch không hợp lệ");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || u.length() > 600) {
            throw new BusinessException("Link lịch phải bắt đầu bằng https:// (lấy ở mục xuất lịch / đồng bộ lịch của Booking.com hoặc Airbnb)");
        }
        return u;
    }

    private static void ensurePublicHost(URI uri) {
        try {
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
                    throw new BusinessException("Link lịch trỏ vào địa chỉ nội bộ, không được phép");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw new BusinessException("Không tìm thấy máy chủ của link lịch");
        }
    }

    @Transactional
    public OtaFeed addFeed(Long roomId, OtaFeed.Channel channel, String url) {
        if (roomId == null || channel == null) {
            throw new BusinessException("Vui lòng chọn phòng và kênh");
        }
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new BusinessException("Không tìm thấy phòng"));
        if (channel != OtaFeed.Channel.OTHER && feedRepository.existsByRoomIdAndChannel(roomId, channel)) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đã có link " + channel.getLabel() + " - xóa link cũ trước khi thêm link mới");
        }
        return feedRepository.save(OtaFeed.builder().room(room).channel(channel).url(validateUrl(url)).active(true).build());
    }

    @Transactional
    public void toggle(Long feedId) {
        OtaFeed f = feed(feedId);
        f.setActive(!f.isOpen());
        feedRepository.save(f);
    }

    // Xoa link: bo cac dot chua ghi nhan (phong mo ban lai); don da ghi nhan giu nguyen
    @Transactional
    public void deleteFeed(Long feedId) {
        OtaFeed f = feed(feedId);
        blockRepository.deleteAll(blockRepository.findByFeedId(feedId));
        feedRepository.delete(f);
    }

    private OtaFeed feed(Long id) {
        return feedRepository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy link lịch"));
    }

    // ===================== Dong bo =====================

    public record SyncResult(int events, int added, int updated, int removed, List<String> conflicts) {
    }

    private String download(String url) {
        URI uri = URI.create(validateUrl(url));
        ensurePublicHost(uri);
        try {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "SongXanhResort-CalendarSync/1.0").GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                throw new BusinessException("Kênh trả về mã " + res.statusCode());
            }
            try (InputStream in = res.body()) {
                byte[] data = in.readNBytes(MAX_BYTES + 1);
                if (data.length > MAX_BYTES) {
                    throw new BusinessException("Tệp lịch quá lớn");
                }
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Đồng bộ bị gián đoạn");
        } catch (Exception ex) {
            throw new BusinessException("Không tải được lịch: " + ex.getClass().getSimpleName());
        }
    }

    // Dong bo tat ca link dang bat (bo hen gio goi dinh ky); loi 1 link khong anh huong link khac
    public int syncAll() {
        int ok = 0;
        for (OtaFeed f : feedRepository.findByActiveTrue()) {
            try {
                sync(f.getId());
                ok++;
            } catch (Exception ex) {
                log.warn("Dong bo lich OTA #{} that bai: {}", f.getId(), ex.getMessage());
            }
        }
        return ok;
    }

    @Transactional
    public SyncResult sync(Long feedId) {
        OtaFeed f = feed(feedId);
        String content;
        try {
            content = fetcher.apply(f.getUrl());
            if (content == null || !content.contains("BEGIN:VCALENDAR")) {
                throw new BusinessException("Nội dung không phải lịch iCal (kiểm tra lại link)");
            }
        } catch (BusinessException ex) {
            f.setLastSyncAt(LocalDateTime.now());
            f.setLastOk(false);
            f.setLastMessage(cut(ex.getMessage(), 300));
            feedRepository.save(f);
            throw ex;
        }
        return syncContent(f, content);
    }

    // Cap nhat cac dot cua 1 link theo noi dung lich moi tai:
    //  - dot moi -> them (canh bao neu trung don website), dot doi ngay -> cap nhat,
    //  - dot bien mat (khach huy tren kenh): chua ghi nhan -> xoa (phong mo ban lai); da ghi nhan -> danh dau, bao le tan
    @Transactional
    public SyncResult syncContent(OtaFeed f, String content) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = parse(content).stream().filter(e -> e.end().isAfter(today.minusDays(1))).toList();
        Map<String, OtaBlock> existing = new HashMap<>();
        for (OtaBlock b : blockRepository.findByFeedId(f.getId())) {
            existing.put(b.getUid(), b);
        }
        Room room = f.getRoom();
        int added = 0, updated = 0, removed = 0;
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Event e : events) {
            seen.add(e.uid());
            OtaBlock b = existing.get(e.uid());
            boolean changed;
            if (b == null) {
                b = OtaBlock.builder().feed(f).room(room).uid(e.uid()).startDate(e.start()).endDate(e.end())
                        .summary(cut(e.summary(), 200)).syncedAt(now).build();
                added++;
                changed = true;
            } else {
                changed = !b.getStartDate().equals(e.start()) || !b.getEndDate().equals(e.end()) || b.getRemovedAt() != null;
                b.setStartDate(e.start());
                b.setEndDate(e.end());
                b.setSummary(cut(e.summary(), 200));
                b.setRemovedAt(null);
                b.setSyncedAt(now);
                if (changed) {
                    updated++;
                }
            }
            blockRepository.save(b);
            if (changed && b.getBooking() == null
                    && bookingRepository.existsOverlappingBooking(room.getId(), e.start(), e.end())) {
                conflicts.add("Phòng " + room.getRoomNumber() + " " + e.start().format(DAY) + " - " + e.end().format(DAY));
            }
        }
        for (OtaBlock b : existing.values()) {
            if (seen.contains(b.getUid()) || b.getEndDate().isBefore(today)) {
                continue;
            }
            if (b.getBooking() == null) {
                blockRepository.delete(b);
                removed++;
            } else if (b.getRemovedAt() == null) {
                b.setRemovedAt(now);
                blockRepository.save(b);
                removed++;
                notifier.notify("ota", StaffNotificationService.Level.WARNING,
                        f.getChannel().getLabel() + " đã hủy đặt phòng " + room.getRoomNumber(),
                        "Đơn #" + b.getBooking().getId() + " (" + b.getStartDate().format(DAY) + " - " + b.getEndDate().format(DAY)
                                + ") không còn trên lịch của kênh - kiểm tra và hủy đơn nếu đúng",
                        "/admin/bookings/" + b.getBooking().getId());
            }
        }
        for (String c : conflicts) {
            notifier.notify("ota", StaffNotificationService.Level.WARNING, "Trùng lịch với " + f.getChannel().getLabel(),
                    c + " đã có khách đặt trên website - liên hệ khách / kênh để đổi phòng", "/admin/ota");
        }
        f.setLastSyncAt(now);
        f.setLastOk(true);
        f.setEventCount(events.size());
        f.setLastMessage(cut("Đọc " + events.size() + " lượt đặt · thêm " + added + " · đổi " + updated + " · bỏ " + removed
                + (conflicts.isEmpty() ? "" : " · " + conflicts.size() + " trùng lịch"), 300));
        feedRepository.save(f);
        return new SyncResult(events.size(), added, updated, removed, conflicts);
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    // ===================== Danh sach cho le tan =====================

    public record Row(OtaBlock block, boolean conflict) {
    }

    @Transactional(readOnly = true)
    public List<Row> upcoming() {
        List<Row> rows = new ArrayList<>();
        for (OtaBlock b : blockRepository.findUpcoming(LocalDate.now())) {
            boolean conflict = b.isBlocking()
                    && bookingRepository.existsOverlappingBooking(b.getRoom().getId(), b.getStartDate(), b.getEndDate());
            rows.add(new Row(b, conflict));
        }
        return rows;
    }

    public OtaBlock block(Long id) {
        return blockRepository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy lượt đặt từ kênh"));
    }

    @Transactional
    public void link(Long blockId, Booking booking) {
        OtaBlock b = block(blockId);
        b.setBooking(booking);
        blockRepository.save(b);
    }

    // Le tan xac nhan da xu ly dot bi kenh huy (da huy don hoac khach van o) -> an khoi danh sach
    @Transactional
    public void dismissRemoved(Long blockId) {
        OtaBlock b = block(blockId);
        if (b.getRemovedAt() == null) {
            throw new BusinessException("Lượt đặt này vẫn còn trên lịch của kênh");
        }
        blockRepository.delete(b);
    }
}
