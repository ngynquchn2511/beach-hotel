package com.hotel.util;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

// Doc cau chu song ngu thang tu messages*.properties, khong can Spring (dung trong service + unit test).
// Cung bo file voi giao dien (WebConfig.messageSource) nen 2 noi luon ra cung 1 cau.
public final class Texts {

    public static final Locale VIETNAMESE = new Locale("vi", "VN");

    private Texts() {
    }

    // Noi dung admin nhap 2 ban (VD mo ta phong): khach chon EN va co ban tieng Anh thi lay ban tieng Anh,
    // con lai (hoac bo trong ban tieng Anh) lay ban tieng Viet
    public static String pick(Locale locale, String vietnamese, String english) {
        boolean en = locale != null && "en".equals(locale.getLanguage());
        return en && english != null && !english.isBlank() ? english : vietnamese;
    }

    // Nhu tren, theo ngon ngu cua request dang xu ly (nut VI/EN). Ngoai request (job, email) -> tieng Viet,
    // khong lay locale cua may chu
    public static String pick(String vietnamese, String english) {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        return pick(context != null ? context.getLocale() : null, vietnamese, english);
    }

    // Chi co 2 ngon ngu: "en" -> tieng Anh, con lai -> tieng Viet
    public static Locale supported(Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? Locale.ENGLISH : VIETNAMESE;
    }

    // Khong fallback theo locale cua may chu: may tieng Anh van lay cau tieng Viet o file goc
    public static String get(Locale locale, String code, Object... args) {
        String pattern;
        try {
            pattern = ResourceBundle.getBundle("messages", supported(locale),
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)).getString(code);
        } catch (MissingResourceException ex) {
            return code;
        }
        // Giong Spring: khong co tham so thi giu nguyen cau (khong xu ly dau ' cua MessageFormat)
        return args == null || args.length == 0 ? pattern : new MessageFormat(pattern, supported(locale)).format(args);
    }
}
