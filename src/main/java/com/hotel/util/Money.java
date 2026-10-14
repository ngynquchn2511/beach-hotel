package com.hotel.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

// Dinh dang so tien VND: dau cham phan cach hang nghin (1.250.000), khong phan le - dung chung moi ngon ngu
public final class Money {

    private static final Locale VIETNAM = new Locale("vi", "VN");

    private Money() {
    }

    public static String format(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(VIETNAM));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }
}
