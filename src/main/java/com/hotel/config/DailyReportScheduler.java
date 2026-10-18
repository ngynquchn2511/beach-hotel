package com.hotel.config;

import com.hotel.controller.AdminDailyReportController;
import com.hotel.service.DailyReportService;
import com.hotel.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 23h30 hang ngay: gui bao cao cuoi ngay cho quan ly (tat bang app.daily-report.enabled=false)
@Component
public class DailyReportScheduler {

    private final DailyReportService reportService;
    private final EmailService emailService;

    @Value("${app.daily-report.enabled:true}")
    private boolean enabled;

    public DailyReportScheduler(DailyReportService reportService, EmailService emailService) {
        this.reportService = reportService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.daily-report.cron:0 30 23 * * *}")
    public void sendToday() {
        if (!enabled || !emailService.isConfigured()) {
            return;
        }
        LocalDate today = LocalDate.now();
        emailService.sendDailyReport(reportService.recipients(), AdminDailyReportController.subject(today),
                DailyReportService.text(reportService.build(today)));
    }
}
