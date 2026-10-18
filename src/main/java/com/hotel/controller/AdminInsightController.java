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

import java.time.YearMonth;

// Phan tich quan tri: nhan dinh theo quy tac (luon co) + phan tich / hoi dap bang AI khi co ANTHROPIC_API_KEY
// (ADMIN + ACCOUNTANT theo /admin/accounting/**)
@Controller
@RequestMapping("/admin/accounting/insights")
public class AdminInsightController {

    private final InsightService insightService;
    private final ProfitService profitService;
    private final AiAdvisorService advisor;
    private com.hotel.service.MonthlyReportService monthlyReportService;

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
        ProfitService.MonthPnl pnl = profitService.month(m);
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
