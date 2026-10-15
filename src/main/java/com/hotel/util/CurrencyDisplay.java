package com.hotel.util;

import com.hotel.service.PricingService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

// Gia quy doi USD de khach nuoc ngoai de hinh dung (trang tieng Anh), dung trong template: ${@currency.approxUsd(gia)}.
// Ty gia doc tu cai dat gia, nho tam 1 phut de trang danh sach nhieu phong khong truy van lien tuc
@Component("currency")
public class CurrencyDisplay {

    private static final long CACHE_MILLIS = 60_000;

    private final PricingService pricingService;
    private volatile int cachedRate;
    private volatile long cachedAt;

    public CurrencyDisplay(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    // "≈ US$43" - chi tra ve khi khach dang xem tieng Anh, con lai chuoi rong
    public String approxUsd(BigDecimal vnd) {
        if (vnd == null || vnd.signum() <= 0 || !"en".equals(LocaleContextHolder.getLocale().getLanguage())) {
            return "";
        }
        return "≈ US$" + NumberFormat.getIntegerInstance(Locale.US).format(toUsd(vnd, rate()));
    }

    public static long toUsd(BigDecimal vnd, int rate) {
        return vnd.divide(BigDecimal.valueOf(rate), 0, RoundingMode.HALF_UP).longValue();
    }

    public int rate() {
        long now = System.currentTimeMillis();
        if (cachedRate == 0 || now - cachedAt > CACHE_MILLIS) {
            cachedRate = pricingService.getSettings().getEffectiveUsdRate();
            cachedAt = now;
        }
        return cachedRate;
    }

    // Admin vua doi ty gia -> lan hien sau doc lai ngay
    public void evict() {
        cachedRate = 0;
    }
}
