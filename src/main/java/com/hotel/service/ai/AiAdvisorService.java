package com.hotel.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entity.AiReport;
import com.hotel.exception.BusinessException;
import com.hotel.repository.AiReportRepository;
import com.hotel.service.CancellationReportService;
import com.hotel.service.ExpenseService;
import com.hotel.service.InsightService;
import com.hotel.service.ProfitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Tro ly quan tri bang AI: gom so lieu lai lo / huy phong / nhan dinh theo quy tac thanh JSON gon, gui Claude
// de nhan phan tich + khuyen nghi tieng Viet (luu lai), va tra loi cau hoi cua quan ly ve so lieu thang
@Service
public class AiAdvisorService {

    public static final int QUESTION_MAX = 1000;
    // Gia tham khao (USD / 1 trieu token vao, ra) theo model - chi de hien uoc tinh chi phi.
    // Haiku 5.5: gia nay ap cho prompt <= 100K token (so lieu 1 thang chi vai nghin token)
    static double[] pricePerMTok(String model) {
        if (model == null) {
            return new double[]{4.0, 20.0};
        }
        if (model.startsWith("claude-haiku-5-5")) {
            return new double[]{0.10, 0.50};
        }
        if (model.startsWith("claude-sonnet-5")) {
            return new double[]{2.0, 10.0};
        }
        if (model.startsWith("claude-fable")) {
            return new double[]{10.0, 50.0};
        }
        return new double[]{4.0, 20.0};
    }

    static final String ANALYST_PROMPT = """
            Bạn là chuyên gia phân tích tài chính và vận hành cho một resort nghỉ dưỡng nhỏ ở Hạ Long, Việt Nam.
            Người đọc là chủ resort và quản lý - không phải dân tài chính. Viết tiếng Việt, rõ ràng, đi thẳng vào việc.

            Dữ liệu là JSON số liệu một tháng: lãi lỗ (đơn vị VND), so sánh tháng trước và cùng kỳ năm trước,
            xu hướng 12 tháng, tỷ lệ hủy, và các nhận định tự động hệ thống đã phát hiện.
            Cách tính: doanh thu phòng chia theo đêm ở trong tháng; dịch vụ theo ngày phát sinh; tiền bán thẻ quà tặng
            không tính là doanh thu. Chi phí gồm giá vốn dịch vụ theo %, chi phí mỗi phòng-đêm, phí cổng thanh toán
            và chi phí vận hành kế toán nhập. Nếu "expensesEntered" là false thì chi phí vận hành chưa được nhập -
            hãy nói rõ lãi đang cao hơn thực tế.

            Nguyên tắc:
            - Chỉ dùng số có trong dữ liệu; được phép tính toán từ chúng (tỷ lệ, chênh lệch, điểm hòa vốn) và nói rõ là ước tính.
            - Không bịa số liệu thị trường, đối thủ hay sự kiện bên ngoài.
            - Khuyến nghị phải làm được bằng công cụ resort đang có: giá cuối tuần / ngày lễ, khuyến mãi tự động,
              mã giảm giá có giới hạn, giá không hoàn tiền, luật số đêm tối thiểu, đặt cọc, nâng hạng phòng, combo,
              spa / nhà hàng / tour / đưa đón, nhắc chuyển cọc, chi phí vận hành.
            - Dữ liệu ít (vài đơn) thì nói thẳng là chưa đủ để kết luận chắc.
            """;

    private final AiGateway gateway;
    private final ProfitService profitService;
    private final InsightService insightService;
    private final CancellationReportService cancellationService;
    private final ExpenseService expenseService;
    private final AiReportRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();
    private com.hotel.service.BudgetService budgetService;
    private com.hotel.service.RevenueForecastService forecastService;
    private com.hotel.service.RoomTypePerformanceService roomTypePerformance;

    @org.springframework.beans.factory.annotation.Autowired
    void setRoomTypePerformance(com.hotel.service.RoomTypePerformanceService roomTypePerformance) {
        this.roomTypePerformance = roomTypePerformance;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setForecastService(com.hotel.service.RevenueForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setBudgetService(com.hotel.service.BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    public AiAdvisorService(AiGateway gateway, ProfitService profitService, InsightService insightService,
                            CancellationReportService cancellationService, ExpenseService expenseService, AiReportRepository repository) {
        this.gateway = gateway;
        this.profitService = profitService;
        this.insightService = insightService;
        this.cancellationService = cancellationService;
        this.expenseService = expenseService;
        this.repository = repository;
    }

    public boolean isEnabled() {
        return gateway.isEnabled();
    }

    public String model() {
        return gateway.model();
    }

    public record SavedAnalysis(AiReport report, AiAnalysis analysis) {
    }

    public Optional<SavedAnalysis> latestAnalysis(YearMonth month) {
        return repository.findFirstByReportMonthAndKindOrderByCreatedAtDescIdDesc(month.atDay(1), AiReport.Kind.ANALYSIS)
                .map(r -> new SavedAnalysis(r, read(r.getContent())));
    }

    public List<AiReport> questions(YearMonth month) {
        return repository.findTop20ByReportMonthAndKindOrderByCreatedAtDescIdDesc(month.atDay(1), AiReport.Kind.QUESTION);
    }

    @Transactional
    public SavedAnalysis analyze(YearMonth month, String requestedBy) {
        String data = dataJson(month);
        AiGateway.Result<AiAnalysis> result = gateway.analyze(ANALYST_PROMPT,
                "Số liệu tháng " + month + ":\n" + data + "\n\nHãy phân tích và đưa khuyến nghị.");
        AiReport saved = repository.save(AiReport.builder().reportMonth(month.atDay(1)).kind(AiReport.Kind.ANALYSIS)
                .content(write(result.value())).model(gateway.model()).inputTokens(result.inputTokens())
                .outputTokens(result.outputTokens()).createdBy(requestedBy).build());
        return new SavedAnalysis(saved, result.value());
    }

    @Transactional
    public AiReport ask(YearMonth month, String question, String requestedBy) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty() || q.length() > QUESTION_MAX) {
            throw new BusinessException("Vui lòng nhập câu hỏi (tối đa " + QUESTION_MAX + " ký tự)");
        }
        // So lieu dat trong system (co cache) de nhieu cau hoi cung thang dung lai
        String system = ANALYST_PROMPT + "\nTrả lời ngắn gọn câu hỏi của quản lý dựa trên số liệu tháng " + month + " dưới đây"
                + " (có thể dùng gạch đầu dòng). Không đủ dữ liệu thì nói rõ cần thêm gì.\n\nSỐ LIỆU:\n" + dataJson(month);
        AiGateway.Result<String> result = gateway.answer(system, q);
        return repository.save(AiReport.builder().reportMonth(month.atDay(1)).kind(AiReport.Kind.QUESTION).question(q)
                .content(result.value()).model(gateway.model()).inputTokens(result.inputTokens())
                .outputTokens(result.outputTokens()).createdBy(requestedBy).build());
    }

    // Chi phi uoc tinh 1 lan goi (USD)
    public static double costUsd(AiReport r) {
        long in = r.getInputTokens() == null ? 0 : r.getInputTokens();
        long out = r.getOutputTokens() == null ? 0 : r.getOutputTokens();
        double[] price = pricePerMTok(r.getModel());
        return Math.round((in * price[0] + out * price[1]) / 1_000_000 * 10000) / 10000.0;
    }

    // ===== So lieu gui AI (gon, chi so tong hop - khong co thong tin ca nhan cua khach) =====

    @Transactional(readOnly = true)
    public String dataJson(YearMonth month) {
        ProfitService.Report r = profitService.report(month);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("month", month.toString());
        root.put("currency", "VND");
        root.put("expensesEntered", r.current().expensesTotal().signum() > 0);
        root.put("current", pnl(r.current()));
        root.put("previousMonth", pnl(r.previous()));
        root.put("sameMonthLastYear", pnl(r.lastYear()));
        root.put("changeVsPreviousPercent", delta(r.vsPrevious()));
        root.put("changeVsLastYearPercent", delta(r.vsLastYear()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (ProfitService.MonthPnl m : r.trend()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("month", m.month().toString());
            t.put("revenue", m.totalRevenue());
            t.put("cost", m.totalCost());
            t.put("operatingProfit", m.operatingProfit());
            t.put("occupancyPercent", m.occupancy());
            t.put("adr", m.adr());
            trend.add(t);
        }
        root.put("trend12Months", trend);
        CancellationReportService.Line cancel = cancellationService.build(month, month).total();
        root.put("bookings", Map.of("created", cancel.created(), "cancelled", cancel.cancelled(), "noShows", cancel.noShows(),
                "cancelRatePercent", cancel.cancelRate(), "lateCancels", cancel.lateCancels(), "forfeitedDeposits", cancel.forfeited()));
        Map<String, Integer> rates = new LinkedHashMap<>();
        expenseService.costRates().forEach((k, v) -> rates.put(k.name(), v));
        root.put("serviceCostPercent", rates);
        root.put("paymentFeePercent", expenseService.paymentFeePercent());
        root.put("variableCostPerOccupiedNight", expenseService.variableCostPerNight());
        List<Map<String, String>> insights = new ArrayList<>();
        for (InsightService.Insight i : insightService.analyze(month)) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("level", i.level().name());
            m.put("title", i.title());
            m.put("detail", i.detail());
            insights.add(m);
        }
        root.put("ruleBasedFindings", insights);
        if (roomTypePerformance != null) {
            List<Map<String, Object>> types = new ArrayList<>();
            for (com.hotel.service.RoomTypePerformanceService.Row t : roomTypePerformance.month(month)) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("roomType", t.type().getName());
                x.put("rooms", t.rooms());
                x.put("occupancyPercent", t.occupancy());
                x.put("adr", t.adr());
                x.put("revPar", t.revPar());
                x.put("serviceRevenue", t.serviceRevenue());
                x.put("contributionPerAvailableNight", t.contributionPerAvailableNight());
                types.add(x);
            }
            root.put("roomTypePerformance", types);
        }
        // Du bao chi co y nghia khi phan tich thang hien tai
        if (forecastService != null && month.equals(YearMonth.now())) {
            List<Map<String, Object>> fc = new ArrayList<>();
            for (com.hotel.service.RevenueForecastService.MonthForecast f : forecastService.forecast(java.time.LocalDate.now())) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("month", f.month().toString());
                x.put("roomRevenueOnTheBooks", f.onTheBooks().revenue());
                x.put("forecastRoomRevenue", f.forecastRevenue());
                x.put("forecastServiceRevenue", f.forecastServiceRevenue());
                x.put("forecastTotalRevenue", f.forecastTotalRevenue());
                x.put("forecastOccupancyPercent", f.forecastOccupancy());
                x.put("paceVsLastYear", f.pace());
                x.put("confidence", f.confidence().name());
                x.put("revenueTarget", f.revenueTarget());
                fc.add(x);
            }
            root.put("roomRevenueForecast", fc);
        }
        if (budgetService != null) {
            budgetService.variance(r.current()).ifPresent(v -> {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("revenueTarget", v.budget().getRevenueTarget());
                b.put("costBudget", v.budget().getCostBudget());
                b.put("occupancyTargetPercent", v.budget().getOccupancyTarget());
                b.put("adrTarget", v.budget().getAdrTarget());
                b.put("revenueAchievedPercent", v.revenuePercent());
                b.put("costUsedPercent", v.costPercent());
                b.put("monthInProgress", v.inProgress());
                root.put("budget", b);
            });
        }
        return write(root);
    }

    private static Map<String, Object> pnl(ProfitService.MonthPnl m) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("month", m.month().toString());
        p.put("roomRevenue", m.roomRevenue());
        Map<String, BigDecimal> services = new LinkedHashMap<>();
        m.serviceRevenue().forEach((k, v) -> services.put(k.name() + " (" + k.getVietnameseLabel() + ")", v));
        p.put("serviceRevenue", services);
        p.put("forfeitedDeposits", m.forfeitedDeposits());
        p.put("totalRevenue", m.totalRevenue());
        p.put("directCost", m.directCost());
        p.put("grossProfit", m.grossProfit());
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        m.expenses().forEach((k, v) -> expenses.put(k.getLabel(), v));
        p.put("operatingExpenses", expenses);
        p.put("totalCost", m.totalCost());
        p.put("operatingProfit", m.operatingProfit());
        p.put("marginPercent", m.margin());
        p.put("occupancyPercent", m.occupancy());
        p.put("soldRoomNights", m.soldRoomNights());
        p.put("availableRoomNights", m.availableRoomNights());
        p.put("adr", m.adr());
        p.put("revPar", m.revPar());
        p.put("goppar", m.goppar());
        p.put("costPerOccupiedRoom", m.cpor());
        return p;
    }

    private static Map<String, Object> delta(ProfitService.Delta d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revenue", d.revenue());
        m.put("cost", d.cost());
        m.put("operatingProfit", d.profit());
        m.put("marginPoints", d.marginPoints());
        m.put("occupancyPoints", d.occupancyPoints());
        return m;
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AiAnalysis read(String json) {
        try {
            return mapper.readValue(json, AiAnalysis.class);
        } catch (JsonProcessingException ex) {
            return new AiAnalysis("Không đọc được bản phân tích đã lưu", List.of(), List.of(), List.of(), null);
        }
    }
}
