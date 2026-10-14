package com.hotel.integration;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Xac thuc email: dang ky moi phai bam link kich hoat; email cua tai khoan tu tao khi dat phong
// chi nhan link dat mat khau gui toi chinh email do (form dang ky khong ghi de mat khau)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Khach Kich Hoat").param("email", email).param("phoneNumber", "0934111222")
                .param("password", password).param("confirmPassword", password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("username", email).param("password", password));
    }

    @Test
    void newRegistration_mustClickLink_beforeLogin_resendIssuesNewLink() throws Exception {
        register("kichhoat@mail.com", "MatKhau123").andExpect(redirectedUrl("/login?registered=true"));
        User created = userRepository.findByEmail("kichhoat@mail.com").orElseThrow();
        assertThat(created.isEmailVerified()).isFalse();
        String firstToken = created.getEmailVerifyToken();
        assertThat(firstToken).isNotBlank();

        login("kichhoat@mail.com", "MatKhau123").andExpect(redirectedUrl("/login?unverified=true"));
        mockMvc.perform(get("/login").param("unverified", "true"))
                .andExpect(content().string(containsString("chưa được kích hoạt")))
                .andExpect(content().string(containsString("/resend-verification")));

        // Gui lai -> ma moi, ma cu het dung
        mockMvc.perform(post("/resend-verification").with(csrf()).param("email", "kichhoat@mail.com"))
                .andExpect(redirectedUrl("/login?resent=true"));
        String newToken = userRepository.findByEmail("kichhoat@mail.com").orElseThrow().getEmailVerifyToken();
        assertThat(newToken).isNotEqualTo(firstToken);
        mockMvc.perform(get("/verify-email").param("token", firstToken)).andExpect(redirectedUrl("/login?verifyInvalid=true"));

        mockMvc.perform(get("/verify-email").param("token", newToken)).andExpect(redirectedUrl("/login?verified=true"));
        User verified = userRepository.findByEmail("kichhoat@mail.com").orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getEmailVerifyToken()).isNull();
        login("kichhoat@mail.com", "MatKhau123").andExpect(redirectedUrl("/customer/rooms"));
    }

    @Test
    void expiredOrMissingToken_rejected() throws Exception {
        register("hethan@mail.com", "MatKhau123");
        User user = userRepository.findByEmail("hethan@mail.com").orElseThrow();
        user.setEmailVerifyExpiry(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        mockMvc.perform(get("/verify-email").param("token", user.getEmailVerifyToken())).andExpect(redirectedUrl("/login?verifyInvalid=true"));
        mockMvc.perform(get("/verify-email")).andExpect(redirectedUrl("/login?verifyInvalid=true"));
        assertThat(userRepository.findByEmail("hethan@mail.com").orElseThrow().isEmailVerified()).isFalse();
    }

    // Tai khoan tu tao khi khach dat phong khong dang nhap: ai do dang ky bang email nay KHONG dat duoc mat khau,
    // chi chu email nhan link dat mat khau
    @Test
    void registeringGuestAccountEmail_sendsSetupLink_doesNotTakePassword() throws Exception {
        String originalHash = passwordEncoder.encode("ngau-nhien");
        userRepository.save(User.builder().fullName("Khach Vang Lai").email("vanglai@mail.com").phoneNumber("0934999888")
                .password(originalHash).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).emailVerified(false).build());

        register("vanglai@mail.com", "KeLaChiem1").andExpect(redirectedUrl("/login?pending=true"));

        User guest = userRepository.findByEmail("vanglai@mail.com").orElseThrow();
        assertThat(guest.getPassword()).isEqualTo(originalHash);
        assertThat(guest.isEmailVerified()).isFalse();
        assertThat(guest.getResetToken()).isNotBlank();
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals("vanglai@mail.com")).count()).isEqualTo(1);
        login("vanglai@mail.com", "KeLaChiem1").andExpect(redirectedUrl("/login?unverified=true"));

        // Chu email bam link dat mat khau -> tai khoan kich hoat, dang nhap duoc
        mockMvc.perform(post("/reset-password").with(csrf()).param("token", guest.getResetToken())
                        .param("password", "ChuThat123").param("confirmPassword", "ChuThat123"))
                .andExpect(redirectedUrl("/login?reset=true"));
        assertThat(userRepository.findByEmail("vanglai@mail.com").orElseThrow().isEmailVerified()).isTrue();
        login("vanglai@mail.com", "ChuThat123").andExpect(redirectedUrl("/customer/rooms"));
    }

    @Test
    void verifiedEmailTaken_suggestsPasswordReset_andResendDoesNotRevealAccounts() throws Exception {
        userRepository.save(User.builder().fullName("Da Co").email("daco@mail.com").phoneNumber("0934777666")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());

        register("daco@mail.com", "MatKhau123")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được đăng ký")))
                .andExpect(content().string(containsString("/forgot-password")));

        // Email khong ton tai / da kich hoat -> cung 1 thong bao, khong tao gi
        mockMvc.perform(post("/resend-verification").with(csrf()).param("email", "khong-co@mail.com"))
                .andExpect(redirectedUrl("/login?resent=true"));
        mockMvc.perform(post("/resend-verification").with(csrf()).param("email", "daco@mail.com"))
                .andExpect(redirectedUrl("/login?resent=true"));
        assertThat(userRepository.findByEmail("daco@mail.com").orElseThrow().getResetToken()).isNull();
    }
}
