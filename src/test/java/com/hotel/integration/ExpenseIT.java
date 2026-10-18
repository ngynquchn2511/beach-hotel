package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.ExpenseRepository;
import com.hotel.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Chi phi van hanh: khoan 1 lan / hang thang (dung tu thang), gia von dich vu, phi cong thanh toan
class ExpenseIT extends ItFixtures {

    @Autowired private ExpenseService expenseService;
    @Autowired private ExpenseRepository expenseRepository;

    private User accountant;
    private final YearMonth jan = YearMonth.of(2025, 1);

    @BeforeEach
    void seed() {
        accountant = persistUser(UserRole.ACCOUNTANT, null);
    }

    @Test
    void monthlyAndOneOff_countedPerMonth_stopKeepsHistory() throws Exception {
        mockMvc.perform(post("/admin/accounting/expenses").with(csrf()).with(as(accountant))
                        .param("category", "PAYROLL").param("description", "Lương 12 nhân viên").param("amount", "120000000")
                        .param("recurrence", "MONTHLY").param("startMonth", "2025-01"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm")));
        mockMvc.perform(post("/admin/accounting/expenses").with(csrf()).with(as(accountant))
                .param("category", "MAINTENANCE").param("description", "Thay máy bơm hồ bơi").param("amount", "8500000")
                .param("recurrence", "ONE_OFF").param("expenseDate", "2025-02-10"));

        assertThat(expenseService.totalsByCategory(jan)).containsOnlyKeys(Expense.Category.PAYROLL);
        assertThat(expenseService.totalsByCategory(jan.plusMonths(1)).get(Expense.Category.MAINTENANCE)).isEqualByComparingTo("8500000");
        assertThat(expenseService.totalsByCategory(jan.plusMonths(5)).get(Expense.Category.PAYROLL)).isEqualByComparingTo("120000000");

        Expense payroll = expenseRepository.findAll().stream().filter(Expense::isMonthly).findFirst().orElseThrow();
        mockMvc.perform(post("/admin/accounting/expenses/{id}/stop", payroll.getId()).with(csrf()).with(as(accountant)).param("month", "2025-04"))
                .andExpect(flash().attribute("successMessage", containsString("4/2025")));
        assertThat(expenseService.totalsByCategory(YearMonth.of(2025, 3))).containsKey(Expense.Category.PAYROLL);
        assertThat(expenseService.totalsByCategory(YearMonth.of(2025, 4))).doesNotContainKey(Expense.Category.PAYROLL);

        mockMvc.perform(get("/admin/accounting/expenses").with(as(accountant)).param("month", "2025-02"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Thay máy bơm hồ bơi")))
                .andExpect(content().string(containsString("Lương 12 nhân viên")));
    }

    @Test
    void validation_costSettings_access() throws Exception {
        mockMvc.perform(post("/admin/accounting/expenses").with(csrf()).with(as(accountant))
                        .param("category", "OTHER").param("description", "X").param("amount", "0").param("recurrence", "ONE_OFF").param("expenseDate", "2025-01-01"))
                .andExpect(flash().attribute("errorMessage", containsString("lớn hơn 0")));
        mockMvc.perform(post("/admin/accounting/expenses").with(csrf()).with(as(accountant))
                        .param("category", "OTHER").param("description", "X").param("amount", "1000").param("recurrence", "MONTHLY")
                        .param("startMonth", "2025-05").param("endMonth", "2025-01"))
                .andExpect(flash().attribute("errorMessage", containsString("Tháng kết thúc")));

        mockMvc.perform(post("/admin/accounting/expenses/cost-settings").with(csrf()).with(as(accountant))
                        .param("cost_ROOM_SERVICE", "35").param("cost_SPA", "40").param("paymentFeePercent", "1.1").param("variableCostPerNight", "120000"))
                .andExpect(flash().attribute("successMessage", containsString("Đã lưu")));
        assertThat(expenseService.costPercent(ChargeType.ROOM_SERVICE)).isEqualTo(35);
        assertThat(expenseService.costPercent(ChargeType.MINIBAR)).isZero();
        assertThat(expenseService.paymentFeePercent()).isEqualByComparingTo("1.1");
        assertThat(expenseService.variableCostPerNight()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        mockMvc.perform(post("/admin/accounting/expenses/cost-settings").with(csrf()).with(as(accountant)).param("cost_SPA", "150"))
                .andExpect(flash().attribute("errorMessage", containsString("0 đến 100")));

        mockMvc.perform(get("/admin/accounting/expenses").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
    }
}
