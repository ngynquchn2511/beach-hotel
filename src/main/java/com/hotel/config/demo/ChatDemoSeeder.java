package com.hotel.config.demo;

import com.hotel.entity.ChatQuestion;
import com.hotel.entity.ChatSession;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.ChatQuestionRepository;
import com.hotel.repository.ChatSessionRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.ChatbotService;
import com.hotel.util.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Cuoc tro chuyen demo voi tro ly "Song" 60 ngay gan day de trang thong ke co so lieu.
// Cau hoi chay qua ChatbotService that nen chu de / cau Song chua hieu dung nhu khi khach hoi.
// Chi chay khi bang chat_sessions con trong
@Component
@Order(30)
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class ChatDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatDemoSeeder.class);

    // {cau hoi, trong so}: cau hay gap co trong so cao
    private static final Object[][] QUESTIONS = {
            {"Giá phòng bao nhiêu 1 đêm?", 14}, {"giá phòng cuối tuần thế nào", 6}, {"Phòng gia đình giá bao nhiêu", 5},
            {"Còn phòng trống cuối tuần này không?", 12}, {"còn phòng cho 4 người không", 6}, {"ngày nào còn phòng trống", 4},
            {"Có cần đặt cọc không?", 8}, {"đặt cọc bao nhiêu %", 4}, {"Hủy phòng có được hoàn cọc không", 6},
            {"đổi ngày được không", 3}, {"Cách đặt phòng thế nào", 5}, {"thanh toán bằng chuyển khoản được không", 4},
            {"Có mã giảm giá nào không?", 7}, {"ưu đãi cho khách mới", 3}, {"Giờ nhận phòng mấy giờ", 6}, {"mấy giờ trả phòng", 3},
            {"Resort có hồ bơi không", 5}, {"có bãi đỗ xe ô tô không", 3}, {"wifi có mạnh không", 2},
            {"Có combo ăn uống nào", 4}, {"buffet sáng có không", 3}, {"Tour lặn biển giá bao nhiêu", 5}, {"đi kayak ở đâu", 2},
            {"Có xe đón sân bay Nội Bài không", 5}, {"xe đón Cát Bi bao nhiêu tiền", 2}, {"Từ Hà Nội đi Hạ Long thế nào", 4},
            {"resort ở đâu", 3}, {"Hạ Long có gì chơi", 4}, {"đặc sản Hạ Long ăn gì", 3}, {"Thời tiết tuần này thế nào", 5},
            {"tháng 12 tắm biển được không", 2}, {"gợi ý phòng cho cặp đôi", 3}, {"số điện thoại resort", 3},
            {"Có cho mang thú cưng không", 3}, {"có phòng hút thuốc không", 2}, {"trẻ em dưới 6 tuổi có tính tiền không", 3},
            {"có máy sấy tóc trong phòng không", 1}, {"cho thuê xe máy không", 2}, {"có tổ chức sinh nhật được không", 2},
            {"xuất hóa đơn đỏ được không", 2}, {"check in sớm được không", 2}, {"Xin chào", 6}, {"cảm ơn bạn", 4}};

    private static final String[][] FEEDBACK = {
            {"NO_ROOM", "Cần 2 phòng liền nhau cho gia đình 6 người dịp Tết"}, {"NO_ROOM", "Muốn phòng view biển mà hết rồi"},
            {"PRICE", "Giá cuối tuần cao quá, có giảm cho ở 3 đêm không?"}, {"PRICE", "Mình đi nhóm sinh viên, ngân sách 500k/người"},
            {"WRONG_ANSWER", "Hỏi thú cưng mà Sóng không trả lời"}, {"WRONG_ANSWER", "Mình hỏi trẻ em có tính tiền không"},
            {"WANT_STAFF", "Gọi lại giúp mình để tư vấn tổ chức sinh nhật"}, {"WANT_STAFF", null}, {"OTHER", "Cần xuất hóa đơn công ty"},
            {"OTHER", null}};

    private final ChatbotService chatbot;
    private final ChatSessionRepository sessions;
    private final ChatQuestionRepository questions;
    private final UserRepository users;

    public ChatDemoSeeder(ChatbotService chatbot, ChatSessionRepository sessions, ChatQuestionRepository questions,
                          UserRepository users) {
        this.chatbot = chatbot;
        this.sessions = sessions;
        this.questions = questions;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (sessions.count() > 0) {
            return;
        }
        List<User> customers = users.findAll().stream().filter(u -> u.getRole() == UserRole.CUSTOMER).limit(600).toList();
        User staff = users.findAll().stream().filter(u -> u.getRole() == UserRole.STAFF).findFirst().orElse(null);
        if (customers.isEmpty()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        Random rnd = new Random(20261126L);
        int totalWeight = 0;
        for (Object[] q : QUESTIONS) {
            totalWeight += (Integer) q[1];
        }
        // Gio khach hay hoi: toi 19-22h dong nhat, sang 8-11h
        int[] hourWeight = {1, 1, 0, 0, 0, 1, 2, 4, 6, 7, 7, 6, 5, 5, 5, 5, 5, 6, 7, 9, 11, 12, 9, 4};
        int hourTotal = 0;
        for (int w : hourWeight) {
            hourTotal += w;
        }
        Map<String, ChatbotService.Reply> cache = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (int back = 59; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            // Dong dan ve gan day + cuoi tuan nhieu hon
            int base = 6 + (59 - back) / 6 + (day.getDayOfWeek().getValue() >= 5 ? 4 : 0);
            int n = Math.max(1, base + rnd.nextInt(5) - 2);
            for (int i = 0; i < n; i++) {
                int h = pick(rnd, hourWeight, hourTotal);
                LocalDateTime start = day.atTime(h, rnd.nextInt(60), rnd.nextInt(60));
                if (start.isAfter(now)) {
                    continue;
                }
                User c = customers.get(rnd.nextInt(customers.size()));
                ChatSession s = sessions.save(ChatSession.builder().customer(c).locale(rnd.nextInt(10) == 0 ? "en" : "vi")
                        .startedAt(start).lastAt(start).build());
                int qn = 1 + rnd.nextInt(4);
                LocalDateTime t = start;
                List<ChatQuestion> list = new ArrayList<>();
                for (int k = 0; k < qn; k++) {
                    String text = (String) QUESTIONS[pickQuestion(rnd, totalWeight)][0];
                    ChatbotService.Reply reply = cache.computeIfAbsent(text, x -> chatbot.reply(x, Texts.VIETNAMESE));
                    list.add(ChatQuestion.builder().session(s).askedAt(t).text(text)
                            .topics(reply.topics().isEmpty() ? null : String.join(",", reply.topics()))
                            .roomTypes(reply.roomTypes().isEmpty() ? null : String.join(",", reply.roomTypes()))
                            .answered(reply.understood()).answer(reply.text().length() > 2000 ? reply.text().substring(0, 2000) : reply.text())
                            .build());
                    s.setQuestionCount(s.getQuestionCount() + 1);
                    if (!reply.understood()) {
                        s.setUnansweredCount(s.getUnansweredCount() + 1);
                    }
                    s.setLastAt(t);
                    t = t.plusSeconds(20 + rnd.nextInt(90));
                }
                questions.saveAll(list);
                count += list.size();
                // Sau 30 giay: 80% duoc hoi lai; khach chua hieu nhieu thi de chua hai long hon
                if (rnd.nextInt(100) < 80) {
                    s.setCheckAskedAt(s.getLastAt().plusSeconds(30));
                    int roll = rnd.nextInt(100) - s.getUnansweredCount() * 25;
                    if (roll >= 30) {
                        s.setOutcome(ChatSession.RESOLVED);
                        s.setFeedbackAt(s.getCheckAskedAt().plusSeconds(5 + rnd.nextInt(20)));
                    } else if (roll >= 10 || s.getUnansweredCount() > 0) {
                        String[] f = FEEDBACK[rnd.nextInt(FEEDBACK.length)];
                        s.setOutcome(ChatSession.UNRESOLVED);
                        s.setReason(f[0]);
                        s.setFeedback(f[1]);
                        s.setFeedbackAt(s.getCheckAskedAt().plusSeconds(10 + rnd.nextInt(60)));
                        // Gop y cu da duoc le tan xu ly, 3 ngay gan day con cho
                        if (back > 3 || rnd.nextBoolean()) {
                            s.setHandledAt(s.getFeedbackAt().plusHours(1 + rnd.nextInt(5)));
                            s.setHandledBy(staff);
                            s.setStaffNote(rnd.nextBoolean() ? "Đã gọi điện tư vấn" : "Đã nhắn Zalo gửi phòng phù hợp");
                        }
                    }
                }
                sessions.save(s);
            }
        }
        log.info("Du lieu demo tro ly Song: {} cau hoi ({} ms)", count, System.currentTimeMillis() - t0);
    }

    private static int pick(Random rnd, int[] weights, int total) {
        int r = rnd.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static int pickQuestion(Random rnd, int total) {
        int r = rnd.nextInt(total);
        for (int i = 0; i < QUESTIONS.length; i++) {
            r -= (Integer) QUESTIONS[i][1];
            if (r < 0) {
                return i;
            }
        }
        return 0;
    }
}
