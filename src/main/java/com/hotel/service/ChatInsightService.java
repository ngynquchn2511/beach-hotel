package com.hotel.service;

import com.hotel.entity.ChatQuestion;
import com.hotel.entity.ChatSession;
import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.repository.ChatQuestionRepository;
import com.hotel.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// Luu lai cau hoi khach gui tro ly "Song" + ket qua "da giai quyet chua?" de thong ke cho quan tri:
// khach hoi gi nhieu nhat, cau nao Song chua hieu, ty le giai quyet, gop y khach chua hai long
@Service
public class ChatInsightService {

    public static final int FEEDBACK_MAX = 500;
    public static final int NOTE_MAX = 300;
    private static final int ANSWER_MAX = 2000;
    private static final int QUESTION_MAX = 500;
    // Cuoc tro chuyen bo do qua 6 tiep -> cau hoi moi mo cuoc moi
    private static final long SESSION_IDLE_HOURS = 6;

    // Nhan tieng Viet cho trang quan tri (thu tu = thu tu hien trong bieu do khi bang nhau)
    public static final Map<String, String> TOPIC_LABELS = labels(
            "PRICE", "Giá phòng", "AVAILABILITY", "Phòng trống", "SUGGEST_ROOM", "Gợi ý phòng", "ROOM_TYPE", "Hỏi về 1 loại phòng",
            "DEPOSIT", "Đặt cọc", "PAYMENT", "Thanh toán", "HOW_TO_BOOK", "Cách đặt phòng", "CANCEL", "Hủy / đổi ngày / hoàn cọc",
            "PROMOTION", "Ưu đãi / mã giảm", "FACILITIES", "Tiện ích, dịch vụ", "COMBO", "Combo ăn uống", "FOOD", "Đặc sản, ăn gì",
            "TOUR", "Tour lặn biển", "TRANSFER", "Đưa đón sân bay", "EXPLORE", "Đi chơi đâu", "WEATHER", "Thời tiết",
            "DIRECTIONS", "Đường đi", "ADDRESS", "Địa chỉ", "CONTACT", "Liên hệ", "CHECK_TIME", "Giờ nhận / trả phòng",
            "GREET", "Chào hỏi", "THANKS", "Cảm ơn", "BYE", "Tạm biệt");

    public static final Map<String, String> REASON_LABELS = labels(
            "NO_ROOM", "Chưa tìm được phòng hợp ý", "PRICE", "Giá chưa phù hợp", "WRONG_ANSWER", "Sóng trả lời chưa đúng ý",
            "WANT_STAFF", "Muốn nói chuyện với nhân viên", "OTHER", "Lý do khác");

    // Chu de xa giao, khong tinh la "cau hoi that" khi xep hang
    private static final List<String> SMALL_TALK = List.of("GREET", "THANKS", "BYE");

    private final ChatSessionRepository sessionRepository;
    private final ChatQuestionRepository questionRepository;
    private StaffNotificationService notificationService;

    public ChatInsightService(ChatSessionRepository sessionRepository, ChatQuestionRepository questionRepository) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNotificationService(StaffNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static Map<String, String> labels(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    // ===== Ghi nhan tu widget =====

    // Ghi 1 cau hoi + cau tra loi. Tra ve id cuoc tro chuyen (moi neu sessionId khong hop le / cua nguoi khac / bo do lau)
    @Transactional
    public Long record(User customer, Long sessionId, String locale, String message, ChatbotService.Reply reply) {
        if (message == null || message.isBlank()) {
            return sessionId;
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = own(customer, sessionId)
                .filter(s -> s.getLastAt().isAfter(now.minusHours(SESSION_IDLE_HOURS)))
                .orElseGet(() -> sessionRepository.save(ChatSession.builder().customer(customer).locale(locale)
                        .startedAt(now).lastAt(now).build()));
        boolean answered = reply.understood();
        questionRepository.save(ChatQuestion.builder().session(session).askedAt(now)
                .text(cut(message.trim(), QUESTION_MAX))
                .topics(reply.topics().isEmpty() ? null : cut(String.join(",", reply.topics()), 200))
                .roomTypes(reply.roomTypes().isEmpty() ? null : cut(String.join(",", reply.roomTypes()), 200))
                .answered(answered).answer(cut(reply.text(), ANSWER_MAX)).build());
        session.setLastAt(now);
        session.setQuestionCount(session.getQuestionCount() + 1);
        if (!answered) {
            session.setUnansweredCount(session.getUnansweredCount() + 1);
        }
        sessionRepository.save(session);
        return session.getId();
    }

    // Widget vua hien cau "da giai quyet chua?" (de tinh ty le khach khong phan hoi)
    @Transactional
    public void markAsked(User customer, Long sessionId) {
        own(customer, sessionId).ifPresent(s -> {
            if (s.getCheckAskedAt() == null) {
                s.setCheckAskedAt(LocalDateTime.now());
                sessionRepository.save(s);
            }
        });
    }

    // Khach tra loi cau hoi kiem tra. Chua hai long -> luu ly do + gop y, bao le tan / quan tri
    @Transactional
    public ChatSession feedback(User customer, Long sessionId, boolean resolved, String reason, String text) {
        ChatSession s = own(customer, sessionId).orElseThrow(() -> new BusinessException("Không tìm thấy cuộc trò chuyện"));
        LocalDateTime now = LocalDateTime.now();
        if (s.getCheckAskedAt() == null) {
            s.setCheckAskedAt(now);
        }
        String note = text == null ? null : text.trim();
        if (note != null && note.length() > FEEDBACK_MAX) {
            throw new BusinessException("Góp ý tối đa " + FEEDBACK_MAX + " ký tự");
        }
        boolean wasUnresolved = s.isUnresolved() && s.getReason() != null;
        s.setOutcome(resolved ? ChatSession.RESOLVED : ChatSession.UNRESOLVED);
        s.setFeedbackAt(now);
        if (resolved) {
            s.setReason(null);
            s.setFeedback(null);
        } else {
            String r = reason != null && REASON_LABELS.containsKey(reason) ? reason : null;
            s.setReason(r);
            s.setFeedback(note == null || note.isEmpty() ? null : note);
            s.setHandledAt(null);
            s.setHandledBy(null);
        }
        sessionRepository.save(s);
        // Chi bao khi khach gui ly do (buoc 2), tranh bao 2 lan: bam "Chua" roi gui ly do
        if (!resolved && s.getReason() != null && !wasUnresolved && notificationService != null) {
            String who = customer != null && customer.getFullName() != null ? customer.getFullName() : "Khách";
            String msg = REASON_LABELS.get(s.getReason()) + (s.getFeedback() != null ? ": " + cut(s.getFeedback(), 120) : "");
            notificationService.notify("chat-feedback", StaffNotificationService.Level.WARNING,
                    who + " chưa hài lòng với trợ lý Sóng", msg, "/admin/chat-insights#gop-y-" + s.getId());
        }
        return s;
    }

    private Optional<ChatSession> own(User customer, Long sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return sessionRepository.findById(sessionId).filter(s -> customer == null
                ? s.getCustomer() == null
                : s.getCustomer() != null && Objects.equals(s.getCustomer().getId(), customer.getId()));
    }

    // ===== Quan tri =====

    @Transactional
    public void handle(Long sessionId, User staff, String note) {
        ChatSession s = sessionRepository.findById(sessionId).orElseThrow(() -> new BusinessException("Không tìm thấy góp ý"));
        String n = note == null ? "" : note.trim();
        if (n.length() > NOTE_MAX) {
            throw new BusinessException("Ghi chú tối đa " + NOTE_MAX + " ký tự");
        }
        s.setHandledAt(LocalDateTime.now());
        s.setHandledBy(staff);
        s.setStaffNote(n.isEmpty() ? null : n);
        sessionRepository.save(s);
    }

    @Transactional
    public void reopen(Long sessionId) {
        ChatSession s = sessionRepository.findById(sessionId).orElseThrow(() -> new BusinessException("Không tìm thấy góp ý"));
        s.setHandledAt(null);
        s.setHandledBy(null);
        sessionRepository.save(s);
    }

    public long countOpenFeedback() {
        return sessionRepository.countByOutcomeAndHandledAtIsNull(ChatSession.UNRESOLVED);
    }

    // Gop y chua hai long kem cac cau khach da hoi. status: open | handled | all
    @Transactional(readOnly = true)
    public List<FeedbackItem> feedbacks(String status) {
        List<ChatSession> list = sessionRepository.findByOutcomeOrderByFeedbackAtDesc(ChatSession.UNRESOLVED).stream()
                .filter(s -> "all".equals(status) || ("handled".equals(status) == s.isHandled()))
                .toList();
        Map<Long, List<ChatQuestion>> qs = list.isEmpty() ? Map.of()
                : questionRepository.findBySessionIdInOrderByAskedAtAsc(list.stream().map(ChatSession::getId).toList())
                .stream().collect(Collectors.groupingBy(q -> q.getSession().getId()));
        return list.stream().map(s -> new FeedbackItem(s, qs.getOrDefault(s.getId(), List.of()),
                s.getReason() == null ? "Chưa nêu lý do" : REASON_LABELS.get(s.getReason()))).toList();
    }

    public record FeedbackItem(ChatSession session, List<ChatQuestion> questions, String reasonLabel) {
    }

    public record Count(String key, String label, long count) {
    }

    public record Report(int days, LocalDate from, LocalDate to,
                         long sessions, long questions, long realQuestions, long unanswered, double unansweredPct,
                         long asked, long resolved, long unresolved, long noResponse, double resolvedPct,
                         long prevQuestions, double prevResolvedPct, long openFeedback, double avgPerSession,
                         List<Count> topics, List<Count> roomTypes, List<Count> reasons,
                         List<Count> topQuestions, List<Count> topUnanswered,
                         List<String> dayLabels, List<Long> dayQuestions, List<Long> dayUnanswered, List<Long> daySessions,
                         long[] hours, String busiestHour) {

        public long questionsDelta() {
            return questions - prevQuestions;
        }
    }

    @Transactional(readOnly = true)
    public Report report(int days, LocalDate today) {
        int d = days <= 0 ? 30 : Math.min(days, 365);
        LocalDate from = today.minusDays(d - 1L);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<ChatQuestion> qs = questionRepository.findByAskedAtGreaterThanEqualAndAskedAtLessThan(start, end);
        List<ChatSession> ss = sessionRepository.findByStartedAtGreaterThanEqualAndStartedAtLessThan(start, end);

        // Ky truoc cung do dai de so sanh
        LocalDateTime prevStart = start.minusDays(d);
        long prevQuestions = questionRepository.findByAskedAtGreaterThanEqualAndAskedAtLessThan(prevStart, start).size();
        List<ChatSession> prevSessions = sessionRepository.findByStartedAtGreaterThanEqualAndStartedAtLessThan(prevStart, start);
        long prevResolved = prevSessions.stream().filter(ChatSession::isResolved).count();
        long prevAnsweredCheck = prevSessions.stream().filter(s -> s.isResolved() || s.isUnresolved()).count();

        long unanswered = qs.stream().filter(q -> !q.isAnswered()).count();
        long real = qs.stream().filter(q -> !isSmallTalk(q)).count();
        long resolved = ss.stream().filter(ChatSession::isResolved).count();
        long unresolved = ss.stream().filter(ChatSession::isUnresolved).count();
        long noResponse = ss.stream().filter(ChatSession::isNoResponse).count();
        long asked = resolved + unresolved + noResponse;

        // Chu de: 1 cau co the nhieu chu de; bo chao hoi / cam on
        Map<String, Long> topicMap = new HashMap<>();
        Map<String, Long> roomMap = new HashMap<>();
        for (ChatQuestion q : qs) {
            for (String t : split(q.getTopics())) {
                if (!SMALL_TALK.contains(t)) {
                    topicMap.merge(t, 1L, Long::sum);
                }
            }
            for (String r : split(q.getRoomTypes())) {
                roomMap.merge(r, 1L, Long::sum);
            }
        }
        List<Count> topics = topicMap.entrySet().stream()
                .map(e -> new Count(e.getKey(), TOPIC_LABELS.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(Count::count).reversed()).toList();
        List<Count> rooms = roomMap.entrySet().stream().map(e -> new Count(e.getKey(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Count::count).reversed()).toList();

        Map<String, Long> reasonMap = new LinkedHashMap<>();
        REASON_LABELS.keySet().forEach(k -> reasonMap.put(k, 0L));
        ss.stream().filter(ChatSession::isUnresolved)
                .forEach(s -> reasonMap.merge(s.getReason() == null ? "OTHER" : s.getReason(), 1L, Long::sum));
        List<Count> reasons = reasonMap.entrySet().stream()
                .map(e -> new Count(e.getKey(), REASON_LABELS.get(e.getKey()), e.getValue())).toList();

        List<Count> topQuestions = groupQuestions(qs.stream().filter(q -> q.isAnswered() && !isSmallTalk(q)).toList(), 10);
        List<Count> topUnanswered = groupQuestions(qs.stream().filter(q -> !q.isAnswered()).toList(), 10);

        // Theo ngay
        List<String> labels = new ArrayList<>();
        List<Long> dq = new ArrayList<>();
        List<Long> du = new ArrayList<>();
        List<Long> dsn = new ArrayList<>();
        Map<LocalDate, long[]> perDay = new HashMap<>();
        for (ChatQuestion q : qs) {
            long[] a = perDay.computeIfAbsent(q.getAskedAt().toLocalDate(), k -> new long[3]);
            a[0]++;
            if (!q.isAnswered()) {
                a[1]++;
            }
        }
        for (ChatSession s : ss) {
            perDay.computeIfAbsent(s.getStartedAt().toLocalDate(), k -> new long[3])[2]++;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            long[] a = perDay.getOrDefault(day, new long[3]);
            labels.add(day.format(fmt));
            dq.add(a[0]);
            du.add(a[1]);
            dsn.add(a[2]);
        }
        long[] hours = new long[24];
        qs.forEach(q -> hours[q.getAskedAt().getHour()]++);
        int peak = 0;
        for (int h = 1; h < 24; h++) {
            if (hours[h] > hours[peak]) {
                peak = h;
            }
        }
        String busiest = qs.isEmpty() ? "–" : String.format("%02d:00 – %02d:00", peak, (peak + 1) % 24);

        return new Report(d, from, today, ss.size(), qs.size(), real, unanswered, pct(unanswered, qs.size()),
                asked, resolved, unresolved, noResponse, pct(resolved, resolved + unresolved),
                prevQuestions, pct(prevResolved, prevAnsweredCheck), countOpenFeedback(),
                ss.isEmpty() ? 0 : Math.round(ss.stream().mapToInt(ChatSession::getQuestionCount).sum() * 10.0 / ss.size()) / 10.0,
                topics, rooms, reasons, topQuestions, topUnanswered, labels, dq, du, dsn, hours, busiest);
    }

    private static boolean isSmallTalk(ChatQuestion q) {
        List<String> t = split(q.getTopics());
        return !t.isEmpty() && SMALL_TALK.containsAll(t);
    }

    // Gom cau hoi giong nhau (bo dau, bo dau cau, chu thuong), hien cau goc xuat hien dau tien
    private static List<Count> groupQuestions(List<ChatQuestion> qs, int limit) {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, String> sample = new HashMap<>();
        for (ChatQuestion q : qs) {
            String key = normalize(q.getText());
            if (key.isEmpty()) {
                continue;
            }
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            sample.putIfAbsent(key, q.getText());
        }
        return counts.entrySet().stream()
                .map(e -> new Count(e.getKey(), sample.get(e.getKey()), e.getValue()[0]))
                .sorted(Comparator.comparingLong(Count::count).reversed().thenComparing(Count::label))
                .limit(limit).toList();
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return t.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static double pct(long part, long whole) {
        return whole <= 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
