package com.hotel.controller;

import com.hotel.exception.BusinessException;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.InsightService;
import com.hotel.service.ProfitService;
import com.hotel.service.ai.AiAdvisorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Phan tich quan tri: nhan dinh theo quy tac (luon co) + phan tich / hoi dap bang AI khi co ANTHROPIC_API_KEY
// (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/insights")
public class AdminInsightController {

    private final InsightService insightService;
    private final ProfitService profitService;
    private final AiAdvisorService advisor;
    private com.hotel.service.MonthlyReportService monthlyReportService;
    // Luong rieng cho hoi dap AI dang dong chay (khong dung chung pool gui mail)
    private final ExecutorService aiStreamPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-stream");
        t.setDaemon(true);
        return t;
    });

    public AdminInsightController(InsightService insightService, ProfitService profitService, AiAdvisorService advisor) {
        this.insightService = insightService;
        this.profitService = profitService;
        this.advisor = advisor;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setMonthlyReportService(com.hotel.service.MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    // Gui ngay bao cao quan tri thang qua email (cung nguoi nhan bao cao cuoi ngay)
    @PostMapping("/email")
    public String email(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, RedirectAttributes redirectAttributes) {
        boolean sent = monthlyReportService.send(month);
        redirectAttributes.addFlashAttribute(sent ? "successMessage" : "errorMessage", sent
                ? "Đã gửi báo cáo tháng tới " + String.join(", ", monthlyReportService.recipients())
                : "Chưa gửi được: cần cấu hình MAIL_USERNAME / MAIL_PASSWORD và có ít nhất 1 người nhận");
        return "redirect:/admin/accounting/insights?month=" + month;
    }

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth m = month != null ? month : YearMonth.now();
        ProfitService.Report report = profitService.report(m);
        ProfitService.MonthPnl pnl = report.current();
        model.addAttribute("charts", charts(report));
        model.addAttribute("month", m);
        model.addAttribute("pnl", pnl);
        model.addAttribute("insights", insightService.analyze(m));
        model.addAttribute("topExpenses", InsightService.topExpenses(pnl, 3));
        model.addAttribute("aiEnabled", advisor.isEnabled());
        model.addAttribute("aiModel", advisor.model());
        advisor.latestAnalysis(m).ifPresent(a -> {
            model.addAttribute("analysis", a.analysis());
            model.addAttribute("analysisReport", a.report());
            model.addAttribute("analysisCost", AiAdvisorService.costUsd(a.report()));
        });
        model.addAttribute("questions", advisor.questions(m));
        return "admin/accounting/insights";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                          @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            advisor.analyze(month, user != null ? user.getUser().getFullName() : null);
            redirectAttributes.addFlashAttribute("successMessage", "AI đã phân tích xong tháng " + month.getMonthValue() + "/" + month.getYear());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/accounting/insights?month=" + month + "#ai";
    }

    // Hoi AI, tra loi hien dan: tra ve text/plain theo tung doan. Loi -> dong cuoi bat dau bang ky tu \u0000
    @PostMapping(value = "/ask/stream", produces = "text/plain;charset=UTF-8")
    public org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter askStream(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @RequestParam(required = false) String question,
            @AuthenticationPrincipal CustomUserDetails user) {
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter(180_000L);
        org.springframework.http.MediaType text = new org.springframework.http.MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);
        String by = user != null ? user.getUser().getFullName() : null;
        aiStreamPool.execute(() -> {
            try {
                AiAdvisorService.validQuestion(question);
                String system = advisor.askSystemPrompt(month);
                advisor.askStreaming(month, question, system, by, chunk -> {
                    try {
                        emitter.send(chunk, text);
                    } catch (java.io.IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
                emitter.complete();
            } catch (BusinessException ex) {
                try {
                    emitter.send("\u0000" + ex.getMessage(), text);
                    emitter.complete();
                } catch (java.io.IOException ignored) {
                    emitter.complete();
                }
            } catch (RuntimeException ex) {
                // Nguoi dung dong trang giua chung (UncheckedIOException) hoac loi khac
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        aiStreamPool.shutdownNow();
    }

    // Du lieu bieu do (don vi trieu dong cho tien) - ve phia trinh duyet bang static/js/mini-charts.js
    static Map<String, Object> charts(ProfitService.Report report) {
        List<String> labels = new ArrayList<>();
        List<Double> revenue = new ArrayList<>();
        List<Double> cost = new ArrayList<>();
        List<Double> profit = new ArrayList<>();
        List<Double> occupancy = new ArrayList<>();
        for (ProfitService.MonthPnl p : report.trend()) {
            labels.add(String.format("%02d/%02d", p.month().getMonthValue(), p.month().getYear() % 100));
            revenue.add(million(p.totalRevenue()));
            cost.add(million(p.totalCost()));
            profit.add(million(p.operatingProfit()));
            occupancy.add(Math.round(p.occupancy() * 10) / 10.0);
        }
        ProfitService.MonthPnl c = report.current();
        Map<String, Double> revenueMix = new LinkedHashMap<>();
        revenueMix.put("Tiền phòng", million(c.roomRevenue()));
        c.serviceRevenue().forEach((k, v) -> revenueMix.put(k.getVietnameseLabel(), million(v)));
        if (c.forfeitedDeposits().signum() > 0) {
            revenueMix.put("Cọc khách bỏ", million(c.forfeitedDeposits()));
        }
        Map<String, Double> costMix = new LinkedHashMap<>();
        BigDecimal serviceCost = c.serviceCost().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        costMix.put("Giá vốn dịch vụ", million(serviceCost));
        costMix.put("Chi phí phòng-đêm", million(c.variableRoomCost()));
        costMix.put("Phí thanh toán", million(c.paymentFees()));
        c.expenses().forEach((k, v) -> costMix.put(k.getLabel(), million(v)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("revenue", revenue);
        out.put("cost", cost);
        out.put("profit", profit);
        out.put("occupancy", occupancy);
        out.put("revenueMix", sortDesc(revenueMix));
        out.put("costMix", sortDesc(costMix));
        return out;
    }

    private static double million(BigDecimal v) {
        return v == null ? 0 : Math.round(v.doubleValue() / 100_000) / 10.0;
    }

    // [[ten, gia tri], ...] giam dan, bo dong = 0
    private static List<List<Object>> sortDesc(Map<String, Double> m) {
        return m.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> List.<Object>of(e.getKey(), e.getValue())).toList();
    }

    @PostMapping("/ask")
    public String ask(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @RequestParam(required = false) String question,
                      @AuthenticationPrincipal CustomUserDetails user, RedirectAttributes redirectAttributes) {
        try {
            advisor.ask(month, question, user != null ? user.getUser().getFullName() : null);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("lastQuestion", question);
        }
        return "redirect:/admin/accounting/insights?month=" + month + "#hoi-ai";
    }
}
