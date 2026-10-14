package com.hotel.exception;

import com.hotel.util.Texts;

import java.util.function.Function;

// Dung cho cac loi vi pham business rule (VD: BR-01 phong da duoc dat, BR-02 ngay khong hop le...)
// Loi khach hang nhin thay tao bang BusinessException.of("ma.loi", tham so...): cau chu nam trong messages*.properties,
// getMessage() luon la tieng Viet (log, test), con giao dien khach dich theo ngon ngu dang chon (xem UserMessages)
public class BusinessException extends RuntimeException {

    // Tra cau theo ma (+ tham so) trong 1 ngon ngu cu the
    @FunctionalInterface
    public interface Translator {
        String t(String code, Object... args);
    }

    // null = loi chi co cau tieng Viet (VD loi o trang quan tri), giao dien nao cung hien nguyen cau
    private final Function<Translator, String> renderer;

    public BusinessException(String message) {
        super(message);
        this.renderer = null;
    }

    private BusinessException(Function<Translator, String> renderer) {
        super(renderer.apply(BusinessException::vietnamese));
        this.renderer = renderer;
    }

    public static BusinessException of(String code, Object... args) {
        return new BusinessException(t -> t.t(code, args));
    }

    // Cau ghep tu nhieu manh (VD danh sach don trung lich) - moi manh tu dich bang translator
    public static BusinessException rendered(Function<Translator, String> renderer) {
        return new BusinessException(renderer);
    }

    public boolean isTranslatable() {
        return renderer != null;
    }

    public String render(Translator translator) {
        return renderer != null ? renderer.apply(translator) : getMessage();
    }

    // Cau tieng Viet theo ma (doc thang messages.properties, khong can Spring)
    public static String vietnamese(String code, Object... args) {
        return Texts.get(Texts.VIETNAMESE, code, args);
    }
}
