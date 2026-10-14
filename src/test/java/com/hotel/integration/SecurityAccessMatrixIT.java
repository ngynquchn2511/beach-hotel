package com.hotel.integration;

import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TcSuite(level = "IT", module = "Phân quyền truy cập & bảo vệ CSRF (Spring Security)")
class SecurityAccessMatrixIT extends ItFixtures {

    private MockHttpServletRequestBuilder withRole(MockHttpServletRequestBuilder req, String role) {
        if (role.equals("ANONYMOUS")) {
            return req;
        }
        User u = persistUser(UserRole.valueOf(role), CustomerType.NEW);
        return req.with(as(u));
    }

    @TcSteps("Gửi GET tới đường dẫn bằng MockMvc với vai trò tương ứng (chưa đăng nhập / CUSTOMER / STAFF / ADMIN), đi qua đủ 2 SecurityFilterChain")
    @ParameterizedTest(name = "Truy cập {0} với vai trò {1} ¦ GET {0}, role={1} ¦ {2}")
    @CsvSource({
            // Trang công khai
            "/,ANONYMOUS,200 OK", "/,CUSTOMER,200 OK", "/,STAFF,200 OK", "/,ADMIN,200 OK",
            "/home,ANONYMOUS,200 OK", "/home,CUSTOMER,200 OK", "/home,STAFF,200 OK", "/home,ADMIN,200 OK",
            "/rooms,ANONYMOUS,200 OK", "/rooms,CUSTOMER,200 OK", "/rooms,STAFF,200 OK", "/rooms,ADMIN,200 OK",
            "/login,ANONYMOUS,200 OK", "/login,CUSTOMER,200 OK", "/login,STAFF,200 OK", "/login,ADMIN,200 OK",
            "/register,ANONYMOUS,200 OK", "/register,CUSTOMER,200 OK", "/register,STAFF,200 OK", "/register,ADMIN,200 OK",
            "/customer/rooms,ANONYMOUS,200 OK", "/customer/rooms,CUSTOMER,200 OK", "/customer/rooms,STAFF,200 OK", "/customer/rooms,ADMIN,200 OK",
            "/admin,ANONYMOUS,200 OK", "/admin,CUSTOMER,200 OK", "/admin,STAFF,200 OK", "/admin,ADMIN,200 OK",
            // Chỉ khách hàng đã đăng nhập
            "/customer/bookings,ANONYMOUS,Chuyển về /login", "/customer/bookings,CUSTOMER,200 OK",
            "/customer/bookings,STAFF,403 Forbidden", "/customer/bookings,ADMIN,403 Forbidden",
            // Khu vực quản trị: STAFF + ADMIN
            "/admin/dashboard,ANONYMOUS,Chuyển về /admin", "/admin/dashboard,CUSTOMER,403 Forbidden", "/admin/dashboard,STAFF,200 OK", "/admin/dashboard,ADMIN,200 OK",
            "/admin/bookings,ANONYMOUS,Chuyển về /admin", "/admin/bookings,CUSTOMER,403 Forbidden", "/admin/bookings,STAFF,200 OK", "/admin/bookings,ADMIN,200 OK",
            "/admin/rooms,ANONYMOUS,Chuyển về /admin", "/admin/rooms,CUSTOMER,403 Forbidden", "/admin/rooms,STAFF,200 OK", "/admin/rooms,ADMIN,200 OK",
            "/admin/room-types,ANONYMOUS,Chuyển về /admin", "/admin/room-types,CUSTOMER,403 Forbidden", "/admin/room-types,STAFF,200 OK", "/admin/room-types,ADMIN,200 OK",
            "/admin/combos,ANONYMOUS,Chuyển về /admin", "/admin/combos,CUSTOMER,403 Forbidden", "/admin/combos,STAFF,200 OK", "/admin/combos,ADMIN,200 OK",
            "/admin/discount-codes,ANONYMOUS,Chuyển về /admin", "/admin/discount-codes,CUSTOMER,403 Forbidden", "/admin/discount-codes,STAFF,200 OK", "/admin/discount-codes,ADMIN,200 OK",
            "/admin/customers,ANONYMOUS,Chuyển về /admin", "/admin/customers,CUSTOMER,403 Forbidden", "/admin/customers,STAFF,200 OK", "/admin/customers,ADMIN,200 OK",
            "/admin/bookings/walk-in/new,ANONYMOUS,Chuyển về /admin", "/admin/bookings/walk-in/new,CUSTOMER,403 Forbidden",
            "/admin/bookings/walk-in/new,STAFF,200 OK", "/admin/bookings/walk-in/new,ADMIN,200 OK",
            "/staff/bookings,ANONYMOUS,Chuyển về /admin", "/staff/bookings,CUSTOMER,403 Forbidden", "/staff/bookings,STAFF,200 OK", "/staff/bookings,ADMIN,200 OK",
            // Tạo mới danh mục và mã giảm giá - chỉ ADMIN, STAFF bị chặn
            "/admin/room-types/new,ANONYMOUS,Chuyển về /admin", "/admin/room-types/new,CUSTOMER,403 Forbidden", "/admin/room-types/new,STAFF,403 Forbidden", "/admin/room-types/new,ADMIN,200 OK",
            "/admin/rooms/new,ANONYMOUS,Chuyển về /admin", "/admin/rooms/new,CUSTOMER,403 Forbidden", "/admin/rooms/new,STAFF,403 Forbidden", "/admin/rooms/new,ADMIN,200 OK",
            "/admin/combos/new,ANONYMOUS,Chuyển về /admin", "/admin/combos/new,CUSTOMER,403 Forbidden", "/admin/combos/new,STAFF,403 Forbidden", "/admin/combos/new,ADMIN,200 OK",
            "/admin/discount-codes/new,ANONYMOUS,Chuyển về /admin", "/admin/discount-codes/new,CUSTOMER,403 Forbidden",
            "/admin/discount-codes/new,STAFF,403 Forbidden", "/admin/discount-codes/new,ADMIN,200 OK",
            // Khu vực quản lý nhân viên và nhật ký thao tác - chỉ ADMIN
            "/admin/staff-accounts,ANONYMOUS,Chuyển về /admin", "/admin/staff-accounts,CUSTOMER,403 Forbidden",
            "/admin/staff-accounts,STAFF,403 Forbidden", "/admin/staff-accounts,ADMIN,200 OK",
            "/admin/staff-accounts/new,STAFF,403 Forbidden", "/admin/staff-accounts/new,ADMIN,200 OK",
            "/admin/audit-logs,ANONYMOUS,Chuyển về /admin", "/admin/audit-logs,CUSTOMER,403 Forbidden",
            "/admin/audit-logs,STAFF,403 Forbidden", "/admin/audit-logs,ADMIN,200 OK"})
    void accessMatrix(String path, String role, String expected) throws Exception {
        var result = mockMvc.perform(withRole(get(path), role));
        switch (expected) {
            case "200 OK" -> result.andExpect(status().isOk());
            case "403 Forbidden" -> result.andExpect(status().isForbidden());
            case "Chuyển về /login" -> result.andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
            default -> result.andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin"));
        }
    }

    @TcSteps("Gửi POST tới endpoint thay đổi dữ liệu nhưng KHÔNG kèm CSRF token (vai trò ADMIN)")
    @ParameterizedTest(name = "Chặn POST thiếu CSRF token ¦ POST {0} không có _csrf ¦ 403 Forbidden, dữ liệu không đổi")
    @CsvSource({"/register", "/customer/bookings/new", "/admin/bookings/1/confirm", "/admin/bookings/1/cancel", "/admin/rooms/1/delete",
            "/admin/rooms/1/status", "/admin/combos/1/toggle-active", "/admin/discount-codes/1/delete", "/admin/customers/1/update-type",
            "/admin/bookings/walk-in/new", "/api/chatbot/ask", "/logout", "/admin/logout"})
    void csrfRequired(String path) throws Exception {
        User admin = persistUser(UserRole.ADMIN, CustomerType.NEW);
        mockMvc.perform(post(path).with(as(admin))).andExpect(status().isForbidden());
    }

    @TcSteps("Gửi POST hợp lệ có CSRF token nhưng vai trò không đủ quyền")
    @ParameterizedTest(name = "Chặn thao tác ghi khi không đủ quyền ¦ POST {0} với vai trò {1} (có CSRF) ¦ {2}")
    @CsvSource({"/admin/staff-accounts/new,STAFF,403 Forbidden", "/admin/staff-accounts/1/edit,STAFF,403 Forbidden",
            "/admin/staff-accounts/1/toggle-lock,STAFF,403 Forbidden", "/admin/customers/1/toggle-lock,CUSTOMER,403 Forbidden",
            "/admin/bookings/1/confirm,CUSTOMER,403 Forbidden",
            "/admin/rooms/1/delete,CUSTOMER,403 Forbidden", "/admin/rooms/1/delete,STAFF,403 Forbidden",
            "/admin/room-types/1/delete,STAFF,403 Forbidden", "/admin/combos/1/delete,STAFF,403 Forbidden",
            "/admin/discount-codes/new,STAFF,403 Forbidden", "/admin/discount-codes/1/edit,STAFF,403 Forbidden",
            "/admin/discount-codes/1/toggle-active,STAFF,403 Forbidden", "/admin/discount-codes/1/delete,STAFF,403 Forbidden",
            "/admin/customers/1/update-type,STAFF,403 Forbidden", "/admin/customers/1/toggle-lock,STAFF,403 Forbidden",
            "/admin/bookings/1/confirm,ANONYMOUS,Chuyển về /admin"})
    void writeRequiresRole(String path, String role, String expected) throws Exception {
        var result = mockMvc.perform(withRole(post(path).with(csrf()), role));
        if (expected.startsWith("403")) {
            result.andExpect(status().isForbidden());
        } else {
            result.andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin"));
        }
    }
}
