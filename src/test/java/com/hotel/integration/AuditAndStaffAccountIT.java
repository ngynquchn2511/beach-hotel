package com.hotel.integration;

import com.hotel.entity.AuditAction;
import com.hotel.entity.AuditLog;
import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.repository.AuditLogRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// KHONG dung @Transactional: nhat ky chi duoc ghi SAU KHI giao dich commit, nen test phai commit that
@SpringBootTest
@AutoConfigureMockMvc
@TcSuite(level = "IT", module = "Nhật ký thao tác & quản lý tài khoản nhân viên")
class AuditAndStaffAccountIT {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User persistUser(UserRole role) {
        int n = SEQ.incrementAndGet();
        return userRepository.save(User.builder().fullName("Audit IT " + n).email("audit" + n + "@test.vn")
                .phoneNumber(String.format("08%08d", n)).password(passwordEncoder.encode("matkhau123"))
                .role(role).customerType(CustomerType.NEW).build());
    }

    private List<AuditLog> logsFor(String entityType, Long id) {
        return auditLogRepository.findAll().stream()
                .filter(l -> entityType.equals(l.getEntityType()) && id.toString().equals(l.getEntityId()))
                .toList();
    }

    @Test
    @TcSteps("ADMIN đổi loại khách hàng qua POST /admin/customers/{id}/update-type, sau đó đọc bảng audit_logs")
    void adminUpdate_isLoggedWithActorTimeAndOldNewValues() throws Exception {
        User actor = persistUser(UserRole.ADMIN);
        User customer = persistUser(UserRole.CUSTOMER);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        mockMvc.perform(post("/admin/customers/{id}/update-type", customer.getId())
                        .param("customerType", "VIP").with(csrf()).with(user(new CustomUserDetails(actor))))
                .andExpect(status().is3xxRedirection());

        AuditLog log = logsFor("User", customer.getId()).stream()
                .filter(l -> l.getAction() == AuditAction.UPDATE).findFirst().orElseThrow();
        assertThat(log.getActorEmail()).isEqualTo(actor.getEmail());
        assertThat(log.getActorRole()).isEqualTo("ADMIN");
        assertThat(log.getCreatedAt()).isAfter(before);
        assertThat(log.getChanges()).contains("Loại khách hàng").contains("→");
        assertThat(log.getRequestUrl()).contains("/admin/customers/" + customer.getId() + "/update-type");

        // Trang nhat ky (chi ADMIN) hien thi duoc ban ghi vua tao, loc theo email nguoi thao tac
        User admin = persistUser(UserRole.ADMIN);
        String html = mockMvc.perform(get("/admin/audit-logs").param("q", actor.getEmail()).param("entityType", "User")
                        .param("action", "UPDATE").with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains(actor.getEmail()).contains("Loại khách hàng: Khách lần đầu → ");
    }

    @Test
    @TcSteps("ADMIN tạo tài khoản nhân viên qua POST /admin/staff-accounts/new")
    void adminCreatesStaff_passwordIsMaskedInLog() throws Exception {
        User admin = persistUser(UserRole.ADMIN);
        String email = "newstaff" + SEQ.incrementAndGet() + "@test.vn";

        mockMvc.perform(post("/admin/staff-accounts/new").with(csrf()).with(user(new CustomUserDetails(admin)))
                        .param("fullName", "Nhân viên mới").param("email", email).param("phoneNumber", "0912345678")
                        .param("role", "STAFF").param("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin/staff-accounts"));

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.STAFF);
        AuditLog log = logsFor("User", created.getId()).get(0);
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.getActorEmail()).isEqualTo(admin.getEmail());
        assertThat(log.getChanges()).contains("Mật khẩu: ******").doesNotContain(created.getPassword());
    }

    @Test
    @TcSteps("ADMIN khóa tài khoản nhân viên, nhân viên đó đăng nhập lại tại /admin/login")
    void lockedStaff_cannotLogin() throws Exception {
        User admin = persistUser(UserRole.ADMIN);
        User staff = persistUser(UserRole.STAFF);

        mockMvc.perform(post("/admin/staff-accounts/{id}/toggle-lock", staff.getId())
                        .with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/staff-accounts"));
        assertThat(userRepository.findById(staff.getId()).orElseThrow().isLocked()).isTrue();

        mockMvc.perform(formLogin("/admin/login").user("username", staff.getEmail()).password("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin?locked=true"));
    }

    @Test
    @TcSteps("ADMIN tự khóa chính mình")
    void adminCannotLockSelf() throws Exception {
        User admin = persistUser(UserRole.ADMIN);
        mockMvc.perform(post("/admin/staff-accounts/{id}/toggle-lock", admin.getId())
                        .with(csrf()).with(user(new CustomUserDetails(admin))))
                .andExpect(redirectedUrl("/admin/staff-accounts"));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isLocked()).isFalse();
    }
}
