package com.hotel.integration;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Sai mat khau 5 lan lien tiep -> khoa tam 15 phut, ca cong khach (/login) lan cong quan tri (/admin/login)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginLockoutIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "khoa.tam@mail.com";
    private static final String PASSWORD = "DungMatKhau1";

    @BeforeEach
    void seed() {
        userRepository.save(User.builder().fullName("Khach Khoa Tam").email(EMAIL).phoneNumber("0988888888")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
    }

    private org.springframework.test.web.servlet.ResultActions login(String path, String email, String password) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).param("username", email).param("password", password));
    }

    @Test
    void fiveWrongPasswords_lockTemporarily_evenCorrectPasswordRejected_thenUnlockAfterExpiry() throws Exception {
        for (int i = 1; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            login("/login", EMAIL, "sai-" + i).andExpect(redirectedUrl("/login?error=true"));
        }
        login("/login", EMAIL, "sai-5").andExpect(redirectedUrl("/login?locked=temp&minutes=15"));

        User locked = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(locked.getLoginLockedUntil()).isBetween(LocalDateTime.now().plusMinutes(14), LocalDateTime.now().plusMinutes(16));

        // Dung mat khau cung khong vao duoc trong luc khoa tam
        login("/login", EMAIL, PASSWORD).andExpect(redirectedUrl("/login?locked=temp&minutes=15"));
        mockMvc.perform(get("/login").param("locked", "temp").param("minutes", "15"))
                .andExpect(content().string(containsString("tạm khóa")))
                .andExpect(content().string(containsString("/forgot-password")));

        // Het han khoa -> dang nhap dung duoc, bo dem xoa sach
        locked.setLoginLockedUntil(LocalDateTime.now().minusSeconds(1));
        userRepository.save(locked);
        login("/login", EMAIL, PASSWORD).andExpect(redirectedUrl("/customer/rooms"));
        User after = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(after.getLoginLockedUntil()).isNull();
        assertThat(after.getFailedLoginCount()).isZero();
    }

    @Test
    void successfulLogin_resetsCounter_soFailuresMustBeConsecutive() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS - 1; i++) {
            login("/login", EMAIL, "sai");
        }
        login("/login", EMAIL, PASSWORD).andExpect(redirectedUrl("/customer/rooms"));
        login("/login", EMAIL, "sai").andExpect(redirectedUrl("/login?error=true"));

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getFailedLoginCount()).isEqualTo(1);
    }

    @Test
    void unknownEmail_neverLocks() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS + 2; i++) {
            login("/login", "khong-ton-tai@mail.com", "sai").andExpect(redirectedUrl("/login?error=true"));
        }
    }

    @Test
    void adminPortal_staffLockedTemporarily_adminCanUnlock() throws Exception {
        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {
            login("/admin/login", "staff@hotel.com", "sai");
        }
        login("/admin/login", "staff@hotel.com", "staff123").andExpect(redirectedUrl("/admin?locked=temp&minutes=15"));
        mockMvc.perform(get("/admin").param("locked", "temp").param("minutes", "15"))
                .andExpect(content().string(containsString("Tài khoản tạm khóa")));

        User staff = userRepository.findByEmail("staff@hotel.com").orElseThrow();
        User admin = userRepository.findByEmail("admin@hotel.com").orElseThrow();
        mockMvc.perform(get("/admin/staff-accounts").with(user(new CustomUserDetails(admin))))
                .andExpect(content().string(containsString("Tạm khóa (sai mật khẩu)")));

        // Nut "Mo khoa" cua admin go khoa tam (khong chuyen sang khoa han)
        mockMvc.perform(post("/admin/staff-accounts/{id}/toggle-lock", staff.getId()).with(csrf())
                .with(user(new CustomUserDetails(admin))));
        User unlocked = userRepository.findByEmail("staff@hotel.com").orElseThrow();
        assertThat(unlocked.isTemporarilyLocked()).isFalse();
        assertThat(unlocked.isLocked()).isFalse();
        login("/admin/login", "staff@hotel.com", "staff123").andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void adminLock_stillShowsContactAdminMessage() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setLocked(true);
        userRepository.save(user);

        login("/login", EMAIL, PASSWORD).andExpect(redirectedUrl("/login?locked=true"));
    }
}
