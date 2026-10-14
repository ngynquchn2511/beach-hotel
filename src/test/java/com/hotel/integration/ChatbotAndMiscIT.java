package com.hotel.integration;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatbotAndMiscIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ComboRepository comboRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;

    private User admin;

    @BeforeEach
    void seed() {
        admin = userRepository.findByEmail("admin@hotel.com").orElseGet(() -> userRepository.save(User.builder()
                .fullName("Admin Misc IT").email("admin.misc@hotel.com").phoneNumber("0900777888")
                .password(passwordEncoder.encode("x")).role(UserRole.ADMIN).build()));
    }

    @Test
    void home_anonymous_returnsOk() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    @Test
    void chatbot_askAboutCheckInTime_returnsRelevantAnswer() throws Exception {
        mockMvc.perform(post("/api/chatbot/ask").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"May gio nhan phong vay ban?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Giờ nhận phòng")));
    }

    // Khach da chon EN (cookie "lang") -> chatbot tra loi tieng Anh
    @Test
    void chatbot_englishSelected_repliesInEnglish() throws Exception {
        mockMvc.perform(post("/api/chatbot/ask").with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("lang", "en"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"What time is check-in?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Check-in: **14:00**")));
    }

    @Test
    void chatbot_emptyMessage_returnsDefaultPromptNotError() throws Exception {
        mockMvc.perform(post("/api/chatbot/ask").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").exists());
    }

    @Test
    void chatbot_noLoginRequired() throws Exception {
        // Khong .with(user(...)) - xac nhan khach chua dang nhap van goi duoc chatbot
        mockMvc.perform(post("/api/chatbot/ask").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Khach san o dau\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createCombo_asAdmin_appearsInActiveComboList() throws Exception {
        long before = comboRepository.findByActiveTrue().size();

        mockMvc.perform(post("/admin/combos/new").with(csrf())
                        .with(user(new CustomUserDetails(admin)))
                        .param("name", "Combo IT Test")
                        .param("description", "Combo tao tu integration test")
                        .param("price", "150000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/combos"));

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, comboRepository.findByActiveTrue().size());
    }

    @Test
    void createDiscountCode_duplicateCode_rejected() throws Exception {
        mockMvc.perform(post("/admin/discount-codes/new").with(csrf())
                        .with(user(new CustomUserDetails(admin)))
                        .param("code", "DUPTEST")
                        .param("discountType", "PERCENTAGE")
                        .param("discountValue", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/discount-codes"));

        long before = discountCodeRepository.findAll().size();

        mockMvc.perform(post("/admin/discount-codes/new").with(csrf())
                        .with(user(new CustomUserDetails(admin)))
                        .param("code", "duptest")
                        .param("discountType", "PERCENTAGE")
                        .param("discountValue", "15"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));

        org.junit.jupiter.api.Assertions.assertEquals(before, discountCodeRepository.findAll().size());
    }
}
