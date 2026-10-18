package com.hotel.config;

import com.hotel.service.EmailService;
import com.hotel.service.MonthlyReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

// 8h sang ngay 1 hang thang: gui bao cao quan tri thang truoc (tat bang app.monthly-report.enabled=false)
@Component
public class MonthlyReportScheduler {

    private final MonthlyReportService reportService;
    private final EmailService emailService;

    @Value("${app.monthly-report.enabled:true}")
    private boolean enabled;

    public MonthlyReportScheduler(MonthlyReportService reportService, EmailService emailService) {
        this.reportService = reportService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.monthly-report.cron:0 0 8 1 * *}")
    public void sendLastMonth() {
        if (enabled && emailService.isConfigured()) {
            reportService.send(YearMonth.now().minusMonths(1));
        }
    }
}
