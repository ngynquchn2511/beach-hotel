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
        ChatbotService.Reply reply = chatbotService.reply(message, locale);
        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply.text());
        out.put("actions", reply.actions());
        out.put("suggestions", reply.suggestions());
        out.put("cards", reply.cards());
        if (insightService != null) {
            // Luu thong ke loi thi van tra loi khach binh thuong
            try {
                Long sid = insightService.record(user(principal), toLong(body.get("sessionId")), locale.getLanguage(), message, reply);
                if (sid != null) {
                    out.put("sessionId", sid);
                }
            } catch (RuntimeException ignored) {
                // bo qua
            }
        }
        return out;
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
