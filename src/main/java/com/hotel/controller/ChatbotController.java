package com.hotel.controller;

import com.hotel.entity.User;
import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.ChatInsightService;
import com.hotel.service.ChatbotService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// API cho widget chat tu van "Song" o giao dien khach hang - bat buoc dang nhap (SecurityConfig)
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    // Gioi han do dai cau hoi de tranh gui noi dung qua dai
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatbotService chatbotService;
    private ChatInsightService insightService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setInsightService(ChatInsightService insightService) {
        this.insightService = insightService;
    }

    // Chong spam + AI tra loi cau Song chua hieu (co tran chi phi)
    private com.hotel.service.ChatGuardService guard;
    private com.hotel.service.ChatAiService aiService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGuard(com.hotel.service.ChatGuardService guard) {
        this.guard = guard;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAiService(com.hotel.service.ChatAiService aiService) {
        this.aiService = aiService;
    }

    // Tra ve: reply (noi dung), actions (nut lien ket [{label, url}]), suggestions (cau hoi goi y tiep theo),
    // sessionId (cuoc tro chuyen - widget gui lai o cau sau va khi tra loi "da giai quyet chua?")
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        String message = body.get("message") == null ? null : String.valueOf(body.get("message"));
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        // Tra loi theo ngon ngu khach dang chon (nut VI/EN, luu trong cookie "lang")
        var locale = LocaleContextHolder.getLocale();
        User u = user(principal);
        Long sessionId = toLong(body.get("sessionId"));
        Map<String, Object> out = new HashMap<>();

        // 1. Chong spam: nhan qua nhanh / gui lai y het -> tra loi ngan, khong xu ly, khong goi AI
        if (guard != null && message != null && !message.isBlank()) {
            var st = aiService != null ? aiService.settings() : com.hotel.entity.ChatAiSettings.defaults();
            var verdict = guard.check(u == null ? null : u.getId(), message, st.getPerMinute(), st.getPerHour());
            if (verdict != com.hotel.service.ChatGuardService.Verdict.OK) {
                String code = verdict == com.hotel.service.ChatGuardService.Verdict.RATE_LIMIT ? "chat.limit.rate" : "chat.limit.duplicate";
                ChatbotService.Reply blocked = new ChatbotService.Reply(com.hotel.util.Texts.get(locale, code), List.of(), List.of());
                out.put("reply", blocked.text());
                out.put("actions", List.of());
                out.put("suggestions", List.of());
                out.put("cards", List.of());
                out.put("limited", true);
                save(out, u, sessionId, locale, message, blocked, new ChatInsightService.AiMeta(verdict.name(), 0, 0, 0));
                return out;
            }
        }

        // 2. Song tra loi theo luat (gia, phong trong, tim phong... bang so lieu that)
        ChatbotService.Reply reply = chatbotService.reply(message, locale);
        ChatInsightService.AiMeta meta = null;

        // 3. Song chua hieu -> AI (Claude) tra loi ngan 1 - 3 dong neu con trong tran chi phi / luot cua khach
        if (aiService != null && !reply.understood() && message != null && !message.isBlank()) {
            List<com.hotel.entity.ChatQuestion> history = insightService != null ? insightService.history(u, sessionId) : List.of();
            var o = aiService.answer(u, message, locale, history);
            if (o.status() != com.hotel.service.ChatAiService.Status.OFF && o.status() != com.hotel.service.ChatAiService.Status.NOT_ALLOWED) {
                meta = new ChatInsightService.AiMeta(o.status().name(), o.inputTokens(), o.outputTokens(), o.costMicro());
            }
            if (o.answered()) {
                reply = new ChatbotService.Reply(o.text(), reply.actions(), reply.suggestions(), List.of(), List.of(), List.of());
                out.put("ai", true);
            }
        }
        out.put("reply", reply.text());
        out.put("actions", reply.actions());
        out.put("suggestions", reply.suggestions());
        out.put("cards", reply.cards());
        save(out, u, sessionId, locale, message, reply, meta);
        return out;
    }

    // Luu thong ke; loi thi van tra loi khach binh thuong
    private void save(Map<String, Object> out, User u, Long sessionId, java.util.Locale locale, String message,
                      ChatbotService.Reply reply, ChatInsightService.AiMeta meta) {
        if (insightService == null) {
            return;
        }
        try {
            Long sid = insightService.record(u, sessionId, locale.getLanguage(), message, reply, meta);
            if (sid != null) {
                out.put("sessionId", sid);
            }
        } catch (RuntimeException ignored) {
            // bo qua
        }
    }

    // Widget vua hoi "da giai quyet chua?"
    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody Map<String, Object> body, @AuthenticationPrincipal CustomUserDetails principal) {
        if (insightService != null) {
            insightService.markAsked(user(principal), toLong(body.get("sessionId")));
        }
        return Map.of("ok", true);
    }

    // Khach tra loi: resolved=true (da xong) / false + reason (NO_ROOM, PRICE, WRONG_ANSWER, WANT_STAFF, OTHER) + text
    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        if (insightService == null) {
            return ResponseEntity.ok(Map.of("ok", false));
        }
        try {
            insightService.feedback(user(principal), toLong(body.get("sessionId")), Boolean.TRUE.equals(body.get("resolved")),
                    body.get("reason") == null ? null : String.valueOf(body.get("reason")),
                    body.get("text") == null ? null : String.valueOf(body.get("text")));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private static User user(CustomUserDetails principal) {
        return principal == null ? null : principal.getUser();
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
