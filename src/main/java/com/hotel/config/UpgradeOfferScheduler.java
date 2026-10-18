package com.hotel.config;

import com.hotel.service.UpgradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 10h hang ngay: gui loi moi nang hang cho don nhan phong sau 2 ngay (chi khi admin bat "tu gui")
@Component
public class UpgradeOfferScheduler {

    private static final Logger log = LoggerFactory.getLogger(UpgradeOfferScheduler.class);

    private final UpgradeService upgradeService;

    public UpgradeOfferScheduler(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    @Scheduled(cron = "${app.upgrade.offer-cron:0 0 10 * * *}")
    public void run() {
        int sent = upgradeService.sendAutoOffers(LocalDate.now());
        if (sent > 0) {
            log.info("Da gui loi moi nang hang cho {} don", sent);
        }
    }
}
