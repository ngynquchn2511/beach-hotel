package com.hotel.integration;

import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Quan ly tai khoan nhan vien: loc theo loai (buong phong, ke toan, le tan, quan tri), trang thai khoa, tim kiem
class StaffAccountFilterIT extends ItFixtures {

    private User named(UserRole role, String name) {
        User u = persistUser(role, null);
        u.setFullName(name);
        return userRepository.save(u);
    }

    @Test
    void filtersByRole_status_andKeyword() throws Exception {
        User admin = named(UserRole.ADMIN, "Quản Trị Lọc IT");
        named(UserRole.HOUSEKEEPER, "Buồng Phòng Lọc IT");
        named(UserRole.ACCOUNTANT, "Kế Toán Lọc IT");
        User locked = named(UserRole.HOUSEKEEPER, "Buồng Phòng Khóa IT");
        locked.setLocked(true);
        userRepository.save(locked);

        mockMvc.perform(get("/admin/staff-accounts").with(as(admin)).param("role", "HOUSEKEEPER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Buồng Phòng Lọc IT")))
                .andExpect(content().string(containsString("Buồng Phòng Khóa IT")))
                .andExpect(content().string(not(containsString("Kế Toán Lọc IT"))))
                .andExpect(content().string(containsString("loc-loai-nhan-vien")));

        mockMvc.perform(get("/admin/staff-accounts").with(as(admin)).param("role", "ACCOUNTANT"))
                .andExpect(content().string(containsString("Kế Toán Lọc IT")))
                .andExpect(content().string(not(containsString("Buồng Phòng Lọc IT"))));

        mockMvc.perform(get("/admin/staff-accounts").with(as(admin)).param("role", "HOUSEKEEPER").param("status", "locked"))
                .andExpect(content().string(containsString("Buồng Phòng Khóa IT")))
                .andExpect(content().string(not(containsString("Buồng Phòng Lọc IT"))));

        mockMvc.perform(get("/admin/staff-accounts").with(as(admin)).param("q", "kế toán lọc"))
                .andExpect(content().string(containsString("Kế Toán Lọc IT")))
                .andExpect(content().string(not(containsString("Quản Trị Lọc IT</span>"))));
    }
}
