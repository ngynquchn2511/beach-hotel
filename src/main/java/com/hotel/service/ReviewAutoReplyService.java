package com.hotel.service;

import com.hotel.entity.Review;
import com.hotel.entity.ReviewReplyRule;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ReviewReplyRuleRepository;
import com.hotel.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Tu dong tra loi danh gia cua khach theo tu khoa: khach gui danh gia -> tim quy tac dau tien khop
// (so sao trong khoang + noi dung co tu khoa) -> dien mau -> dang cau tra loi cong khai + email bao khach.
// Quy tac co tu khoa duoc xet truoc quy tac du phong (tu khoa trong)
@Service
public class ReviewAutoReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReviewAutoReplyService.class);

    public static final int NAME_MAX = 80;
    public static final int KEYWORDS_MAX = 500;
    public static final int BODY_MAX = 1000;
    public static final List<String> VARIABLES = List.of("{ten_khach}", "{so_sao}", "{loai_phong}", "{tu_khoa}");

    // Ket qua khop: quy tac, tu khoa da khop (null neu quy tac du phong), cau tra loi da dien
    public record Match(ReviewReplyRule rule, String keyword, String text) {
    }

    private final ReviewReplyRuleRepository ruleRepository;
    private final ReviewRepository reviewRepository;
    private final EmailService emailService;
    private boolean enabled;

    public ReviewAutoReplyService(ReviewReplyRuleRepository ruleRepository, ReviewRepository reviewRepository,
                                  EmailService emailService,
                                  @Value("${app.reviews.auto-reply:true}") boolean enabled) {
        this.ruleRepository = ruleRepository;
        this.reviewRepository = reviewRepository;
        this.emailService = emailService;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // Test bat / tat tu dong tra loi khi khach gui danh gia
    public void setEnabledForTest(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ReviewReplyRule> all() {
        return ruleRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    // ===== Khop quy tac =====

    @Transactional(readOnly = true)
    public Optional<Match> match(Integer rating, String comment, String guestName, String roomType) {
        if (rating == null) {
            return Optional.empty();
        }
        List<ReviewReplyRule> rules = all().stream()
                .filter(ReviewReplyRule::isOpen)
                .filter(r -> rating >= r.getMinRating() && rating <= r.getMaxRating())
                .toList();
        String lower = pad(comment == null ? "" : comment.toLowerCase(Locale.ROOT));
        String plain = pad(strip(comment));
        for (ReviewReplyRule r : rules) {
            if (r.isFallback()) {
                continue;
            }
            for (String kw : keywords(r.getKeywords())) {
                if (contains(lower, plain, kw)) {
                    return Optional.of(new Match(r, kw, render(r.getBody(), guestName, rating, roomType, kw)));
                }
            }
        }
        return rules.stream().filter(ReviewReplyRule::isFallback).findFirst()
                .map(r -> new Match(r, null, render(r.getBody(), guestName, rating, roomType, null)));
    }

    public Optional<Match> match(Review review) {
        String roomType = review.getBooking() != null && review.getBooking().getRoom() != null
                ? review.getBooking().getRoom().getRoomType().getName() : null;
        return match(review.getRating(), review.getComment(), review.getDisplayName(), roomType);
    }

    // Tu khoa co dau: so voi noi dung giu dau (tranh "ồn" khop nham "on"); tu khoa khong dau: so voi noi dung bo dau.
    // Tu khoa co dau dai (>= 4 chu cai hoac nhieu tu) cung khop khi khach go khong dau. Khop tron tu (khong khop giua tu)
    static boolean contains(String lowerPadded, String plainPadded, String keyword) {
        String kw = pad(keyword.toLowerCase(Locale.ROOT)).trim();
        String kwPlain = strip(kw);
        if (kwPlain.isEmpty()) {
            return false;
        }
        if (kwPlain.equals(kw)) {
            return plainPadded.contains(" " + kwPlain + " ");
        }
        if (lowerPadded.contains(" " + kw + " ")) {
            return true;
        }
        return (kwPlain.length() >= 4 || kwPlain.contains(" ")) && plainPadded.contains(" " + kwPlain + " ");
    }

    // Bo dau tieng Viet, chu thuong, chi giu chu / so, cach nhau 1 dau cach
    static String strip(String s) {
        if (s == null) {
            return "";
        }
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return t.replaceAll("[^a-z0-9]+", " ").trim();
    }

    // Chuan hoa NFC, bo dau cau: "ồn quá!" -> " ồn quá "
    private static String pad(String s) {
        return " " + Normalizer.normalize(s, Normalizer.Form.NFC).replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
    }

    static List<String> keywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\n]")).map(String::trim).filter(k -> !k.isEmpty()).toList();
    }

    static String render(String body, String guestName, int rating, String roomType, String keyword) {
        return body.replace("{ten_khach}", guestName == null || guestName.isBlank() ? "quý khách" : guestName)
                .replace("{so_sao}", String.valueOf(rating))
                .replace("{loai_phong}", roomType == null ? "phòng" : roomType)
                .replace("{tu_khoa}", keyword == null ? "" : keyword);
    }

    // ===== Tu dong tra loi =====

    // Goi ngay sau khi khach gui danh gia. Tra ve true neu da tu tra loi
    @Transactional
    public boolean autoReply(Review review) {
        if (!enabled || review == null || review.getReply() != null) {
            return false;
        }
        return apply(review);
    }

    private boolean apply(Review review) {
        Optional<Match> m = match(review);
        if (m.isEmpty()) {
            return false;
        }
        ReviewReplyRule rule = m.get().rule();
        review.setReply(m.get().text());
        review.setRepliedAt(LocalDateTime.now());
        review.setAutoReplyRule(rule.getName());
        reviewRepository.save(review);
        rule.setUsedCount(rule.getUsedCount() + 1);
        ruleRepository.save(rule);
        try {
            emailService.sendReviewReply(review);
        } catch (RuntimeException e) {
            log.warn("Khong gui duoc email bao tra loi danh gia #{}: {}", review.getId(), e.getMessage());
        }
        return true;
    }

    // Ap dung quy tac cho cac danh gia cu chua tra loi (khong dong vao danh gia da an). Tra ve so danh gia da tra loi
    @Transactional
    public int applyToUnreplied() {
        int n = 0;
        for (Review r : reviewRepository.findAllByOrderByCreatedAtDesc()) {
            if (!r.isReplied() && !r.isHidden() && apply(r)) {
                n++;
            }
        }
        return n;
    }

    public long countUnreplied() {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream().filter(r -> !r.isReplied() && !r.isHidden()).count();
    }

    // ===== Quan ly quy tac =====

    @Transactional
    public ReviewReplyRule create(String name, String keywords, Integer minRating, Integer maxRating, String body, Integer sortOrder) {
        ReviewReplyRule r = new ReviewReplyRule();
        fill(r, name, keywords, minRating, maxRating, body, sortOrder);
        if (r.getSortOrder() == null) {
            r.setSortOrder((int) ruleRepository.count() + 1);
        }
        return ruleRepository.save(r);
    }

    @Transactional
    public ReviewReplyRule update(Long id, String name, String keywords, Integer minRating, Integer maxRating, String body, Integer sortOrder) {
        ReviewReplyRule r = find(id);
        fill(r, name, keywords, minRating, maxRating, body, sortOrder);
        return ruleRepository.save(r);
    }

    @Transactional
    public void toggle(Long id) {
        ReviewReplyRule r = find(id);
        r.setActive(!r.isOpen());
        ruleRepository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        ruleRepository.delete(find(id));
    }

    private ReviewReplyRule find(Long id) {
        return ruleRepository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy quy tắc"));
    }

    private static void fill(ReviewReplyRule r, String name, String keywords, Integer minRating, Integer maxRating,
                             String body, Integer sortOrder) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > NAME_MAX) {
            throw new BusinessException("Tên quy tắc không được trống và tối đa " + NAME_MAX + " ký tự");
        }
        String k = String.join(", ", keywords(keywords));
        if (k.length() > KEYWORDS_MAX) {
            throw new BusinessException("Từ khóa tối đa " + KEYWORDS_MAX + " ký tự");
        }
        int min = minRating == null ? 1 : minRating;
        int max = maxRating == null ? 5 : maxRating;
        if (min < 1 || max > 5 || min > max) {
            throw new BusinessException("Khoảng số sao không hợp lệ (1 - 5, từ ≤ đến)");
        }
        String b = body == null ? "" : body.trim();
        if (b.isEmpty() || b.length() > BODY_MAX) {
            throw new BusinessException("Nội dung trả lời không được trống và tối đa " + BODY_MAX + " ký tự");
        }
        if (sortOrder != null && (sortOrder < 0 || sortOrder > 999)) {
            throw new BusinessException("Thứ tự từ 0 đến 999");
        }
        r.setName(n);
        r.setKeywords(k.isEmpty() ? null : k);
        r.setMinRating(min);
        r.setMaxRating(max);
        r.setBody(b);
        if (sortOrder != null) {
            r.setSortOrder(sortOrder);
        }
    }

    // Lan dau chay (bang trong): tao bo quy tac mac dinh cho cac than phien / khen thuong gap nhat
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        if (ruleRepository.count() > 0) {
            return;
        }
        Object[][] defaults = {
                {"Vệ sinh, mùi phòng", "bẩn, dơ, mùi, hôi, ẩm mốc, mốc, gián, côn trùng, kiến, muỗi, tóc, vệ sinh, dirty, smell, mold", 1, 3,
                        "Sóng Xanh xin lỗi {ten_khach} vì phòng chưa sạch sẽ như mong đợi. Bộ phận buồng phòng đã được nhắc kiểm tra lại "
                                + "{loai_phong} và quy trình dọn dẹp. Quản lý sẽ liên hệ để lắng nghe thêm ạ."},
                {"Tiếng ồn", "ồn, ồn ào, tiếng ồn, karaoke, nhạc to, khó ngủ, noise, noisy, loud", 1, 3,
                        "Cảm ơn {ten_khach} đã phản hồi về tiếng ồn. Resort rất tiếc vì kỳ nghỉ bị ảnh hưởng; lần tới lễ tân sẽ ưu tiên "
                                + "xếp phòng yên tĩnh xa khu sự kiện. Quản lý sẽ liên hệ để xin lỗi trực tiếp ạ."},
                {"Thái độ phục vụ", "thái độ, khó chịu, chậm, chờ lâu, đợi lâu, lễ tân, nhân viên, phục vụ, rude, slow, staff", 1, 3,
                        "Sóng Xanh thành thật xin lỗi {ten_khach} về trải nghiệm phục vụ chưa tốt. Góp ý đã được chuyển tới quản lý "
                                + "bộ phận để chấn chỉnh ngay. Rất mong có dịp đón tiếp anh/chị chu đáo hơn ạ."},
                {"Đồ ăn, bữa sáng", "đồ ăn, món ăn, buffet, bữa sáng, ăn sáng, nhà hàng, nguội, ít món, food, breakfast", 1, 3,
                        "Cảm ơn {ten_khach} đã góp ý về ẩm thực. Bếp trưởng đã ghi nhận để điều chỉnh thực đơn và chất lượng món. "
                                + "Hẹn gặp lại anh/chị với bữa sáng ngon hơn ạ."},
                {"Hỏng hóc, tiện nghi", "hỏng, hư, điều hòa, máy lạnh, nóng lạnh, nước nóng, wifi, tivi, vòi sen, broken, aircon", 1, 3,
                        "Sóng Xanh xin lỗi {ten_khach} vì sự cố tiện nghi trong phòng. Kỹ thuật đã được yêu cầu kiểm tra lại toàn bộ "
                                + "{loai_phong}. Quản lý sẽ liên hệ để hỗ trợ anh/chị ạ."},
                {"Khen view, bãi biển", "view, biển, bãi biển, hoàng hôn, bình minh, cảnh đẹp, vịnh, beach, sea, sunset", 4, 5,
                        "Cảm ơn {ten_khach} đã dành {so_sao} sao! Thật vui vì anh/chị thích khung cảnh biển ở Sóng Xanh. "
                                + "Hẹn gặp lại anh/chị vào một buổi hoàng hôn khác trên vịnh Hạ Long nhé!"},
                {"Khen nhân viên", "nhân viên, lễ tân, thân thiện, nhiệt tình, chu đáo, dễ thương, phục vụ tốt, friendly, staff, helpful", 4, 5,
                        "Cảm ơn {ten_khach} đã khen đội ngũ Sóng Xanh! Lời khen đã được gửi tới các bạn nhân viên. "
                                + "Rất mong sớm được đón tiếp anh/chị lần sau ạ."},
                {"Khen đồ ăn", "đồ ăn, ngon, buffet, bữa sáng, hải sản, nhà hàng, food, breakfast, delicious", 4, 5,
                        "Cảm ơn {ten_khach} đã khen ẩm thực của resort! Bếp Sóng Xanh rất vui khi anh/chị hài lòng. Hẹn gặp lại anh/chị nhé!"},
                {"Cảm ơn chung (4-5 sao)", "", 4, 5,
                        "Cảm ơn {ten_khach} đã đánh giá {so_sao} sao cho kỳ nghỉ tại Sóng Xanh! Rất mong được đón tiếp anh/chị lần sau."},
                {"Xin lỗi chung (1-3 sao)", "", 1, 3,
                        "Cảm ơn {ten_khach} đã chia sẻ thẳng thắn. Sóng Xanh rất tiếc vì kỳ nghỉ chưa trọn vẹn; quản lý sẽ liên hệ "
                                + "để lắng nghe và khắc phục ạ."},
        };
        List<ReviewReplyRule> rules = new ArrayList<>();
        for (int i = 0; i < defaults.length; i++) {
            Object[] d = defaults[i];
            String kw = (String) d[1];
            rules.add(ReviewReplyRule.builder().name((String) d[0]).keywords(kw.isEmpty() ? null : kw)
                    .minRating((Integer) d[2]).maxRating((Integer) d[3]).body((String) d[4])
                    .sortOrder(i + 1).active(true).usedCount(0).build());
        }
        ruleRepository.saveAll(rules);
    }
}
