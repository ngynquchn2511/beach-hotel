package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.QuickReply;
import com.hotel.exception.BusinessException;
import com.hotel.repository.QuickReplyRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Mau tra loi nhanh: admin soan san, le tan chon trong khung tra loi tin nhan cua don -> dien san bien theo don
@Service
public class QuickReplyService {

    public static final int TITLE_MAX = 80;
    public static final int BODY_MAX = 1000;
    public static final List<String> VARIABLES = List.of("{ten_khach}", "{ma_don}", "{so_phong}", "{ngay_den}", "{ngay_di}", "{gio_nhan}", "{gio_tra}");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final QuickReplyRepository repository;

    public QuickReplyService(QuickReplyRepository repository) {
        this.repository = repository;
    }

    public record Rendered(Long id, String title, String text) {
    }

    public List<QuickReply> all() {
        return repository.findAllByOrderBySortOrderAscIdAsc();
    }

    // Mau dang dung, da thay bien theo don (de chen vao o tra loi)
    public List<Rendered> forBooking(Booking booking) {
        return repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(q -> new Rendered(q.getId(), q.getTitle(), render(q.getBody(), booking))).toList();
    }

    public static String render(String body, Booking b) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("{ten_khach}", b.getGuestName() != null ? b.getGuestName() : "");
        v.put("{ma_don}", String.valueOf(b.getId()));
        v.put("{so_phong}", b.getRoom() != null ? b.getRoom().getRoomNumber() : "");
        v.put("{ngay_den}", b.getCheckInDate() != null ? b.getCheckInDate().format(DAY) : "");
        v.put("{ngay_di}", b.getCheckOutDate() != null ? b.getCheckOutDate().format(DAY) : "");
        v.put("{gio_nhan}", b.getCheckInTime() != null ? b.getCheckInTime().format(TIME) : "");
        v.put("{gio_tra}", b.getCheckOutTime() != null ? b.getCheckOutTime().format(TIME) : "");
        String out = body;
        for (Map.Entry<String, String> e : v.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    @Transactional
    public QuickReply create(String title, String body, Integer sortOrder) {
        return repository.save(QuickReply.builder().title(cleanTitle(title)).body(cleanBody(body))
                .sortOrder(sortOrder != null ? sortOrder : (int) repository.count() + 1).active(true).build());
    }

    @Transactional
    public QuickReply update(Long id, String title, String body, Integer sortOrder) {
        QuickReply q = find(id);
        q.setTitle(cleanTitle(title));
        q.setBody(cleanBody(body));
        if (sortOrder != null) {
            q.setSortOrder(sortOrder);
        }
        return repository.save(q);
    }

    @Transactional
    public void toggle(Long id) {
        QuickReply q = find(id);
        q.setActive(!q.isOpen());
        repository.save(q);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private QuickReply find(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy mẫu trả lời"));
    }

    private static String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > TITLE_MAX) {
            throw new BusinessException("Tên mẫu không được trống và tối đa " + TITLE_MAX + " ký tự");
        }
        return t;
    }

    private static String cleanBody(String body) {
        String t = body == null ? "" : body.trim();
        if (t.isEmpty() || t.length() > BODY_MAX) {
            throw new BusinessException("Nội dung mẫu không được trống và tối đa " + BODY_MAX + " ký tự");
        }
        return t;
    }

    // Lan dau chay (bang trong): tao vai mau dung thong tin co that trong he thong.
    // Mau wifi can admin dien ten mang / mat khau that nen de tam ngung
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (repository.count() > 0) {
            return;
        }
        String[][] defaults = {
                {"Đã nhận yêu cầu", "Dạ chào anh/chị {ten_khach}, lễ tân đã nhận được tin nhắn cho đơn #{ma_don} và sẽ phản hồi sớm nhất ạ.", "1"},
                {"Giờ nhận / trả phòng", "Dạ đơn #{ma_don} nhận phòng từ {gio_nhan} ngày {ngay_den} và trả phòng trước {gio_tra} ngày {ngay_di}. "
                        + "Nếu cần nhận sớm hoặc trả muộn, anh/chị gửi yêu cầu ngay trên trang đơn giúp em ạ.", "1"},
                {"Đưa đón sân bay", "Dạ resort có xe đón các sân bay Vân Đồn, Nội Bài, Cát Bi. Anh/chị đặt ở mục Đưa đón sân bay trên trang đơn, "
                        + "lễ tân sẽ xác nhận và giá tính theo loại xe ạ.", "1"},
                {"Phòng đã sẵn sàng", "Dạ phòng {so_phong} đã sẵn sàng, anh/chị tới nơi là nhận phòng được ngay ạ.", "1"},
                {"Cảm ơn sau kỳ nghỉ", "Cảm ơn anh/chị {ten_khach} đã lưu trú tại Sóng Xanh. Rất mong được đón tiếp anh/chị lần sau!", "1"},
                {"Wifi (cần điền trước khi bật)", "Dạ wifi của resort: tên mạng [điền tên mạng], mật khẩu [điền mật khẩu] ạ.", "0"},
        };
        for (int i = 0; i < defaults.length; i++) {
            repository.save(QuickReply.builder().title(defaults[i][0]).body(defaults[i][1]).sortOrder(i + 1)
                    .active("1".equals(defaults[i][2])).build());
        }
    }
}
