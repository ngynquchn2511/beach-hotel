package com.hotel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entity.ChatQuestion;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.ChatQuestionRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ChatAiService;
import com.hotel.service.ChatGuardService;
import com.hotel.service.ai.AiAnalysis;
import com.hotel.service.ai.AiGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// AI tra loi cau Song chua hieu: ngan 1 - 3 dong, chi khach da xac minh, het luot / het tran thi dung Song theo luat,
// chong spam (nhan qua nhanh, gui lai y het, cau vo nghia), quan tri xem chi phi + doi cai dat. Gateway gia, khong goi API that
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatAiIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ChatAiService aiService;
    @Autowired private ChatQuestionRepository questionRepository;
    private final ObjectMapper json = new ObjectMapper();

    private AiGateway original;
    private FakeGateway fake;
    private User customer;

    static class FakeGateway implements AiGateway {
        int calls;
        String lastSystem;
        String lastUser;
        String reply = "Có nha, resort nhận thú cưng nhỏ dưới 5kg.\nPhụ thu 200.000đ/đêm.\nBạn báo lễ tân khi đặt phòng nhé.\nDòng thứ tư thừa.\nDòng thứ năm thừa.";

        public boolean isEnabled() { return true; }
        public String model() { return "claude-haiku-5-5"; }
        public Result<AiAnalysis> analyze(String s, String u) { throw new UnsupportedOperationException(); }
        public Result<String> answer(String s, String u) {
            calls++;
            lastSystem = s;
            lastUser = u;
            return new Result<>(reply, 3000, 120);
        }
    }

    @BeforeEach
    void setUp() {
        original = aiService.gatewayForTest();
        fake = new FakeGateway();
        aiService.setGatewayForTest(fake);
        customer = customer("ai.khach@it.com", true);
    }

    @AfterEach
    void restore() {
        aiService.setGatewayForTest(original);
    }

    private User customer(String email, boolean verified) {
        return userRepository.save(User.builder().fullName("Khach AI " + email).email(email).phoneNumber("09" + Math.abs(email.hashCode() % 100000000))
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).emailVerified(verified).build());
    }

    private JsonNode ask(User u, String message) throws Exception {
        String res = mockMvc.perform(post("/api/chatbot/ask").with(csrf()).with(user(new CustomUserDetails(u)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("message", message))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res);
    }

    @Test
    void unknownQuestion_answeredByAi_shortAndRecordedWithCost() throws Exception {
        JsonNode r = ask(customer, "Resort có cho mang chó mèo theo không?");
        assertThat(r.path("ai").asBoolean()).isTrue();
        String text = r.get("reply").asText();
        assertThat(text.split("\n")).hasSize(ChatAiService.MAX_LINES);
        assertThat(text).startsWith("Có nha").doesNotContain("Dòng thứ tư");
        // AI nhan du lieu that cua resort va cau hoi nam trong the rieng (chong chen lenh)
        assertThat(fake.lastSystem).contains("<du_lieu_resort>").contains("Tối đa 3 dòng");
        assertThat(fake.lastUser).contains("<cau_hoi_cua_khach>").contains("chó mèo");
        ChatQuestion q = questionRepository.findBySessionIdOrderByAskedAtAsc(r.get("sessionId").asLong()).get(0);
        assertThat(q.getAiStatus()).isEqualTo("AI");
        assertThat(q.getAiCostMicro()).isEqualTo(360L);
    }

    @Test
    void ruleBasedQuestions_doNotCallAi() throws Exception {
        ask(customer, "Giá phòng bao nhiêu");
        ask(customer, "Có cần đặt cọc không");
        ask(customer, "tôi muốn phòng view biển");
        assertThat(fake.calls).isZero();
    }

    @Test
    void perUserDailyLimit_andBudget_fallBackToSong() throws Exception {
        var s = aiService.settings();
        aiService.update(true, s.getMonthlyBudgetUsd(), s.getDailyBudgetUsd(), 1, 30, 300, "test");
        assertThat(ask(customer, "Resort có cho mang chó mèo theo không?").path("ai").asBoolean()).isTrue();
        JsonNode second = ask(customer, "Lễ tân có cho mượn bàn là quần áo không bạn");
        assertThat(second.path("ai").asBoolean()).isFalse();
        assertThat(fake.calls).isEqualTo(1);
        assertThat(questionRepository.findAll()).anyMatch(q -> "LIMIT_USER".equals(q.getAiStatus()));

        User other = customer("ai.khach2@it.com", true);
        aiService.update(true, BigDecimal.ZERO, BigDecimal.ZERO, 10, 30, 300, "test");
        assertThat(ask(other, "Resort có cho mang chó mèo theo không?").path("ai").asBoolean()).isFalse();
        assertThat(fake.calls).isEqualTo(1);
        assertThat(questionRepository.findAll()).anyMatch(q -> "LIMIT_BUDGET".equals(q.getAiStatus()));
    }

    @Test
    void unverifiedOrDisabled_noAi() throws Exception {
        User unverified = customer("ai.chuaxacminh@it.com", false);
        assertThat(ask(unverified, "Resort có cho mang chó mèo theo không?").path("ai").asBoolean()).isFalse();
        var s = aiService.settings();
        aiService.update(false, s.getMonthlyBudgetUsd(), s.getDailyBudgetUsd(), 10, 30, 300, "test");
        assertThat(ask(customer, "Resort có cho mang chó mèo theo không?").path("ai").asBoolean()).isFalse();
        assertThat(fake.calls).isZero();
    }

    @Test
    void spam_rateLimitDuplicateAndGibberish() throws Exception {
        var s = aiService.settings();
        aiService.update(true, s.getMonthlyBudgetUsd(), s.getDailyBudgetUsd(), 10, 3, 300, "test");
        User u = customer("ai.spam@it.com", true);
        ask(u, "xin chào");
        JsonNode dup = ask(u, "Xin chào!");
        assertThat(dup.path("limited").asBoolean()).isTrue();
        assertThat(dup.get("reply").asText()).contains("vừa trả lời");
        ask(u, "giá phòng");
        ask(u, "đặt cọc");
        JsonNode fast = ask(u, "còn phòng không");
        assertThat(fast.path("limited").asBoolean()).isTrue();
        assertThat(fast.get("reply").asText()).contains("nhắn hơi nhanh");

        User g = customer("ai.vonghia@it.com", true);
        ask(g, "aaaaaaaaaaaa");
        ask(g, "??? !!!");
        assertThat(fake.calls).isZero();
        assertThat(ChatGuardService.worthAsking("Resort có cho mang chó không")).isTrue();
        assertThat(ChatGuardService.worthAsking("hahahahahahahaha")).isFalse();
    }

    @Test
    void adminSeesAiCard_onlyAdminChangesSettings() throws Exception {
        ask(customer, "Resort có cho mang chó mèo theo không?");
        User admin = userRepository.save(User.builder().fullName("Admin AI").email("admin.ai@it.com").phoneNumber("0911222333")
                .password(passwordEncoder.encode("x")).role(UserRole.ADMIN).build());
        User staff = userRepository.save(User.builder().fullName("Le tan AI").email("letan.ai@it.com").phoneNumber("0911222444")
                .password(passwordEncoder.encode("x")).role(UserRole.STAFF).build());
        mockMvc.perform(get("/admin/chat-insights").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI trả lời câu Sóng chưa hiểu")))
                .andExpect(content().string(containsString("ciAi")))
                .andExpect(content().string(containsString("Lưu cài đặt")));
        mockMvc.perform(post("/admin/chat-insights/ai-settings").with(csrf()).with(user(new CustomUserDetails(staff)))
                        .param("enabled", "false").param("monthlyBudgetUsd", "1").param("dailyBudgetUsd", "0.1")
                        .param("perUserDaily", "5").param("perMinute", "5").param("perHour", "50"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/chat-insights/ai-settings").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("enabled", "true").param("monthlyBudgetUsd", "2.5").param("dailyBudgetUsd", "0.2")
                        .param("perUserDaily", "5").param("perMinute", "5").param("perHour", "50"))
                .andExpect(status().is3xxRedirection());
        assertThat(aiService.settings().getMonthlyBudgetUsd()).isEqualByComparingTo("2.50");
        assertThat(aiService.settings().getPerUserDaily()).isEqualTo(5);
    }

    @Test
    void tidy_keepsThreeLines_andCutsLongText() {
        assertThat(List.of(ChatAiService.tidy("# Tiêu đề\n\n- ý 1\n- ý 2\n- ý 3\n- ý 4").split("\n")))
                .containsExactly("Tiêu đề", "• ý 1", "• ý 2");
        assertThat(ChatAiService.tidy("a. ".repeat(300)).length()).isLessThanOrEqualTo(ChatAiService.MAX_CHARS + 1);
    }
}
