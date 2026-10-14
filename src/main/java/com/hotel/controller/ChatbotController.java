package com.hotel.controller;

import com.hotel.service.ChatbotService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// API cho widget chat tu van "May" o giao dien khach hang - khong can dang nhap
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    // Gioi han do dai cau hoi de tranh gui noi dung qua dai
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    // Tra ve: reply (noi dung), actions (nut lien ket [{label, url}]), suggestions (cau hoi goi y tiep theo)
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        // Tra loi theo ngon ngu khach dang chon (nut VI/EN, luu trong cookie "lang")
        ChatbotService.Reply reply = chatbotService.reply(message, LocaleContextHolder.getLocale());
        return Map.of(
                "reply", reply.text(),
                "actions", reply.actions(),
                "suggestions", reply.suggestions());
    }
}
