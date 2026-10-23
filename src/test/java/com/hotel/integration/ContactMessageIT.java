package com.hotel.integration;

import com.hotel.entity.ContactMessage;
import com.hotel.repository.ContactMessageRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Form "Gui yeu cau tu van" o trang gioi thieu -> hop thu lien he cua le tan -> tra loi qua email
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContactMessageIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContactMessageRepository repository;
    @Autowired private UserRepository userRepository;

    // Tai khoan khach dung de gui form lien he (lien he bat buoc dang nhap)
    private CustomUserDetails customer() {
        return new CustomUserDetails(userRepository.findByEmail("khach.lienhe@mail.com").orElseGet(() -> userRepository.save(
                com.hotel.entity.User.builder().fullName("Khach Lien He IT").email("khach.lienhe@mail.com").phoneNumber("0900111222")
                        .password("x").role(com.hotel.entity.UserRole.CUSTOMER).build())));
    }

    @Test
    void aboutPage_loggedIn_showsContactFormPrefilled() throws Exception {
        mockMvc.perform(get("/about").with(user(customer())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"lien-he\"")))
                .andExpect(content().string(containsString("Gửi yêu cầu tư vấn")))
                .andExpect(content().string(containsString("name=\"website\"")))
                .andExpect(content().string(containsString("value=\"Khach Lien He IT\"")))
                .andExpect(content().string(containsString("value=\"khach.lienhe@mail.com\"")));
    }

    @Test
    void anonymous_seesLoginButton_sendingNeedsLogin_thenBackToForm() throws Exception {
        // Chua dang nhap: khong co form, chi co nut "Dang nhap de gui yeu cau" tro toi /contact
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng nhập để gửi yêu cầu")))
                .andExpect(content().string(containsString("href=\"/contact\"")))
                .andExpect(content().string(not(containsString("name=\"website\""))));
        // Gui thang form khi chua dang nhap -> ve /login, khong luu gi
        long before = repository.count();
        mockMvc.perform(post("/contact").with(csrf())
                        .param("fullName", "Khach Chua Dang Nhap").param("email", "chuadn@mail.com")
                        .param("message", "Cho hoi gia phong thang 12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(repository.count()).isEqualTo(before);
        mockMvc.perform(get("/contact"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        // Dang nhap xong mo lai /contact -> ve dung form lien he
        mockMvc.perform(get("/contact").with(user(customer())))
                .andExpect(redirectedUrl("/about#lien-he"));
    }

    @Test
    void customerSubmits_staffSeesBadgeRepliesAndMessageIsHandled() throws Exception {
        mockMvc.perform(post("/contact").with(csrf()).with(user(customer()))
                        .param("fullName", "Pham Thi Lien He")
                        .param("email", "lienhe@mail.com")
                        .param("phone", "0912 345 678")
                        .param("message", "Doan 20 nguoi muon dat tiec toi ngay 20/12, resort co nhan khong?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/about#lien-he"))
                .andExpect(flash().attribute("contactSuccess", containsString("Đã gửi yêu cầu")));

        List<ContactMessage> saved = repository.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> m.getEmail().equals("lienhe@mail.com")).toList();
        assertThat(saved).hasSize(1);
        ContactMessage msg = saved.get(0);
        assertThat(msg.isHandled()).isFalse();

        CustomUserDetails staff = new CustomUserDetails(userRepository.findByEmail("staff@hotel.com").orElseThrow());
        mockMvc.perform(get("/admin/contact-messages").param("status", "NEW").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pham Thi Lien He")))
                .andExpect(content().string(containsString("Doan 20 nguoi muon dat tiec")))
                .andExpect(content().string(containsString("title=\"Tin liên hệ chưa xử lý\"")));

        mockMvc.perform(post("/admin/contact-messages/{id}/reply", msg.getId()).with(csrf()).with(user(staff))
                        .param("reply", "Resort nhan tiec doan, le tan se goi lai cho chi.")
                        .param("status", "NEW"))
                .andExpect(redirectedUrl("/admin/contact-messages?status=NEW&page=1#contact-" + msg.getId()))
                .andExpect(flash().attribute("successMessage", "Đã gửi trả lời tới lienhe@mail.com"));

        ContactMessage replied = repository.findById(msg.getId()).orElseThrow();
        assertThat(replied.isHandled()).isTrue();
        assertThat(replied.getReply()).isEqualTo("Resort nhan tiec doan, le tan se goi lai cho chi.");
        assertThat(replied.getHandledBy()).isEqualTo(staff.getUser().getFullName());

        mockMvc.perform(get("/admin/contact-messages").param("status", "NEW").with(user(staff)))
                .andExpect(content().string(not(containsString("Pham Thi Lien He"))));
        mockMvc.perform(get("/admin/contact-messages").param("status", "HANDLED").with(user(staff)))
                .andExpect(content().string(containsString("Resort nhan tiec doan")));
    }

    @Test
    void invalidSubmission_keepsInputAndShowsError() throws Exception {
        mockMvc.perform(post("/contact").with(csrf()).with(user(customer()))
                        .param("fullName", "Khach Sai Email")
                        .param("email", "khong-phai-email")
                        .param("message", "Noi dung hop le du dai"))
                .andExpect(redirectedUrl("/about#lien-he"))
                .andExpect(flash().attribute("contactError", "Email không hợp lệ."))
                .andExpect(flash().attributeExists("contactForm"));
        assertThat(repository.findAll()).noneMatch(m -> m.getFullName().equals("Khach Sai Email"));

        // Trang hien loi + giu noi dung khach da go
        mockMvc.perform(get("/about").with(user(customer()))
                        .flashAttr("contactError", "Email không hợp lệ.")
                        .flashAttr("contactForm", java.util.Map.of("fullName", "Khach Sai Email", "email", "x",
                                "phone", "", "message", "Noi dung hop le du dai")))
                .andExpect(content().string(containsString("Email không hợp lệ.")))
                .andExpect(content().string(containsString("value=\"Khach Sai Email\"")));
    }

    @Test
    void honeypotFilled_pretendsSuccessButSavesNothing() throws Exception {
        long before = repository.count();
        mockMvc.perform(post("/contact").with(csrf()).with(user(customer()))
                        .param("fullName", "Bot")
                        .param("email", "bot@spam.com")
                        .param("message", "Mua thuoc gia re tai day")
                        .param("website", "http://spam.example"))
                .andExpect(flash().attributeExists("contactSuccess"));
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void tooManyFromSameEmail_blocked() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/contact").with(csrf()).with(user(customer())).param("fullName", "Khach Gui Nhieu")
                            .param("email", "nhieu@mail.com").param("message", "Cau hoi so " + i + " ve phong"))
                    .andExpect(flash().attributeExists("contactSuccess"));
        }
        mockMvc.perform(post("/contact").with(csrf()).with(user(customer())).param("fullName", "Khach Gui Nhieu")
                        .param("email", "NHIEU@mail.com").param("message", "Cau hoi thu tu ve phong"))
                .andExpect(flash().attribute("contactError", containsString("nhiều yêu cầu")));
    }

    @Test
    void adminInbox_requiresStaffLogin() throws Exception {
        mockMvc.perform(get("/admin/contact-messages"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin"));
    }
}
