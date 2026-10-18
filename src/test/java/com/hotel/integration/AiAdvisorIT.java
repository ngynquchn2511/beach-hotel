package com.hotel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entity.*;
import com.hotel.repository.AiReportRepository;
import com.hotel.service.CancellationReportService;
import com.hotel.service.ExpenseService;
import com.hotel.service.InsightService;
import com.hotel.service.ProfitService;
import com.hotel.service.ai.AiAdvisorService;
import com.hotel.service.ai.AiAnalysis;
import com.hotel.service.ai.AiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Tro ly AI: so lieu gui di (khong co thong tin ca nhan khach), luu ket qua, hien tren trang; khong co API key -> tat, van co nhan dinh tu dong
class AiAdvisorIT extends ItFixtures {

    @Autowired private ProfitService profitService;
    @Autowired private InsightService insightService;
    @Autowired private CancellationReportService cancellationService;
    @Autowired private ExpenseService expenseService;
    @Autowired private AiReportRepository reportRepository;

    // AI gia: ghi lai noi dung nhan duoc, tra ket qua co dinh
    static class FakeGateway implements AiGateway {
        String lastSystem;
        String lastUser;

        public boolean isEnabled() { return true; }
        public String model() { return "claude-haiku-5-5"; }
        public Result<AiAnalysis> analyze(String system, String user) {
            lastSystem = system;
            lastUser = user;
            return new Result<>(new AiAnalysis("Tháng có lãi nhờ công suất tăng.",
                    List.of(new AiAnalysis.Finding("Công suất tăng", "Từ 40% lên 60%")),
                    List.of(new AiAnalysis.Finding("Chi phí lương cao", "Chiếm 45% doanh thu")),
                    List.of(new AiAnalysis.Recommendation("Mở giá không hoàn tiền 10%", "Giảm hủy", "+5 triệu", "CAO", "Kinh doanh")),
                    "Tháng tới dự kiến tương đương."), 1200, 800);
        }
        public Result<String> answer(String system, String user) {
            lastSystem = system;
            lastUser = user;
            return new Result<>("Lãi tăng khoảng 3 triệu.", 900, 100);
        }
    }

    private AiAdvisorService advisor(FakeGateway g) {
        return new AiAdvisorService(g, profitService, insightService, cancellationService, expenseService, reportRepository);
    }

    @Test
    void analyze_sendsAggregatedDataOnly_savesResult_pageShowsIt() throws Exception {
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        guest.setFullName("Nguyễn Văn Bí Mật");
        userRepository.save(guest);
        Room room = persistRoom(persistRoomType("Hang AI IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
        Booking b = persistBooking(guest, room, BookingStatus.CHECKED_OUT, -3, 2);
        b.setGuestName("Nguyễn Văn Bí Mật");
        bookingRepository.save(b);
        expenseService.create(Expense.Category.PAYROLL, "Lương", BigDecimal.valueOf(900_000), Expense.Recurrence.MONTHLY,
                null, YearMonth.now(), null, "test");

        FakeGateway g = new FakeGateway();
        AiAdvisorService advisor = advisor(g);
        YearMonth m = YearMonth.from(LocalDate.now().minusDays(3));
        advisor.analyze(m, "Kế toán A");

        // Du lieu gui di: JSON tong hop hop le, khong co ten / email / so dien thoai khach
        String json = g.lastUser.substring(g.lastUser.indexOf('{'), g.lastUser.lastIndexOf('}') + 1);
        var tree = new ObjectMapper().readTree(json);
        assertThat(tree.get("current").get("totalRevenue").asLong()).isPositive();
        assertThat(tree.get("trend12Months")).hasSize(12);
        assertThat(tree.get("expensesEntered").asBoolean()).isTrue();
        assertThat(g.lastUser).doesNotContain("Bí Mật").doesNotContain(guest.getEmail());
        assertThat(g.lastSystem).contains("Không bịa số liệu");

        AiAdvisorService.SavedAnalysis saved = advisor.latestAnalysis(m).orElseThrow();
        assertThat(saved.analysis().recommendations()).hasSize(1);
        assertThat(saved.report().getInputTokens()).isEqualTo(1200);
        // Haiku 5.5: 1200 x $0.10 + 800 x $0.50 tren 1 trieu token
        assertThat(AiAdvisorService.costUsd(saved.report())).isEqualTo(0.0005);

        // Trang dung bean that (khong co API key) -> nut AI tat, nhung van hien ban phan tich da luu + nhan dinh tu dong
        mockMvc.perform(get("/admin/accounting/insights").with(as(persistUser(UserRole.ACCOUNTANT, null))).param("month", m.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhận định tự động")))
                .andExpect(content().string(containsString("Mở giá không hoàn tiền 10%")))
                .andExpect(content().string(containsString("ANTHROPIC_API_KEY")));
    }

    @Test
    void ask_validatesAndSaves_disabledGatewayGivesClearError() throws Exception {
        FakeGateway g = new FakeGateway();
        AiAdvisorService advisor = advisor(g);
        YearMonth m = YearMonth.of(2025, 6);
        assertThatThrownBy(() -> advisor.ask(m, "  ", "A")).hasMessageContaining("câu hỏi");
        AiReport answer = advisor.ask(m, "Tăng giá cuối tuần 10% thì sao?", "A");
        assertThat(answer.getContent()).isEqualTo("Lãi tăng khoảng 3 triệu.");
        assertThat(g.lastUser).isEqualTo("Tăng giá cuối tuần 10% thì sao?");
        assertThat(g.lastSystem).contains("SỐ LIỆU").contains("\"month\":\"2025-06\"");
        assertThat(advisor.questions(m)).hasSize(1);

        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/accounting/insights/analyze").with(csrf()).with(as(admin)).param("month", "2025-06"))
                .andExpect(flash().attribute("errorMessage", containsString("ANTHROPIC_API_KEY")));
        mockMvc.perform(get("/admin/accounting/insights").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
    }
}
