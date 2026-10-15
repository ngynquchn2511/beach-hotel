package com.hotel.integration;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Xac thuc 2 buoc cong quan tri: dung mat khau -> ma 6 so qua email -> nhap dung moi vao duoc /admin/**
@TestPropertySource(properties = "app.security.admin-otp=true")
class AdminOtpIT extends ItFixtures {

    @MockBean private EmailService emailService;

    private User staff;

    @BeforeEach
    void seed() {
        when(emailService.isConfigured()).thenReturn(true);
        staff = persistUser(UserRole.STAFF, null);
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/admin/login").session(session).with(csrf())
                        .param("username", staff.getEmail()).param("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin/otp"));
        return session;
    }

    private String lastCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendAdminOtp(eq(staff.getEmail()), anyString(), code.capture(), anyLong());
        return code.getValue();
    }

    @Test
    void passwordThenCorrectCode_opensAdmin() throws Exception {
        MockHttpSession session = login();
        assertThat(lastCode()).matches("\\d{6}");

        // Chua nhap ma -> moi trang quan tri deu bi dua ve trang nhap ma
        mockMvc.perform(get("/admin/dashboard").session(session)).andExpect(redirectedUrl("/admin/otp"));
        mockMvc.perform(get("/admin/otp").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Xác thực 2 bước")))
                .andExpect(content().string(containsString(staff.getEmail().charAt(0) + "***@test.vn")));

        String wrong = lastCode().equals("000000") ? "111111" : "000000";
        mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", wrong))
                .andExpect(redirectedUrl("/admin/otp"))
                .andExpect(flash().attribute("errorMessage", containsString("không đúng")));

        mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", lastCode()))
                .andExpect(redirectedUrl("/admin/home"));
        mockMvc.perform(get("/admin/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    void tooManyWrongCodes_forceNewLogin() throws Exception {
        MockHttpSession session = login();
        String wrong = lastCode().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", wrong))
                    .andExpect(redirectedUrl("/admin/otp"));
        }
        mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", wrong))
                .andExpect(redirectedUrl("/admin?otp=locked"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void expiredCode_rejected_resendHasCooldown() throws Exception {
        MockHttpSession session = login();
        String code = lastCode();
        session.setAttribute("ADMIN_OTP_EXPIRES", Instant.now().minusSeconds(1));
        mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", code))
                .andExpect(flash().attribute("errorMessage", containsString("hết hạn")));

        // Vua gui -> phai doi 60 giay
        mockMvc.perform(post("/admin/otp/resend").session(session).with(csrf()))
                .andExpect(flash().attribute("errorMessage", containsString("đợi 60 giây")));
        session.setAttribute("ADMIN_OTP_SENT_AT", Instant.now().minusSeconds(61));
        mockMvc.perform(post("/admin/otp/resend").session(session).with(csrf()))
                .andExpect(flash().attribute("successMessage", containsString("Đã gửi mã mới")));
        mockMvc.perform(post("/admin/otp").session(session).with(csrf()).param("code", lastCode()))
                .andExpect(redirectedUrl("/admin/home"));
    }

    @Test
    void mailNotConfigured_skipsOtp() throws Exception {
        when(emailService.isConfigured()).thenReturn(false);
        mockMvc.perform(post("/admin/login").with(csrf()).param("username", staff.getEmail()).param("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin/dashboard"));
        verify(emailService, never()).sendAdminOtp(anyString(), anyString(), anyString(), anyLong());
    }
}
