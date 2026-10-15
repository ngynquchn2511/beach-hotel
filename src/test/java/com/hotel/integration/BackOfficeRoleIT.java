package com.hotel.integration;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Vai tro Buong phong / Ke toan: chi vao dung khu vuc cua minh
class BackOfficeRoleIT extends ItFixtures {

    @MockBean private EmailService emailService;

    @Test
    void housekeeper_onlyHousekeepingArea() throws Exception {
        User hk = persistUser(UserRole.HOUSEKEEPER, null);
        when(emailService.isConfigured()).thenReturn(false);
        mockMvc.perform(post("/admin/login").with(csrf()).param("username", hk.getEmail()).param("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin/housekeeping"));

        for (String url : new String[]{"/admin/housekeeping", "/admin/maintenance", "/admin/lost-found"}) {
            mockMvc.perform(get(url).with(as(hk))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/admin/housekeeping").with(as(hk)))
                .andExpect(content().string(containsString("Buồng phòng")))
                .andExpect(content().string(not(containsString("Đặt phòng / Check-in-out"))));
        for (String url : new String[]{"/admin/bookings", "/admin/dashboard", "/admin/shifts", "/admin/company-invoices", "/admin/customers"}) {
            mockMvc.perform(get(url).with(as(hk))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/admin/home").with(as(hk))).andExpect(redirectedUrl("/admin/housekeeping"));
        // Khong dang nhap cong khach duoc
        mockMvc.perform(post("/login").with(csrf()).param("username", hk.getEmail()).param("password", "matkhau123"))
                .andExpect(redirectedUrl("/login?error=wrong_portal"));
    }

    @Test
    void accountant_readsReportsButCannotOperate() throws Exception {
        User acc = persistUser(UserRole.ACCOUNTANT, null);
        mockMvc.perform(get("/admin/home").with(as(acc))).andExpect(redirectedUrl("/admin/company-invoices"));
        for (String url : new String[]{"/admin/company-invoices", "/admin/dashboard", "/admin/shifts", "/admin/gift-vouchers", "/admin/forecast"}) {
            mockMvc.perform(get(url).with(as(acc))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/admin/shifts").with(as(acc)))
                .andExpect(content().string(not(containsString("Nhận ca</button>"))));
        mockMvc.perform(post("/admin/shifts/open").with(csrf()).with(as(acc)).param("openingCash", "0"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/gift-vouchers/1/cancel").with(csrf()).with(as(acc))).andExpect(status().isForbidden());
        for (String url : new String[]{"/admin/bookings", "/admin/housekeeping", "/admin/staff-accounts"}) {
            mockMvc.perform(get(url).with(as(acc))).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCreatesNewRoleAccounts() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/staff-accounts/new").with(csrf()).with(as(admin))
                        .param("fullName", "Chị Lan buồng phòng").param("email", "lan.hk@test.vn").param("phoneNumber", "0912000111")
                        .param("role", "HOUSEKEEPER").param("password", "matkhau123"))
                .andExpect(redirectedUrl("/admin/staff-accounts"));
        assertThat(userRepository.findByEmail("lan.hk@test.vn").orElseThrow().getRole()).isEqualTo(UserRole.HOUSEKEEPER);
        mockMvc.perform(get("/admin/staff-accounts").with(as(admin)))
                .andExpect(content().string(containsString("Buồng phòng")));
        mockMvc.perform(post("/admin/staff-accounts/new").with(csrf()).with(as(admin))
                        .param("fullName", "Khách").param("email", "kh.x@test.vn").param("phoneNumber", "0912000112")
                        .param("role", "CUSTOMER").param("password", "matkhau123"))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail("kh.x@test.vn")).isEmpty();
        assertThat(persistUser(UserRole.CUSTOMER, CustomerType.NEW).getRole().isBackOffice()).isFalse();
    }
}
