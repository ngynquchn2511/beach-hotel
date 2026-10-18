package com.hotel.service;

import com.hotel.service.ai.AiAdvisorService;
import com.hotel.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

// Bao cao quan tri thang gui email chu resort: lai lo, so sanh, ke hoach, nhan dinh, tom tat AI (neu da phan tich)
@Service
public class MonthlyReportService {

    static final int MAX_INSIGHTS = 6;

    private final ProfitService profitService;
    private final InsightService insightService;
    private final BudgetService budgetService;
    private final AiAdvisorService advisor;
    private final DailyReportService dailyReportService;
    private final EmailService emailService;

    public MonthlyReportService(ProfitService profitService, InsightService insightService, BudgetService budgetService,
                                AiAdvisorService advisor, DailyReportService dailyReportService, EmailService emailService) {
        this.profitService = profitService;
        this.insightService = insightService;
        this.budgetService = budgetService;
        this.advisor = advisor;
        this.dailyReportService = dailyReportService;
        this.emailService = emailService;
    }

    public static String subject(YearMonth m) {
        return "Sóng Xanh Resort - Báo cáo quản trị tháng " + m.getMonthValue() + "/" + m.getYear();
    }

    // Cung danh sach nguoi nhan voi bao cao cuoi ngay
    public List<String> recipients() {
        return dailyReportService.recipients();
    }

    public boolean send(YearMonth m) {
        return emailService.sendDailyReport(recipients(), subject(m), text(m));
    }

    @Transactional(readOnly = true)
    public String text(YearMonth m) {
        ProfitService.Report r = profitService.report(m);
        ProfitService.MonthPnl c = r.current();
        StringBuilder sb = new StringBuilder();
        sb.append("BÁO CÁO QUẢN TRỊ THÁNG ").append(m.getMonthValue()).append("/").append(m.getYear()).append(" - Sóng Xanh Resort\n\n");
        sb.append("KẾT QUẢ\n");
        sb.append("- Doanh thu: ").append(Money.format(c.totalRevenue())).append(" VND").append(change(r.vsPrevious().revenue(), "tháng trước"))
                .append(change(r.vsLastYear().revenue(), "cùng kỳ")).append("\n");
        sb.append("- Chi phí: ").append(Money.format(c.totalCost())).append(" VND").append(change(r.vsPrevious().cost(), "tháng trước")).append("\n");
        sb.append("- Lãi vận hành: ").append(Money.format(c.operatingProfit())).append(" VND (biên ").append(c.margin()).append("%)")
                .append(change(r.vsPrevious().profit(), "tháng trước")).append("\n");
        sb.append("- Công suất ").append(c.occupancy()).append("% · ADR ").append(Money.format(c.adr())).append(" · RevPAR ")
                .append(Money.format(c.revPar())).append(" · GOPPAR ").append(Money.format(c.goppar())).append("\n");
        budgetService.variance(c).ifPresent(v -> {
            sb.append("\nKẾ HOẠCH\n");
            if (v.revenuePercent() != null) {
                sb.append("- Doanh thu đạt ").append(v.revenuePercent()).append("% kế hoạch");
                if (v.getRevenueShortfall().signum() > 0) {
                    sb.append(" (thiếu ").append(Money.format(v.getRevenueShortfall())).append(" VND)");
                }
                sb.append("\n");
            }
            if (v.costPercent() != null) {
                sb.append("- Chi phí dùng ").append(v.costPercent()).append("% ngân sách\n");
            }
        });
        List<InsightService.Insight> insights = insightService.analyze(m).stream()
                .filter(i -> i.level() != InsightService.Level.GOOD || i.recommendation() != null).limit(MAX_INSIGHTS).toList();
        if (!insights.isEmpty()) {
            sb.append("\nCẦN CHÚ Ý\n");
            for (InsightService.Insight i : insights) {
                sb.append("- [").append(i.level().getLabel()).append("] ").append(i.title()).append(": ").append(i.detail());
                if (i.recommendation() != null) {
                    sb.append(" → ").append(i.recommendation());
                }
                sb.append("\n");
            }
        }
        advisor.latestAnalysis(m).ifPresent(a -> {
            sb.append("\nNHẬN ĐỊNH AI\n").append(a.analysis().summary()).append("\n");
            a.analysis().recommendations().stream().limit(3)
                    .forEach(rc -> sb.append("- ").append(rc.action()).append(" (").append(rc.expectedImpact()).append(")\n"));
        });
        sb.append("\nXem chi tiết: trang Lãi lỗ và Phân tích & AI trong cổng quản trị.\n");
        return sb.toString();
    }

    private static String change(Double pct, String label) {
        if (pct == null) {
            return "";
        }
        return " (" + (pct >= 0 ? "+" : "") + pct + "% so với " + label + ")";
    }
}
