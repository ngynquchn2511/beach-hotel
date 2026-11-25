package com.hotel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entity.ChatSession;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.ChatQuestionRepository;
import com.hotel.repository.ChatSessionRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ChatInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Thong ke tro ly "Song": luu cau hoi + chu de, hoi lai "da giai quyet chua?", gop y vao trang quan tri
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatInsightIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private ChatQuestionRepository questionRepository;
    @Autowired private ChatInsightService insightService;
    private final ObjectMapper json = new ObjectMapper();

    private User customer;
    private User other;
    private User admin;

    @BeforeEach
    void seed() {
        customer = userRepository.save(User.builder().fullName("Khach Hoi Song").email("khach.hoisong@it.com")
                .phoneNumber("0900111222").password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).build());
        other = userRepository.save(User.builder().fullName("Khach Khac").email("khach.khac.song@it.com")
                .phoneNumber("0900111333").password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).build());
        admin = userRepository.save(User.builder().fullName("Quan Tri Song").email("quantri.song@it.com")
                .phoneNumber("0900111444").password(passwordEncoder.encode("x")).role(UserRole.ADMIN).build());
    }

    private JsonNode ask(User u, String message, Long sessionId) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("message", message, "sessionId", sessionId == null ? "" : sessionId));
        String res = mockMvc.perform(post("/api/chatbot/ask").with(csrf()).with(user(new CustomUserDetails(u)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(res);
    }

    private void feedback(User u, Long sid, boolean resolved, String reason, String text) throws Exception {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("sessionId", sid);
        m.put("resolved", resolved);
        m.put("reason", reason);
        m.put("text", text);
        mockMvc.perform(post("/api/chatbot/feedback").with(csrf()).with(user(new CustomUserDetails(u)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(m)))
                .andExpect(status().isOk());
    }

    @Test
    void ask_recordsQuestionsWithTopics_inOneSession() throws Exception {
        JsonNode first = ask(customer, "Giá phòng bao nhiêu 1 đêm?", null);
        long sid = first.get("sessionId").asLong();
        assertThat(first.get("reply").asText()).isNotBlank();
        JsonNode second = ask(customer, "xyz qwerty abc", sid);
        assertThat(second.get("sessionId").asLong()).isEqualTo(sid);

        ChatSession s = sessionRepository.findById(sid).orElseThrow();
        assertThat(s.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(s.getQuestionCount()).isEqualTo(2);
        assertThat(s.getUnansweredCount()).isEqualTo(1);
        var qs = questionRepository.findBySessionIdOrderByAskedAtAsc(sid);
        assertThat(qs).hasSize(2);
        assertThat(qs.get(0).getTopics()).contains("PRICE");
        assertThat(qs.get(0).isAnswered()).isTrue();
        assertThat(qs.get(1).isAnswered()).isFalse();
        assertThat(qs.get(1).getTopics()).isNull();
    }

    // Khach khac gui sessionId khong phai cua minh -> mo cuoc tro chuyen moi, khong ghi vao cuoc cua nguoi khac
    @Test
    void ask_withSomeoneElsesSession_startsNewSession() throws Exception {
        long sid = ask(customer, "còn phòng trống không", null).get("sessionId").asLong();
        long otherSid = ask(other, "giá phòng", sid).get("sessionId").asLong();
        assertThat(otherSid).isNotEqualTo(sid);
        assertThat(sessionRepository.findById(sid).orElseThrow().getQuestionCount()).isEqualTo(1);
    }

    @Test
    void feedback_resolved_and_unresolvedNotifiesFrontDesk() throws Exception {
        long sid = ask(customer, "giá phòng", null).get("sessionId").asLong();
        mockMvc.perform(post("/api/chatbot/check").with(csrf()).with(user(new CustomUserDetails(customer)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\": " + sid + "}")).andExpect(status().isOk());
        assertThat(sessionRepository.findById(sid).orElseThrow().getCheckAskedAt()).isNotNull();

        feedback(customer, sid, true, null, null);
        assertThat(sessionRepository.findById(sid).orElseThrow().isResolved()).isTrue();

        // Buoc 1: bam "Chua" (chua co ly do) -> chua bao; buoc 2: gui ly do -> bao le tan 1 lan
        feedback(customer, sid, false, null, null);
        feedback(customer, sid, false, "NO_ROOM", "Cần phòng 4 người ngày 20/12 dưới 2 triệu");
        ChatSession s = sessionRepository.findById(sid).orElseThrow();
        assertThat(s.isUnresolved()).isTrue();
        assertThat(s.getReason()).isEqualTo("NO_ROOM");
        assertThat(s.getFeedback()).contains("4 người");
        assertThat(insightService.countOpenFeedback()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void feedback_onOthersSession_isRejected() throws Exception {
        long sid = ask(customer, "giá phòng", null).get("sessionId").asLong();
        mockMvc.perform(post("/api/chatbot/feedback").with(csrf()).with(user(new CustomUserDetails(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": " + sid + ", \"resolved\": false, \"reason\": \"PRICE\"}"))
                .andExpect(status().isBadRequest());
        assertThat(sessionRepository.findById(sid).orElseThrow().getOutcome()).isEqualTo(ChatSession.OPEN);
    }

    @Test
    void report_countsTopicsOutcomesAndUnanswered() throws Exception {
        long a = ask(customer, "Giá phòng bao nhiêu", null).get("sessionId").asLong();
        ask(customer, "giá phòng bao nhiêu?", a);
        ask(customer, "con meo nha ban ten gi", a);
        feedback(customer, a, true, null, null);
        long b = ask(other, "Có cần đặt cọc không", null).get("sessionId").asLong();
        feedback(other, b, false, "PRICE", "Đắt quá");

        ChatInsightService.Report r = insightService.report(7, LocalDate.now());
        assertThat(r.questions()).isGreaterThanOrEqualTo(4);
        assertThat(r.unanswered()).isGreaterThanOrEqualTo(1);
        assertThat(r.topics()).anyMatch(c -> c.key().equals("PRICE") && c.count() >= 2);
        assertThat(r.topQuestions()).anyMatch(c -> c.count() >= 2 && c.label().toLowerCase().contains("giá phòng"));
        assertThat(r.topUnanswered()).anyMatch(c -> c.label().contains("con meo"));
        assertThat(r.resolved()).isGreaterThanOrEqualTo(1);
        assertThat(r.unresolved()).isGreaterThanOrEqualTo(1);
        assertThat(r.reasons()).anyMatch(c -> c.key().equals("PRICE") && c.count() >= 1);
        assertThat(r.dayLabels()).hasSize(7);
        long sum = 0;
        for (long h : r.hours()) {
            sum += h;
        }
        assertThat(sum).isEqualTo(r.questions());
    }

    @Test
    void adminPage_showsChartsAndFeedback_thenHandle() throws Exception {
        long sid = ask(customer, "giá phòng", null).get("sessionId").asLong();
        feedback(customer, sid, false, "WANT_STAFF", "Gọi lại cho tôi nhé");

        mockMvc.perform(get("/admin/chat-insights").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ciTopics")))
                .andExpect(content().string(containsString("Gọi lại cho tôi nhé")))
                .andExpect(content().string(containsString("Muốn nói chuyện với nhân viên")))
                .andExpect(content().string(containsString("Khach Hoi Song")));

        mockMvc.perform(post("/admin/chat-insights/" + sid + "/handle").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("note", "Đã gọi điện tư vấn"))
                .andExpect(status().is3xxRedirection());
        ChatSession s = sessionRepository.findById(sid).orElseThrow();
        assertThat(s.isHandled()).isTrue();
        assertThat(s.getStaffNote()).isEqualTo("Đã gọi điện tư vấn");

        mockMvc.perform(get("/admin/chat-insights").param("status", "handled").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã gọi điện tư vấn")));
    }

    @Test
    void adminPage_forbiddenForCustomerAndHousekeeper() throws Exception {
        User hk = userRepository.save(User.builder().fullName("Buong Phong Song").email("bp.song@it.com")
                .phoneNumber("0900111555").password(passwordEncoder.encode("x")).role(UserRole.HOUSEKEEPER).build());
        mockMvc.perform(get("/admin/chat-insights").with(user(new CustomUserDetails(hk))))
                .andExpect(status().isForbidden());
    }

    @Test
    void widget_containsThirtySecondCheck() throws Exception {
        mockMvc.perform(get("/").with(user(new CustomUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IDLE_MS = 30000")))
                .andExpect(content().string(containsString("/api/chatbot/feedback")));
    }
}
